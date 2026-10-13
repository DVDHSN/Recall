package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.RecallDatabase
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.model.IndexingStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IndexingRecoveryTest {

    private lateinit var context: Context
    private lateinit var database: RecallDatabase
    private lateinit var dao: ScreenshotDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.screenshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInterruptedProcessingIsResetToPending() = runBlocking {
        // Arrange: Insert an item stuck in PROCESSING status (e.g. app terminated unexpectedly during indexing)
        val entity = ScreenshotEntity(
            id = 1,
            mediaStoreId = 201,
            contentUri = "content://media/201",
            displayName = "interrupted.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 12000,
            dateAdded = 1000,
            dateModified = 1000,
            indexingStatus = IndexingStatus.PROCESSING,
            retryCount = 1
        )
        dao.insertOrUpdate(entity)

        // Act: resetInterruptedProcessing runs on indexer startup / scan
        val resetCount = dao.resetInterruptedProcessing()
        assertEquals("Should have reset 1 interrupted item", 1, resetCount)

        // Assert: Item is back to PENDING and eligible for indexing
        val recovered = dao.getById(1)
        assertEquals(IndexingStatus.PENDING, recovered?.indexingStatus)
        assertEquals(1, recovered?.retryCount)
    }

    @Test
    fun testRetryCountLimitsAndQuarantineAfterThreeFailures() = runBlocking {
        // Arrange: Insert an item with retryCount = 2
        val entity = ScreenshotEntity(
            id = 2,
            mediaStoreId = 202,
            contentUri = "content://media/202",
            displayName = "failing_image.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 5000,
            dateAdded = 1000,
            dateModified = 1000,
            indexingStatus = IndexingStatus.NEEDS_REINDEX,
            retryCount = 2
        )
        dao.insertOrUpdate(entity)

        // Verify it is picked up by getAwaitingIndexing because retryCount < 3
        val awaiting = dao.getAwaitingIndexing(limit = 10)
        assertTrue("Item with retryCount < 3 should be retrieved", awaiting.any { it.id == 2L })

        // Simulate 3rd failure transitioning into FAILED quarantine
        val quarantined = entity.copy(
            indexingStatus = IndexingStatus.FAILED,
            retryCount = 3,
            ocrError = "Failed to open or decode image (Attempt 3/3)"
        )
        dao.update(quarantined)

        // Verify quarantined items are excluded from normal getAwaitingIndexing loop (prevents infinite loop)
        val awaitingAfterFailure = dao.getAwaitingIndexing(limit = 10)
        assertTrue("Quarantined item must NOT be retrieved in normal indexing batch", awaitingAfterFailure.none { it.id == 2L })
    }

    @Test
    fun testManualRetryResetsQuarantinedItems() = runBlocking {
        // Arrange: Insert 2 failed items
        val failed1 = ScreenshotEntity(
            id = 10,
            mediaStoreId = 210,
            contentUri = "content://media/210",
            displayName = "corrupted_1.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000,
            dateAdded = 1000,
            dateModified = 1000,
            indexingStatus = IndexingStatus.FAILED,
            retryCount = 3,
            ocrError = "Failed to open or decode image (Attempt 3/3)"
        )
        val failed2 = ScreenshotEntity(
            id = 11,
            mediaStoreId = 211,
            contentUri = "content://media/211",
            displayName = "corrupted_2.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000,
            dateAdded = 1000,
            dateModified = 1000,
            indexingStatus = IndexingStatus.FAILED,
            retryCount = 3,
            ocrError = "Inaccessible or permission revoked"
        )
        dao.insertOrUpdate(failed1)
        dao.insertOrUpdate(failed2)

        // Act: User taps "Retry Failed Images" from UI
        val retriedCount = dao.retryFailed()
        assertEquals("Should reset 2 failed items", 2, retriedCount)

        // Assert: Both items reset to PENDING with retryCount = 0 and cleared error
        val item1 = dao.getById(10)
        val item2 = dao.getById(11)

        assertEquals(IndexingStatus.PENDING, item1?.indexingStatus)
        assertEquals(0, item1?.retryCount)
        assertEquals(null, item1?.ocrError)

        assertEquals(IndexingStatus.PENDING, item2?.indexingStatus)
        assertEquals(0, item2?.retryCount)
        assertEquals(null, item2?.ocrError)

        // They are now retried in the next indexing cycle
        val awaiting = dao.getAwaitingIndexing(limit = 10)
        assertEquals(2, awaiting.size)
    }
}
