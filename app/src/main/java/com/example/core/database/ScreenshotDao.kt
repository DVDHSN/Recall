package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.model.IndexingStatus
import kotlinx.coroutines.flow.Flow

data class DuplicateHashInfo(
    val id: Long,
    val mediaStoreId: Long,
    val displayName: String,
    val contentHash: String,
    val perceptualHash: Long,
    val dateModified: Long
)

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM screenshots ORDER BY dateModified DESC")
    fun getAllFlow(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots ORDER BY dateModified DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getById(id: Long): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE mediaStoreId = :mediaStoreId")
    suspend fun getByMediaStoreId(mediaStoreId: Long): ScreenshotEntity?

    @Query("SELECT mediaStoreId FROM screenshots")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Query("SELECT * FROM screenshots WHERE indexingStatus = :status ORDER BY dateModified DESC LIMIT :limit")
    suspend fun getByStatus(status: IndexingStatus, limit: Int = 100): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE indexingStatus IN ('PENDING', 'NEEDS_REINDEX') ORDER BY dateModified DESC LIMIT :limit")
    suspend fun getAwaitingIndexing(limit: Int = 50): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE visualEmbeddingStatus IN ('PENDING', 'NEEDS_REINDEX') AND indexingStatus != 'FAILED' ORDER BY dateModified DESC LIMIT :limit")
    suspend fun getAwaitingVisualEmbedding(limit: Int = 20): List<ScreenshotEntity>

    @Query("UPDATE screenshots SET visualBlob = :visualBlob, visualModelVersion = :visualModelVersion, visualDimension = :visualDimension, visualEmbeddingStatus = :status WHERE id = :id")
    suspend fun updateVisualEmbedding(id: Long, visualBlob: FloatArray?, visualModelVersion: String, visualDimension: Int, status: String)

    @Query("SELECT COUNT(*) FROM screenshots WHERE visualEmbeddingStatus IN ('PENDING', 'NEEDS_REINDEX')")
    fun getVisualPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM screenshots WHERE visualBlob IS NOT NULL")
    suspend fun getAllWithVisualEmbeddings(): List<ScreenshotEntity>

    @Query("UPDATE screenshots SET indexingStatus = 'NEEDS_REINDEX' WHERE indexingStatus = 'FAILED'")
    suspend fun retryFailed()

    @Query("UPDATE screenshots SET indexingStatus = 'PENDING' WHERE indexingStatus = 'PROCESSING'")
    suspend fun resetInterruptedProcessing(): Int

    @Query("SELECT * FROM screenshots WHERE indexingStatus = 'INDEXED' AND embeddingBlob IS NOT NULL")
    suspend fun getAllIndexedWithEmbeddings(): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE indexingStatus IN ('INDEXED', 'EMPTY_TEXT') OR visualBlob IS NOT NULL")
    suspend fun getAllSearchable(): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE categoryLabels LIKE '%' || :category || '%' ORDER BY dateModified DESC")
    fun getByCategoryFlow(category: String): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE imageSource = :imageSource ORDER BY dateModified DESC")
    fun getByImageSourceFlow(imageSource: String): Flow<List<ScreenshotEntity>>

    @Query("SELECT id, mediaStoreId, displayName, contentHash, perceptualHash, dateModified FROM screenshots WHERE perceptualHash != 0")
    suspend fun getAllDuplicateHashInfo(): List<DuplicateHashInfo>

    @Query("SELECT * FROM screenshots WHERE id != :currentId AND perceptualHash != 0")
    suspend fun getCandidateDuplicates(currentId: Long): List<ScreenshotEntity>

    @Query("SELECT COUNT(*) FROM screenshots")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE indexingStatus = 'INDEXED'")
    fun getIndexedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE indexingStatus = 'EMPTY_TEXT'")
    fun getEmptyTextCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE indexingStatus = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE indexingStatus IN ('PENDING', 'NEEDS_REINDEX', 'PROCESSING')")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM screenshots WHERE ocrText LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%' ORDER BY dateModified DESC LIMIT 100")
    suspend fun searchLexical(query: String): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE dateModified >= :startSec AND dateModified <= :endSec ORDER BY dateModified DESC")
    suspend fun searchByDateRange(startSec: Long, endSec: Long): List<ScreenshotEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ScreenshotEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ScreenshotEntity): Long

    @Update
    suspend fun update(entity: ScreenshotEntity)

    @Query("UPDATE screenshots SET indexingStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: IndexingStatus)

    @Query("DELETE FROM screenshots WHERE mediaStoreId NOT IN (:keepMediaStoreIds) AND relativePath != 'Imported'")
    suspend fun deleteMissing(keepMediaStoreIds: List<Long>): Int

    @Query("DELETE FROM screenshots WHERE relativePath != 'Imported'")
    suspend fun deleteAllMediaStoreItems(): Int

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM screenshots")
    suspend fun clearAll()

    @Query("UPDATE screenshots SET indexingStatus = 'NEEDS_REINDEX', embeddingBlob = NULL, ocrText = '', visualEmbeddingStatus = 'NEEDS_REINDEX'")
    suspend fun resetAllForReindexing()
}
