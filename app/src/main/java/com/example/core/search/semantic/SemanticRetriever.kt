package com.example.core.search.semantic

import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.search.query.ParsedQuery

data class SemanticCandidate(
    val entity: ScreenshotEntity,
    val cosineSimilarity: Float
)

class SemanticRetriever(
    private val embeddingEngine: TextEmbeddingEngine
) {

    suspend fun retrieve(
        query: ParsedQuery,
        corpus: List<ScreenshotEntity>,
        limit: Int = 100
    ): List<SemanticCandidate> {
        if (corpus.isEmpty() || query.cleanSearchText.isBlank()) return emptyList()

        val queryVec = embeddingEngine.generateEmbedding(query.cleanSearchText)
        val candidates = mutableListOf<SemanticCandidate>()

        for (doc in corpus) {
            val docVec = doc.embeddingBlob ?: continue
            if (docVec.size != queryVec.size) continue

            // Dot product (both vectors are L2-normalized)
            var dot = 0f
            for (i in queryVec.indices) {
                dot += queryVec[i] * docVec[i]
            }
            val cosSim = dot.coerceIn(-1.0f, 1.0f)

            if (cosSim > 0.05f) {
                candidates.add(
                    SemanticCandidate(
                        entity = doc,
                        cosineSimilarity = cosSim
                    )
                )
            }
        }

        candidates.sortByDescending { it.cosineSimilarity }
        return candidates.take(limit)
    }
}
