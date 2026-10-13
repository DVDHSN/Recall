package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.DuplicateHashInfo
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.model.IndexingStatus
import com.example.core.qa.AskRecallCoordinator
import com.example.core.qa.engine.OnDeviceLanguageModel
import com.example.core.qa.engine.ModelOutput
import com.example.core.qa.model.CalculationBreakdown
import com.example.core.qa.model.EvidenceItem
import com.example.core.search.SemanticSearchEngine
import com.example.core.search.query.SearchMode
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
class NormalSearchIndependentTest {

    private lateinit var context: Context

    private class MockEmbeddingEngine : TextEmbeddingEngine {
        override val modelVersion: String = "test-mock-v1"
        override val embeddingDimension: Int = 4

        override suspend fun generateEmbedding(text: String): FloatArray {
            val lower = text.lowercase()
            return when {
                lower.contains("wifi") || lower.contains("password") -> floatArrayOf(1f, 0f, 0f, 0f)
                else -> floatArrayOf(0f, 0f, 0f, 1f)
            }
        }
        override fun close() {}
    }

    private class FailingLanguageModel : OnDeviceLanguageModel {
        override val modelName: String = "CrashingModel"
        override val isReady: Boolean = false
        override suspend fun generateAnswer(
            question: String,
            evidence: List<EvidenceItem>,
            calculationBreakdown: CalculationBreakdown?
        ): ModelOutput {
            throw RuntimeException("Simulated on-device LLM OOM or initialization failure")
        }
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
    }

    @Test
    fun testNormalSearch_WorksIndependentlyEvenIfLanguageModelFails() = runBlocking {
        val sampleEntity = ScreenshotEntity(
            id = 1L,
            mediaStoreId = 100L,
            contentUri = "content://images/1",
            displayName = "wifi_card.png",
            relativePath = "Pictures",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 50000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "Campus Wi-Fi Password: SecretPass123",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f)
        )

        val mockDao = MockDao(listOf(sampleEntity))
        val searchEngine = SemanticSearchEngine(
            embeddingEngine = MockEmbeddingEngine(),
            screenshotDao = mockDao
        )

        // 1. Ask Recall coordinator with crashing model
        val coordinator = AskRecallCoordinator(
            context = context,
            searchEngine = searchEngine,
            screenshotDao = mockDao,
            languageModel = FailingLanguageModel()
        )

        // Coordinator handles error gracefully without throwing
        val askAnswer = coordinator.answerQuestion("What is the wifi password?")
        assertNotNull(askAnswer)

        // 2. Normal search MUST run cleanly and return genuine results without failure
        val normalSearchResults = searchEngine.search("wifi password", SearchMode.HYBRID)
        assertEquals(1, normalSearchResults.size)
        assertEquals(1L, normalSearchResults[0].screenshot.id)
        assertTrue(normalSearchResults[0].snippet.contains("SecretPass123"))
    }
}
