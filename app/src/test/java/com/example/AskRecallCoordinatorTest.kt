package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.DuplicateHashInfo
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.model.IndexingStatus
import com.example.core.qa.AskRecallCoordinator
import com.example.core.qa.engine.LocalGroundedSynthesizer
import com.example.core.qa.model.AnswerStatus
import com.example.core.search.SemanticSearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AskRecallCoordinatorTest {

    private lateinit var context: Context
    private lateinit var mockEmbeddingEngine: TextEmbeddingEngine

    private class MockEmbeddingEngine : TextEmbeddingEngine {
        override val modelVersion: String = "test-mock-v1"
        override val embeddingDimension: Int = 4

        override suspend fun generateEmbedding(text: String): FloatArray {
            val lower = text.lowercase()
            return when {
                lower.contains("receipt") || lower.contains("rm80") -> floatArrayOf(1f, 0f, 0f, 0f)
                lower.contains("transformer") || lower.contains("revision") -> floatArrayOf(0f, 1f, 0f, 0f)
                lower.contains("error") || lower.contains("404") -> floatArrayOf(0f, 0f, 1f, 0f)
                else -> floatArrayOf(0f, 0f, 0f, 1f)
            }
        }
        override fun close() {}
    }

    private class MockDao(private val indexedScreenshots: List<ScreenshotEntity>) : ScreenshotDao {
        override fun getAllFlow(): Flow<List<ScreenshotEntity>> = flowOf(indexedScreenshots)
        override suspend fun getPaged(limit: Int, offset: Int): List<ScreenshotEntity> = indexedScreenshots
        override suspend fun getById(id: Long): ScreenshotEntity? = indexedScreenshots.find { it.id == id }
        override suspend fun getByMediaStoreId(mediaStoreId: Long): ScreenshotEntity? = indexedScreenshots.find { it.mediaStoreId == mediaStoreId }
        override suspend fun getAllMediaStoreIds(): List<Long> = indexedScreenshots.map { it.mediaStoreId }
        override suspend fun getByStatus(status: IndexingStatus, limit: Int): List<ScreenshotEntity> = indexedScreenshots.filter { it.indexingStatus == status }
        override suspend fun getAwaitingIndexing(limit: Int): List<ScreenshotEntity> = emptyList()
        override suspend fun getAllIndexedWithEmbeddings(): List<ScreenshotEntity> = indexedScreenshots
        override fun getTotalCountFlow(): Flow<Int> = flowOf(indexedScreenshots.size)
        override fun getIndexedCountFlow(): Flow<Int> = flowOf(indexedScreenshots.size)
        override fun getEmptyTextCountFlow(): Flow<Int> = flowOf(0)
        override fun getFailedCountFlow(): Flow<Int> = flowOf(0)
        override fun getPendingCountFlow(): Flow<Int> = flowOf(0)
        override fun getByCategoryFlow(category: String): Flow<List<ScreenshotEntity>> = flowOf(indexedScreenshots)
        override suspend fun getAllDuplicateHashInfo(): List<DuplicateHashInfo> = emptyList()
        override suspend fun getCandidateDuplicates(currentId: Long): List<ScreenshotEntity> = emptyList()
        override suspend fun searchByDateRange(startSec: Long, endSec: Long): List<ScreenshotEntity> = emptyList()
        override suspend fun searchLexical(query: String): List<ScreenshotEntity> = emptyList()
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
        override suspend fun getAllWithVisualEmbeddings(): List<ScreenshotEntity> = indexedScreenshots.filter { it.visualBlob != null }
        override suspend fun getAllSearchable(): List<ScreenshotEntity> = indexedScreenshots
        override fun getByImageSourceFlow(imageSource: String): Flow<List<ScreenshotEntity>> = flowOf(indexedScreenshots.filter { it.imageSource == imageSource })
        override suspend fun deleteAllMediaStoreItems(): Int = 0
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockEmbeddingEngine = MockEmbeddingEngine()
    }

    @Test
    fun testAskQuestion_RelevantReceiptQuery_AnswersWithGroundedCitation() = runBlocking {
        val receiptEntity = ScreenshotEntity(
            id = 50L,
            mediaStoreId = 500L,
            contentUri = "content://images/50",
            displayName = "dinner_bill.jpg",
            relativePath = "Pictures",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            sizeBytes = 100000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "Restaurant Dynasty\nTOTAL: RM 80.00\nPayment: Cash",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f)
        )

        val mockDao = MockDao(listOf(receiptEntity))
        val searchEngine = SemanticSearchEngine(
            embeddingEngine = mockEmbeddingEngine,
            screenshotDao = mockDao
        )

        val coordinator = AskRecallCoordinator(
            context = context,
            searchEngine = searchEngine,
            screenshotDao = mockDao,
            languageModel = LocalGroundedSynthesizer()
        )

        val answer = coordinator.answerQuestion("Find the receipt that shows RM80")

        assertEquals(AnswerStatus.ANSWERED, answer.status)
        assertTrue(answer.citations.isNotEmpty())
        assertEquals(50L, answer.citations.first().screenshotId)
        assertNotNull(answer.calculationBreakdown)
        assertEquals(80.0, answer.calculationBreakdown!!.winningAmount!!.amount, 0.001)
    }

    @Test
    fun testAskQuestion_LowestTotalComparison() = runBlocking {
        val receiptA = ScreenshotEntity(
            id = 101L,
            mediaStoreId = 1010L,
            contentUri = "content://images/101",
            displayName = "groceries_receipt.jpg",
            relativePath = "Pictures",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            sizeBytes = 100000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "Market Fresh\nTOTAL: RM 35.00",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f)
        )

        val receiptB = ScreenshotEntity(
            id = 102L,
            mediaStoreId = 1020L,
            contentUri = "content://images/102",
            displayName = "hardware_receipt.jpg",
            relativePath = "Pictures",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            sizeBytes = 100000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "Hardware Depot\nTOTAL: RM 150.00",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f)
        )

        val mockDao = MockDao(listOf(receiptA, receiptB))
        val searchEngine = SemanticSearchEngine(
            embeddingEngine = mockEmbeddingEngine,
            screenshotDao = mockDao
        )

        val coordinator = AskRecallCoordinator(
            context = context,
            searchEngine = searchEngine,
            screenshotDao = mockDao,
            languageModel = LocalGroundedSynthesizer()
        )

        val answer = coordinator.answerQuestion("Which of these receipts has the lowest total?")

        assertEquals(AnswerStatus.ANSWERED, answer.status)
        assertNotNull(answer.calculationBreakdown)
        assertEquals(101L, answer.calculationBreakdown!!.winningAmount!!.screenshotId)
        assertEquals(35.0, answer.calculationBreakdown!!.winningAmount!!.amount, 0.001)
        assertTrue(answer.citations.any { it.screenshotId == 101L })
    }

    @Test
    fun testAskQuestion_NoMatchingImages_ReturnsAppropriateStatus() = runBlocking {
        val mockDao = MockDao(emptyList())
        val searchEngine = SemanticSearchEngine(
            embeddingEngine = mockEmbeddingEngine,
            screenshotDao = mockDao
        )

        val coordinator = AskRecallCoordinator(
            context = context,
            searchEngine = searchEngine,
            screenshotDao = mockDao
        )

        val answer = coordinator.answerQuestion("Find the quantum physics notes")

        assertEquals(AnswerStatus.NO_MATCHING_IMAGES, answer.status)
        assertTrue(answer.citations.isEmpty())
    }

    @Test
    fun testAskQuestion_EmptyQuestion_ReturnsSafely() = runBlocking {
        val mockDao = MockDao(emptyList())
        val searchEngine = SemanticSearchEngine(
            embeddingEngine = mockEmbeddingEngine,
            screenshotDao = mockDao
        )

        val coordinator = AskRecallCoordinator(
            context = context,
            searchEngine = searchEngine,
            screenshotDao = mockDao
        )

        val answer = coordinator.answerQuestion("   ")
        assertEquals(AnswerStatus.INSUFFICIENT_EVIDENCE, answer.status)
    }
}
