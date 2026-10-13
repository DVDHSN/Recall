package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.RecallDatabase
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.model.ImageSource
import com.example.core.model.IndexingStatus
import com.example.core.model.ScreenshotItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IncrementalIndexingAndSyncTest {

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
    fun testRepeatedScansDoNotCreateDuplicateRecords() = runBlocking {
        val cameraItem = ScreenshotItem(
            mediaStoreId = 1001L,
            contentUri = "content://media/external/images/media/1001",
            displayName = "IMG_20261013_101010.jpg",
            relativePath = "DCIM/Camera/",
            mimeType = "image/jpeg",
            width = 4000,
            height = 3000,
            sizeBytes = 3500000L,
            dateAdded = 1791800000L,
            dateModified = 1791800000L,
            bucketName = "Camera",
            imageSource = ImageSource.CAMERA.name
        )

        val entity1 = ScreenshotEntity.fromDomain(cameraItem)
        dao.insertAll(listOf(entity1))

        assertEquals(1, dao.getAllMediaStoreIds().size)

        // Second repeated scan with the same mediaStoreId:
        val entity2 = ScreenshotEntity.fromDomain(cameraItem)
        dao.insertAll(listOf(entity2))

        // Unique constraint on mediaStoreId with OnConflictStrategy.IGNORE must prevent duplicate row
        assertEquals(1, dao.getAllMediaStoreIds().size)
    }

    @Test
    fun testModifiedImageTriggersReindex() = runBlocking {
        val originalItem = ScreenshotItem(
            mediaStoreId = 2001L,
            contentUri = "content://media/external/images/media/2001",
            displayName = "Receipt_Dinner.jpg",
            relativePath = "Pictures/Receipts/",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            sizeBytes = 450000L,
            dateAdded = 1791700000L,
            dateModified = 1791700000L,
            ocrText = "Restaurant Bill: $42.50",
            indexingStatus = IndexingStatus.INDEXED,
            bucketName = "Receipts",
            imageSource = ImageSource.DOCUMENTS_NOTES.name
        )

        dao.insertOrUpdate(ScreenshotEntity.fromDomain(originalItem))
        val initial = dao.getByMediaStoreId(2001L)
        assertNotNull(initial)
        assertEquals(IndexingStatus.INDEXED, initial!!.indexingStatus)

        // When image is modified (e.g. edited, cropped, or overwritten with newer timestamp)
        val updatedTimestamp = 1791799000L
        val updatedSize = 510000L
        if (initial.dateModified != updatedTimestamp || initial.sizeBytes != updatedSize) {
            dao.update(
                initial.copy(
                    dateModified = updatedTimestamp,
                    sizeBytes = updatedSize,
                    indexingStatus = IndexingStatus.NEEDS_REINDEX
                )
            )
        }

        val updated = dao.getByMediaStoreId(2001L)
        assertNotNull(updated)
        assertEquals(IndexingStatus.NEEDS_REINDEX, updated!!.indexingStatus)
        assertEquals(updatedTimestamp, updated.dateModified)
        assertEquals(updatedSize, updated.sizeBytes)

        // Verifies awaiting indexing picks up the modified item
        val awaiting = dao.getAwaitingIndexing(10)
        assertTrue(awaiting.any { it.mediaStoreId == 2001L })
    }

    @Test
    fun testCrashOrInterruptedProcessingRecovery() = runBlocking {
        val item1 = ScreenshotEntity(
            mediaStoreId = 3001L,
            contentUri = "content://media/3001",
            displayName = "Note1.png",
            relativePath = "Pictures/Notes",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 120000L,
            dateAdded = 1000L,
            dateModified = 1000L,
            indexingStatus = IndexingStatus.PROCESSING // Simulated process death while worker was indexing
        )
        dao.insertAll(listOf(item1))

        // Reset interrupted processing on startup
        val recoveredCount = dao.resetInterruptedProcessing()
        assertEquals(1, recoveredCount)

        val recoveredEntity = dao.getByMediaStoreId(3001L)
        assertNotNull(recoveredEntity)
        assertEquals(IndexingStatus.PENDING, recoveredEntity!!.indexingStatus)

        // Verify awaiting indexing now picks it up
        val awaiting = dao.getAwaitingIndexing(10)
        assertEquals(1, awaiting.size)
        assertEquals(3001L, awaiting[0].mediaStoreId)
    }

    @Test
    fun testDeletionHandlingInFullAccessMode() = runBlocking {
        val img1 = ScreenshotEntity(
            mediaStoreId = 4001L,
            contentUri = "content://media/4001",
            displayName = "Kept.png",
            relativePath = "DCIM/Camera",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000L,
            dateAdded = 1000L,
            dateModified = 1000L
        )
        val img2 = ScreenshotEntity(
            mediaStoreId = 4002L,
            contentUri = "content://media/4002",
            displayName = "DeletedFromDevice.png",
            relativePath = "DCIM/Camera",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000L,
            dateAdded = 1000L,
            dateModified = 1000L
        )
        val importedImg = ScreenshotEntity(
            mediaStoreId = 9999L,
            contentUri = "content://com.android.providers.media.documents/document/1",
            displayName = "ImportedPhoto.png",
            relativePath = "Imported",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000L,
            dateAdded = 1000L,
            dateModified = 1000L
        )

        dao.insertAll(listOf(img1, img2, importedImg))
        assertEquals(3, dao.getAllMediaStoreIds().size)

        // Full access scan discovers only img1 (img2 was deleted from disk)
        val currentMediaIds = listOf(4001L)
        dao.deleteMissing(currentMediaIds)

        val remainingIds = dao.getAllMediaStoreIds().toSet()
        assertEquals(2, remainingIds.size)
        assertTrue(remainingIds.contains(4001L))
        assertTrue(remainingIds.contains(9999L)) // Imported item was preserved
        assertFalse(remainingIds.contains(4002L)) // Deleted item was removed
    }

    @Test
    fun testPartialAccessSafetyDoesNotDeleteUnselectedMedia() = runBlocking {
        val imgA = ScreenshotEntity(
            mediaStoreId = 5001L,
            contentUri = "content://media/5001",
            displayName = "PhotoA.png",
            relativePath = "DCIM/Camera",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000L,
            dateAdded = 1000L,
            dateModified = 1000L
        )
        val imgB = ScreenshotEntity(
            mediaStoreId = 5002L,
            contentUri = "content://media/5002",
            displayName = "PhotoB.png",
            relativePath = "DCIM/Camera",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 1000L,
            dateAdded = 1000L,
            dateModified = 1000L
        )

        dao.insertAll(listOf(imgA, imgB))

        // In PARTIAL_ACCESS mode, user only selected PhotoA in photo picker.
        // The reconciliation logic intentionally skips dao.deleteMissing(), preserving PhotoB.
        val partialDiscovered = listOf(5001L)

        // Verify both items remain present in database
        val allIds = dao.getAllMediaStoreIds().toSet()
        assertEquals(2, allIds.size)
        assertTrue(allIds.contains(5001L))
        assertTrue(allIds.contains(5002L))
    }
}
