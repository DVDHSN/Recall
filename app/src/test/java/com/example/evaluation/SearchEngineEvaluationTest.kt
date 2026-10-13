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
        override suspend fun retryFailed() {}
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

        val queries = EvaluationDataset.queries
        var hitsAt5 = 0
        var hitsAt10 = 0
        var totalReciprocalRank = 0.0
        var identifierHits = 0
        var totalIdentifiers = 0

        for (q in queries) {
            val results = searchEngine.search(q.queryText)
            val top5Ids = results.take(5).map { it.screenshot.id }.toSet()
            val top10Ids = results.take(10).map { it.screenshot.id }.toSet()

            // Check Recall@5
            val hasHitAt5 = q.expectedDocIds.any { top5Ids.contains(it) }
            if (hasHitAt5) hitsAt5++

            // Check Recall@10
            val hasHitAt10 = q.expectedDocIds.any { top10Ids.contains(it) }
            if (hasHitAt10) hitsAt10++

            // Compute MRR
            val firstHitRank = results.indexOfFirst { q.expectedDocIds.contains(it.screenshot.id) }
            if (firstHitRank >= 0) {
                totalReciprocalRank += 1.0 / (firstHitRank + 1)
            }

            // Check technical identifier accuracy
            if (q.isExactImportant) {
                totalIdentifiers++
                if (results.isNotEmpty() && q.expectedDocIds.contains(results[0].screenshot.id)) {
                    identifierHits++
                }
            }
        }

        val recallAt5 = hitsAt5.toDouble() / queries.size
        val recallAt10 = hitsAt10.toDouble() / queries.size
        val mrr = totalReciprocalRank / queries.size
        val identifierAccuracy = if (totalIdentifiers > 0) identifierHits.toDouble() / totalIdentifiers else 1.0

        println("=== RECALL 2.0 EVALUATION REPORT (65 QUERIES) ===")
        println("• Total Queries Evaluated: ${queries.size}")
        println("• Recall@5: ${(recallAt5 * 100).toInt()}% ($hitsAt5 / ${queries.size})")
        println("• Recall@10: ${(recallAt10 * 100).toInt()}% ($hitsAt10 / ${queries.size})")
        println("• Mean Reciprocal Rank (MRR): ${String.format("%.3f", mrr)}")
        println("• Exact Identifier Accuracy: ${(identifierAccuracy * 100).toInt()}% ($identifierHits / $totalIdentifiers)")

        // Assert benchmark acceptance criteria
        assertTrue("Recall@5 must be >= 90%", recallAt5 >= 0.90)
        assertTrue("Recall@10 must be >= 95%", recallAt10 >= 0.95)
        assertTrue("MRR must be >= 0.80", mrr >= 0.80)
        assertTrue("Exact identifier accuracy must be >= 90%", identifierAccuracy >= 0.90)
    }
}
