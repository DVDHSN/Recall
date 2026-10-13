package com.example

import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.model.IndexingStatus
import com.example.core.search.SemanticSearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SemanticSearchEngineTest {

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val n = sqrt(sum)
        return if (n > 0) FloatArray(v.size) { v[it] / n } else v
    }

    private class MockEmbeddingEngine : TextEmbeddingEngine {
        override val modelVersion: String = "test-mock-v1"
        override val embeddingDimension: Int = 4

        override suspend fun generateEmbedding(text: String): FloatArray {
            val lower = text.lowercase()
            return when {
                lower.contains("chickpea") || lower.contains("curry") -> floatArrayOf(1f, 0f, 0f, 0f)
                lower.contains("wifi") || lower.contains("password") -> floatArrayOf(0f, 1f, 0f, 0f)
                lower.contains("python") || lower.contains("null") -> floatArrayOf(0f, 0f, 1f, 0f)
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
        override suspend fun getAllDuplicateHashInfo(): List<com.example.core.database.DuplicateHashInfo> = emptyList()
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
        override suspend fun retryFailed() {}
    }

    @Test
    fun testSemanticSearchRanking() = runBlocking {
        val recipeScreenshot = ScreenshotEntity(
            id = 1,
            mediaStoreId = 101,
            contentUri = "content://media/1",
            displayName = "Screenshot_Recipe.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 50000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "Creamy coconut chickpea curry with spinach and garlic",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f)
        )

        val wifiScreenshot = ScreenshotEntity(
            id = 2,
            mediaStoreId = 102,
            contentUri = "content://media/2",
            displayName = "Screenshot_Wifi.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 60000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "SSID: Campus_Wifi Password: BlueRiver#9281 IP: 192.168.1.1",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0f, 1f, 0f, 0f)
        )

        val dao = MockDao(listOf(recipeScreenshot, wifiScreenshot))
        val engine = SemanticSearchEngine(MockEmbeddingEngine(), dao)

        val results = engine.search("recipe with chickpeas")

        // First result must be the recipe screenshot
        assertTrue(results.isNotEmpty())
        assertEquals(1L, results[0].screenshot.id)
        assertTrue(results[0].semanticScore > 0.8f)
    }

    @Test
    fun testExactTechnicalIdentifierMatching() = runBlocking {
        val wifiScreenshot = ScreenshotEntity(
            id = 2,
            mediaStoreId = 102,
            contentUri = "content://media/2",
            displayName = "Screenshot_Wifi.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 60000,
            dateAdded = 1000,
            dateModified = 1000,
            ocrText = "Router IP: 192.168.1.1",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0f, 1f, 0f, 0f)
        )

        val dao = MockDao(listOf(wifiScreenshot))
        val engine = SemanticSearchEngine(MockEmbeddingEngine(), dao)

        val results = engine.search("192.168.1.1")
        assertTrue(results.isNotEmpty())
        assertEquals(2L, results[0].screenshot.id)
        assertTrue(results[0].lexicalScore >= 0.5f)
    }
}
