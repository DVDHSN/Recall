package com.example.core.search

import android.util.Log
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.MobileClipEmbeddingEngine
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.ml.embedding.VisualEmbeddingEngine
import com.example.core.model.SearchResult
import com.example.core.search.fusion.CandidateFusion
import com.example.core.search.lexical.LexicalRetriever
import com.example.core.search.query.ParsedQuery
import com.example.core.search.query.QueryParser
import com.example.core.search.query.SearchMode
import com.example.core.search.semantic.SemanticRetriever
import com.example.core.search.visual.VisualRetriever
import com.example.core.visual.PerceptualHasher
import com.example.core.visual.VisualFeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SemanticSearchEngine(
    private val embeddingEngine: TextEmbeddingEngine,
    private val screenshotDao: ScreenshotDao,
    private val queryParser: QueryParser = QueryParser(),
    private val lexicalRetriever: LexicalRetriever = LexicalRetriever(),
    private val semanticRetriever: SemanticRetriever = SemanticRetriever(embeddingEngine),
    private val candidateFusion: CandidateFusion = CandidateFusion(),
    private val visualEngine: VisualEmbeddingEngine? = null,
    private val visualRetriever: VisualRetriever? = visualEngine?.let { VisualRetriever(it) }
) {

    fun parseQuery(rawQuery: String, overrideMode: SearchMode? = null): ParsedQuery {
        return queryParser.parse(rawQuery, overrideMode)
    }

    suspend fun search(
        rawQuery: String,
        overrideMode: SearchMode? = null,
        limit: Int = 50
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val parsed = queryParser.parse(rawQuery, overrideMode)
        searchParsed(parsed, limit)
    }

    suspend fun searchParsed(
        query: ParsedQuery,
        limit: Int = 50
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        if (query.cleanSearchText.isBlank()) return@withContext emptyList()

        val startTime = System.currentTimeMillis()

        // Fetch candidate searchable corpus from database (includes images with OCR or visual embeddings)
        val corpus: List<ScreenshotEntity> = withContext(Dispatchers.IO) {
            val all = screenshotDao.getAllSearchable()
            if (all.isNotEmpty()) all else screenshotDao.getAllIndexedWithEmbeddings()
        }

        if (corpus.isEmpty()) return@withContext emptyList()

        // 1. Text Semantic Retrieval
        val semanticCandidates = if (query.searchMode != SearchMode.EXACT_ONLY) {
            try {
                semanticRetriever.retrieve(query, corpus, limit = limit * 2)
            } catch (e: Exception) {
                Log.e(TAG, "Semantic retrieval failed, falling back to lexical", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        // 2. Cross-Modal Visual Semantic Retrieval (MobileCLIP)
        val visualCandidates = if (query.searchMode != SearchMode.EXACT_ONLY && visualRetriever != null) {
            try {
                visualRetriever.retrieve(query, corpus, limit = limit * 2)
            } catch (e: Exception) {
                Log.e(TAG, "Visual retrieval failed", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        // 3. Lexical Keyword Retrieval
        val lexicalCandidates = if (query.searchMode != SearchMode.SEMANTIC_ONLY && query.searchMode != SearchMode.VISUAL_SIMILAR) {
            lexicalRetriever.retrieve(query, corpus, limit = limit * 2)
        } else {
            emptyList()
        }

        // 4. Candidate Fusion & Intelligent Adaptive Ranking
        val finalResults = candidateFusion.fuseAndRank(
            query = query,
            semanticCandidates = semanticCandidates,
            lexicalCandidates = lexicalCandidates,
            visualCandidates = visualCandidates,
            allIndexedCorpus = corpus,
            limit = limit
        )

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Search for '${query.rawQuery}' (mode=${query.searchMode}, intent=${query.queryIntent}) returned ${finalResults.size} results in ${duration}ms")
        finalResults
    }

    suspend fun findVisuallySimilar(
        targetId: Long,
        limit: Int = 20
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val target = withContext(Dispatchers.IO) { screenshotDao.getById(targetId) } ?: return@withContext emptyList()
        val allDocs = withContext(Dispatchers.IO) { screenshotDao.getCandidateDuplicates(targetId) }

        val targetVec = target.visualBlob
        val targetPHash = target.perceptualHash

        val results = mutableListOf<SearchResult>()

        for (doc in allDocs) {
            var visualSim = 0f
            if (targetVec != null && doc.visualBlob != null) {
                visualSim = if (targetVec.size == 512 && doc.visualBlob.size == 512) {
                    MobileClipEmbeddingEngine.cosineSimilarity(targetVec, doc.visualBlob)
                } else {
                    VisualFeatureExtractor.cosineSimilarity(targetVec, doc.visualBlob)
                }
            }

            val isNearDup = if (targetPHash != 0L && doc.perceptualHash != 0L) {
                PerceptualHasher.isNearDuplicate(targetPHash, doc.perceptualHash, threshold = 8)
            } else false

            val distance = if (targetPHash != 0L && doc.perceptualHash != 0L) {
                PerceptualHasher.hammingDistance(targetPHash, doc.perceptualHash)
            } else 64

            val combined = if (isNearDup) 0.95f else visualSim

            if (combined > 0.35f || isNearDup) {
                val explanation = if (isNearDup) {
                    "Probable near-duplicate image (difference: $distance bits)"
                } else {
                    "Visually similar content & scene structure (${(combined * 100).toInt()}% similarity)"
                }

                results.add(
                    SearchResult(
                        screenshot = doc.toDomain(),
                        semanticScore = 0f,
                        lexicalScore = 0f,
                        visualScore = combined,
                        combinedScore = combined,
                        snippet = doc.ocrText.take(120).ifEmpty { "Visual similarity match" },
                        explanation = explanation,
                        hasNearDuplicates = isNearDup,
                        nearDuplicateCount = if (isNearDup) 1 else 0
                    )
                )
            }
        }

        results.sortByDescending { it.combinedScore }
        results.take(limit)
    }

    companion object {
        private const val TAG = "SemanticSearchEngine"
    }
}
