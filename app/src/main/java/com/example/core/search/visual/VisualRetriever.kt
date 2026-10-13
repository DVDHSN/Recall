package com.example.core.search.visual

import android.util.Log
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.MobileClipEmbeddingEngine
import com.example.core.ml.embedding.VisualEmbeddingEngine
import com.example.core.search.query.ParsedQuery

data class VisualCandidate(
    val entity: ScreenshotEntity,
    val cosineSimilarity: Float
)

class VisualRetriever(
    private val visualEngine: VisualEmbeddingEngine
) {

    suspend fun retrieve(
        query: ParsedQuery,
        corpus: List<ScreenshotEntity>,
        limit: Int = 50,
        similarityThreshold: Float = 0.25f
    ): List<VisualCandidate> {
        val cleanText = query.cleanSearchText.trim()
        if (cleanText.isEmpty() || corpus.isEmpty()) {
            return emptyList()
        }

        val queryVector = try {
            visualEngine.generateTextEmbedding(cleanText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate visual text embedding for: $cleanText", e)
            return emptyList()
        }

        val candidates = mutableListOf<VisualCandidate>()

        for (entity in corpus) {
            val visualBlob = entity.visualBlob ?: continue
            if (visualBlob.isEmpty()) continue

            val similarity = MobileClipEmbeddingEngine.cosineSimilarity(queryVector, visualBlob)
            if (similarity >= similarityThreshold) {
                candidates.add(VisualCandidate(entity = entity, cosineSimilarity = similarity))
            }
        }

        candidates.sortByDescending { it.cosineSimilarity }
        return candidates.take(limit)
    }

    companion object {
        private const val TAG = "VisualRetriever"
    }
}
