package com.example.core.indexing

import android.util.Log
import com.example.core.classification.ScreenshotClassifier
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.media.ScreenshotDiscovery
import com.example.core.ml.embedding.TextEmbeddingEngine
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
    private val screenshotDao: ScreenshotDao
) {

    suspend fun indexScreenshot(entity: ScreenshotEntity): Boolean = withContext(Dispatchers.IO) {
        val id = entity.id
        screenshotDao.updateStatus(id, IndexingStatus.PROCESSING)

        val bitmap = discovery.loadDownscaledBitmap(entity.contentUri, maxDimension = 1920)
        if (bitmap == null) {
            Log.w(TAG, "Failed to decode bitmap for screenshot $id (${entity.displayName})")
            val failedEntity = entity.copy(
                indexingStatus = IndexingStatus.FAILED,
                ocrError = "Failed to open or decode image"
            )
            screenshotDao.update(failedEntity)
            return@withContext false
        }

        try {
            // 1. Compute Visual Hashes & Feature Vector before bitmap recycling
            val contentHash = PerceptualHasher.computeContentHash(bitmap)
            val perceptualHash = PerceptualHasher.computeDHash(bitmap)
            val visualVector = VisualFeatureExtractor.extractVisualVector(bitmap)

            // 2. Run OCR
            val ocrResult = ocrEngine.recognizeText(bitmap)
            // Recycle bitmap immediately to release RAM on mid-range hardware
            bitmap.recycle()

            if (!ocrResult.isSuccess) {
                Log.w(TAG, "OCR failed for screenshot $id: ${ocrResult.error}")
                val failedEntity = entity.copy(
                    indexingStatus = IndexingStatus.FAILED,
                    contentHash = contentHash,
                    perceptualHash = perceptualHash,
                    visualBlob = visualVector,
                    ocrError = ocrResult.error
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
                    lastIndexedAt = System.currentTimeMillis(),
                    embeddingDimension = 0,
                    embeddingBlob = null,
                    contentHash = contentHash,
                    perceptualHash = perceptualHash,
                    visualBlob = visualVector,
                    categoryLabels = categoriesStr,
                    classificationConfidence = classification.confidence
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
                lastIndexedAt = System.currentTimeMillis(),
                embeddingModelVersion = embeddingEngine.modelVersion,
                embeddingDimension = embeddingEngine.embeddingDimension,
                embeddingBlob = embedding,
                contentHash = contentHash,
                perceptualHash = perceptualHash,
                visualBlob = visualVector,
                categoryLabels = categoriesStr,
                classificationConfidence = classification.confidence,
                ocrError = null,
                embeddingError = null
            )
            screenshotDao.update(indexedEntity)
            Log.d(TAG, "Indexed image $id (${entity.displayName}) - Cat: $categoriesStr, pHash: $perceptualHash")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing screenshot $id", e)
            val failedEntity = entity.copy(
                indexingStatus = IndexingStatus.FAILED,
                embeddingError = e.localizedMessage
            )
            screenshotDao.update(failedEntity)
            false
        }
    }

    companion object {
        private const val TAG = "ScreenshotIndexer"
    }
}
