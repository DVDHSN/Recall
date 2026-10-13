package com.example.evaluation

import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.model.IndexingStatus
import com.example.core.search.SemanticSearchEngine
import com.example.core.search.fusion.CandidateFusion
import com.example.core.search.lexical.LexicalRetriever
import com.example.core.search.query.QueryParser
import com.example.core.search.semantic.SemanticRetriever
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ln

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SearchEngineEvaluationTest {

    private class MockEmbeddingEngine : TextEmbeddingEngine {
        override val modelVersion: String = "eval-mock-v2"
        override val embeddingDimension: Int = 4

        override suspend fun generateEmbedding(text: String): FloatArray {
            val lower = text.lowercase()
            return when {
                lower.contains("chickpea") || lower.contains("curry") || lower.contains("garbanzo") || lower.contains("recipe") -> floatArrayOf(1f, 0f, 0f, 0f)
                lower.contains("wifi") || lower.contains("password") || lower.contains("ssid") || lower.contains("network") -> floatArrayOf(0f, 1f, 0f, 0f)
                lower.contains("python") || lower.contains("null") || lower.contains("traceback") || lower.contains("error") -> floatArrayOf(0f, 0f, 1f, 0f)
                lower.contains("rtx") || lower.contains("benchmark") || lower.contains("fps") || lower.contains("gpu") -> floatArrayOf(0f, 0f, 0f, 1f)
                lower.contains("biology") || lower.contains("light") || lower.contains("phototropism") || lower.contains("plant") -> floatArrayOf(0.7f, 0.7f, 0f, 0f)
                lower.contains("lease") || lower.contains("rent") || lower.contains("tenant") -> floatArrayOf(0.2f, 0f, 0f, 0.8f)
                else -> floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
            }
        }
        override fun close() {}
    }

    private class MockDao(private val corpus: List<ScreenshotEntity>) : ScreenshotDao {
        override fun getAllFlow(): Flow<List<ScreenshotEntity>> = flowOf(corpus)
        override suspend fun getPaged(limit: Int, offset: Int): List<ScreenshotEntity> = corpus
        override suspend fun getById(id: Long): ScreenshotEntity? = corpus.find { it.id == id }
        override suspend fun getByMediaStoreId(mediaStoreId: Long): ScreenshotEntity? = corpus.find { it.mediaStoreId == mediaStoreId }
        override suspend fun getAllMediaStoreIds(): List<Long> = corpus.map { it.mediaStoreId }
        override suspend fun getByStatus(status: IndexingStatus, limit: Int): List<ScreenshotEntity> = corpus.filter { it.indexingStatus == status }
        override suspend fun getAwaitingIndexing(limit: Int): List<ScreenshotEntity> = emptyList()
        override suspend fun getAllIndexedWithEmbeddings(): List<ScreenshotEntity> = corpus
        override fun getByCategoryFlow(category: String): Flow<List<ScreenshotEntity>> = flowOf(corpus.filter { it.categoryLabels.contains(category) })
        override suspend fun getAllDuplicateHashInfo(): List<com.example.core.database.DuplicateHashInfo> = emptyList()
        override suspend fun getCandidateDuplicates(currentId: Long): List<ScreenshotEntity> = corpus.filter { it.id != currentId }
        override fun getTotalCountFlow(): Flow<Int> = flowOf(corpus.size)
        override fun getIndexedCountFlow(): Flow<Int> = flowOf(corpus.size)
        override fun getEmptyTextCountFlow(): Flow<Int> = flowOf(0)
        override fun getFailedCountFlow(): Flow<Int> = flowOf(0)
        override fun getPendingCountFlow(): Flow<Int> = flowOf(0)
        override suspend fun searchLexical(query: String): List<ScreenshotEntity> = emptyList()
        override suspend fun searchByDateRange(startSec: Long, endSec: Long): List<ScreenshotEntity> = corpus.filter { it.dateModified in startSec..endSec }
        override suspend fun insertAll(entities: List<ScreenshotEntity>): List<Long> = emptyList()
        override suspend fun insertOrUpdate(entity: ScreenshotEntity): Long = 0L
        override suspend fun update(entity: ScreenshotEntity) {}
        override suspend fun updateStatus(id: Long, status: IndexingStatus) {}
        override suspend fun deleteMissing(keepMediaStoreIds: List<Long>): Int = 0
        override suspend fun deleteById(id: Long) {}
        override suspend fun clearAll() {}
        override suspend fun resetAllForReindexing() {}
        override suspend fun retryFailed(): Int = 0
        override suspend fun resetInterruptedProcessing(): Int = 0
        override suspend fun getAwaitingVisualEmbedding(limit: Int): List<ScreenshotEntity> = emptyList()
        override suspend fun updateVisualEmbedding(id: Long, visualBlob: FloatArray?, visualModelVersion: String, visualDimension: Int, status: String) {}
        override fun getVisualPendingCountFlow(): Flow<Int> = flowOf(0)
        override suspend fun getAllWithVisualEmbeddings(): List<ScreenshotEntity> = corpus.filter { it.visualBlob != null }
        override suspend fun getAllSearchable(): List<ScreenshotEntity> = corpus
        override fun getByImageSourceFlow(imageSource: String): Flow<List<ScreenshotEntity>> = flowOf(corpus.filter { it.imageSource == imageSource })
        override suspend fun deleteAllMediaStoreItems(): Int = 0
    }

    data class SegmentMetrics(
        val name: String,
        val queryCount: Int,
        val recallAt5: Double,
        val precisionAt5: Double,
        val ndcgAt5: Double,
        val mrr: Double
    )

    private suspend fun evaluateQuerySubset(
        name: String,
        subset: List<EvalQuery>,
        searchEngine: SemanticSearchEngine
    ): SegmentMetrics {
        var hitsAt5 = 0
        var totalPrecisionAt5 = 0.0
        var totalNdcgAt5 = 0.0
        var totalReciprocalRank = 0.0

        for (q in subset) {
            val results = searchEngine.search(q.queryText)
            val top5 = results.take(5)
            val top5Ids = top5.map { it.screenshot.id }.toSet()

            // Recall@5
            val hasHitAt5 = q.expectedDocIds.any { top5Ids.contains(it) }
            if (hasHitAt5) hitsAt5++

            // Precision@5
            val relevantInTop5 = top5.count { q.expectedDocIds.contains(it.screenshot.id) }
            totalPrecisionAt5 += relevantInTop5.toDouble() / 5.0

            // nDCG@5
            var dcg = 0.0
            for (i in top5.indices) {
                val rel = if (q.expectedDocIds.contains(top5[i].screenshot.id)) 1.0 else 0.0
                dcg += rel / (ln((i + 2).toDouble()) / ln(2.0))
            }
            var idcg = 0.0
            val idealCount = kotlin.math.min(5, q.expectedDocIds.size)
            for (i in 0 until idealCount) {
                idcg += 1.0 / (ln((i + 2).toDouble()) / ln(2.0))
            }
            val ndcg = if (idcg > 0.0) dcg / idcg else 1.0
            totalNdcgAt5 += ndcg

            // MRR
            val firstRank = results.indexOfFirst { q.expectedDocIds.contains(it.screenshot.id) }
            if (firstRank >= 0) {
                totalReciprocalRank += 1.0 / (firstRank + 1)
            }
        }

        val n = subset.size.coerceAtLeast(1)
        return SegmentMetrics(
            name = name,
            queryCount = subset.size,
            recallAt5 = hitsAt5.toDouble() / n,
            precisionAt5 = totalPrecisionAt5 / n,
            ndcgAt5 = totalNdcgAt5 / n,
            mrr = totalReciprocalRank / n
        )
    }

    @Test
    fun testComprehensiveEvaluation65Queries() = runBlocking {
        val corpus = EvaluationDataset.testCorpus
        val dao = MockDao(corpus)
        val embeddingEngine = MockEmbeddingEngine()
        val queryParser = QueryParser(clockNowMsProvider = { 1791870000000L }) // fixed mock timestamp (matching today in testCorpus)

        val searchEngine = SemanticSearchEngine(
            embeddingEngine = embeddingEngine,
            screenshotDao = dao,
            queryParser = queryParser,
            lexicalRetriever = LexicalRetriever(),
            semanticRetriever = SemanticRetriever(embeddingEngine),
            candidateFusion = CandidateFusion()
        )

        val allQueries = EvaluationDataset.queries

        // 1. Segment: Text-Heavy / OCR Queries
        val textQueries = allQueries.filter {
            it.category in listOf("Code", "Network", "Identifier", "Finance")
        }

        // 2. Segment: Hybrid / Semantic Queries
        val hybridQueries = allQueries.filter {
            it.category in listOf("Recipe", "Shopping", "Notes", "Date", "Negative")
        }

        val textMetrics = evaluateQuerySubset("Text-Heavy / OCR Queries", textQueries, searchEngine)
        val hybridMetrics = evaluateQuerySubset("Hybrid / Semantic Queries", hybridQueries, searchEngine)
        val overallMetrics = evaluateQuerySubset("Overall Full Benchmark", allQueries, searchEngine)

        // Exact technical identifier evaluation
        val identifierQueries = allQueries.filter { it.isExactImportant }
        var identifierHits = 0
        for (q in identifierQueries) {
            val results = searchEngine.search(q.queryText)
            if (results.isNotEmpty() && q.expectedDocIds.contains(results[0].screenshot.id)) {
                identifierHits++
            }
        }
        val identifierAccuracy = if (identifierQueries.isNotEmpty()) {
            identifierHits.toDouble() / identifierQueries.size
        } else 1.0

        println("=== RECALL 2.0 EVALUATION REPORT (65 QUERIES) ===")
        println("• Segment 1 [${textMetrics.name}] (N=${textMetrics.queryCount}):")
        println("   - Recall@5:    ${String.format("%.1f%%", textMetrics.recallAt5 * 100)}")
        println("   - Precision@5: ${String.format("%.1f%%", textMetrics.precisionAt5 * 100)}")
        println("   - nDCG@5:      ${String.format("%.3f", textMetrics.ndcgAt5)}")
        println("   - MRR:         ${String.format("%.3f", textMetrics.mrr)}")

        println("• Segment 2 [${hybridMetrics.name}] (N=${hybridMetrics.queryCount}):")
        println("   - Recall@5:    ${String.format("%.1f%%", hybridMetrics.recallAt5 * 100)}")
        println("   - Precision@5: ${String.format("%.1f%%", hybridMetrics.precisionAt5 * 100)}")
        println("   - nDCG@5:      ${String.format("%.3f", hybridMetrics.ndcgAt5)}")
        println("   - MRR:         ${String.format("%.3f", hybridMetrics.mrr)}")

        println("• Overall [${overallMetrics.name}] (N=${overallMetrics.queryCount}):")
        println("   - Recall@5:    ${String.format("%.1f%%", overallMetrics.recallAt5 * 100)}")
        println("   - Precision@5: ${String.format("%.1f%%", overallMetrics.precisionAt5 * 100)}")
        println("   - nDCG@5:      ${String.format("%.3f", overallMetrics.ndcgAt5)}")
        println("   - MRR:         ${String.format("%.3f", overallMetrics.mrr)}")
        println("• Technical Identifier Top-1 Accuracy: ${String.format("%.1f%%", identifierAccuracy * 100)} ($identifierHits / ${identifierQueries.size})")

        // Assert benchmark acceptance criteria
        assertTrue("Overall Recall@5 must be >= 90%", overallMetrics.recallAt5 >= 0.90)
        assertTrue("Text Recall@5 must be >= 90%", textMetrics.recallAt5 >= 0.90)
        assertTrue("Hybrid Recall@5 must be >= 90%", hybridMetrics.recallAt5 >= 0.90)
        assertTrue("Overall nDCG@5 must be >= 0.85", overallMetrics.ndcgAt5 >= 0.85)
        assertTrue("Overall MRR must be >= 0.80", overallMetrics.mrr >= 0.80)
        assertTrue("Exact identifier accuracy must be >= 90%", identifierAccuracy >= 0.90)
    }
}
