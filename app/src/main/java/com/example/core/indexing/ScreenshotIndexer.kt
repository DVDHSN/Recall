package com.example.core.indexing

import android.util.Log
import com.example.core.classification.ScreenshotClassifier
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.media.ScreenshotDiscovery
import com.example.core.ml.embedding.MobileClipEmbeddingEngine
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.ml.embedding.VisualEmbeddingEngine
import com.example.core.ml.ocr.OcrEngine
import com.example.core.model.IndexingStatus
import com.example.core.visual.PerceptualHasher
import com.example.core.visual.VisualFeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenshotIndexer(
    private val discovery: ScreenshotDiscovery,
    private val ocrEngine: OcrEngine,
    private val embeddingEngine: TextEmbeddingEngine,
    private val screenshotDao: ScreenshotDao,
    private val visualEngine: VisualEmbeddingEngine? = null
) {

    suspend fun indexScreenshot(entity: ScreenshotEntity): Boolean = withContext(Dispatchers.IO) {
        val id = entity.id
        val newRetryCount = entity.retryCount + 1
        val now = System.currentTimeMillis()
        screenshotDao.updateStatus(id, IndexingStatus.PROCESSING)

        val bitmap = discovery.loadDownscaledBitmap(entity.contentUri, maxDimension = 1920)
        if (bitmap == null) {
            Log.w(TAG, "Failed to decode bitmap for image $id (${entity.displayName}) attempt $newRetryCount")
            val isAccessible = discovery.isUriAccessible(entity.contentUri)
            if (!isAccessible && entity.relativePath != "Imported") {
                Log.i(TAG, "Image $id (${entity.displayName}) URI is no longer accessible; deleting from database.")
                screenshotDao.deleteById(id)
                return@withContext false
            }

            val nextStatus = if (newRetryCount >= 3) IndexingStatus.FAILED else IndexingStatus.NEEDS_REINDEX
            val failedEntity = entity.copy(
                indexingStatus = nextStatus,
                retryCount = newRetryCount,
                lastAttemptAt = now,
                ocrError = if (!isAccessible) "Inaccessible or permission revoked" else "Failed to open or decode image (Attempt $newRetryCount/3)"
            )
            screenshotDao.update(failedEntity)
            return@withContext false
        }

        try {
            // 1. Compute Visual Hashes & Cross-Modal Visual Embedding
            val contentHash = PerceptualHasher.computeContentHash(bitmap)
            val perceptualHash = PerceptualHasher.computeDHash(bitmap)

            val visualVector = if (visualEngine != null) {
                try {
                    visualEngine.generateImageEmbedding(bitmap)
                } catch (e: Exception) {
                    Log.w(TAG, "Visual embedding failed for $id, falling back to layout vector: ${e.message}")
                    VisualFeatureExtractor.extractVisualVector(bitmap)
                }
            } else {
                VisualFeatureExtractor.extractVisualVector(bitmap)
            }
            val visualModel = visualEngine?.modelVersion ?: "SpatialLayoutColor-v1"
            val visualDims = visualEngine?.embeddingDimension ?: visualVector.size

            // 2. Run OCR
            val ocrResult = ocrEngine.recognizeText(bitmap)

            if (!ocrResult.isSuccess) {
                Log.w(TAG, "OCR failed for image $id (attempt $newRetryCount): ${ocrResult.error}")
                val nextStatus = if (newRetryCount >= 3) IndexingStatus.FAILED else IndexingStatus.NEEDS_REINDEX
                val failedEntity = entity.copy(
                    indexingStatus = nextStatus,
                    retryCount = newRetryCount,
                    lastAttemptAt = now,
                    contentHash = contentHash,
                    perceptualHash = perceptualHash,
                    visualBlob = visualVector,
                    visualModelVersion = visualModel,
                    visualDimension = visualDims,
                    visualEmbeddingStatus = "READY",
                    ocrError = "${ocrResult.error} (Attempt $newRetryCount/3)"
                )
                screenshotDao.update(failedEntity)
                return@withContext false
            }

            val extractedText = ocrResult.fullText.trim()

            // 3. Classify Topics
            val classification = ScreenshotClassifier.classify(extractedText, entity.displayName)
            val categoriesStr = classification.categories.joinToString(",") { it.name }

            val cleanDisplayName = entity.displayName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()

            val textToEmbed = if (extractedText.isNotEmpty()) {
                extractedText
            } else if (cleanDisplayName.length >= 3 && !cleanDisplayName.all { it.isDigit() }) {
                cleanDisplayName
            } else {
                ""
            }

            if (textToEmbed.isEmpty()) {
                // Image contains no readable text, but retains visual embeddings and perceptual hash
                val emptyEntity = entity.copy(
                    ocrText = "",
                    indexingStatus = IndexingStatus.EMPTY_TEXT,
                    retryCount = 0,
                    lastIndexedAt = now,
                    lastAttemptAt = now,
                    embeddingDimension = 0,
                    embeddingBlob = null,
                    contentHash = contentHash,
                    perceptualHash = perceptualHash,
                    visualBlob = visualVector,
                    visualModelVersion = visualModel,
                    visualDimension = visualDims,
                    visualEmbeddingStatus = "READY",
                    categoryLabels = categoriesStr,
                    classificationConfidence = classification.confidence,
                    ocrError = null,
                    embeddingError = null
                )
                screenshotDao.update(emptyEntity)
                return@withContext true
            }

            // 4. Generate on-device sentence embedding from extracted text or descriptive name
            val embedding = embeddingEngine.generateEmbedding(textToEmbed)

            // 5. Persist fully indexed image
            val indexedEntity = entity.copy(
                ocrText = extractedText,
                indexingStatus = IndexingStatus.INDEXED,
                retryCount = 0,
                lastIndexedAt = now,
                lastAttemptAt = now,
                embeddingModelVersion = embeddingEngine.modelVersion,
                embeddingDimension = embeddingEngine.embeddingDimension,
                embeddingBlob = embedding,
                contentHash = contentHash,
                perceptualHash = perceptualHash,
                visualBlob = visualVector,
                visualModelVersion = visualModel,
                visualDimension = visualDims,
                visualEmbeddingStatus = "READY",
                categoryLabels = categoriesStr,
                classificationConfidence = classification.confidence,
                ocrError = null,
                embeddingError = null
            )
            screenshotDao.update(indexedEntity)
            Log.d(TAG, "Indexed image $id (${entity.displayName}) - Cat: $categoriesStr, pHash: $perceptualHash, Visual: $visualDims dims")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing image $id attempt $newRetryCount", e)
            val nextStatus = if (newRetryCount >= 3) IndexingStatus.FAILED else IndexingStatus.NEEDS_REINDEX
            val failedEntity = entity.copy(
                indexingStatus = nextStatus,
                retryCount = newRetryCount,
                lastAttemptAt = now,
                ocrError = "${e.localizedMessage ?: "Unknown failure"} (Attempt $newRetryCount/3)"
            )
            screenshotDao.update(failedEntity)
            false
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenshotIndexer"
    }
}
