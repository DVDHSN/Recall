package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.IndexingStatus
import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem

@Entity(
    tableName = "screenshots",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["indexingStatus"]),
        Index(value = ["dateModified"]),
        Index(value = ["displayName"]),
        Index(value = ["contentHash"]),
        Index(value = ["perceptualHash"]),
        Index(value = ["imageSource"]),
        Index(value = ["bucketName"]),
        Index(value = ["visualEmbeddingStatus"])
    ]
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val dateCaptured: Long = 0,
    val contentFingerprint: String = "",
    val ocrText: String = "",
    val indexingStatus: IndexingStatus = IndexingStatus.PENDING,
    val lastIndexedAt: Long = 0,
    val embeddingModelVersion: String = "",
    val embeddingDimension: Int = 0,
    val embeddingBlob: FloatArray? = null,
    val contentHash: String = "",
    val perceptualHash: Long = 0L,
    val visualBlob: FloatArray? = null,
    val categoryLabels: String = "",
    val classificationConfidence: Float = 0f,
    val ocrError: String? = null,
    val embeddingError: String? = null,
    val bucketName: String = "",
    val imageSource: String = "OTHER",
    val visualModelVersion: String = "",
    val visualDimension: Int = 0,
    val visualEmbeddingStatus: String = "PENDING",
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L
) {
    fun toDomain(): ScreenshotItem {
        val categories = categoryLabels.split(",")
            .mapNotNull { ScreenshotCategory.fromString(it.trim()) }

        return ScreenshotItem(
            id = id,
            mediaStoreId = mediaStoreId,
            contentUri = contentUri,
            displayName = displayName,
            relativePath = relativePath,
            mimeType = mimeType,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            dateAdded = dateAdded,
            dateModified = dateModified,
            dateCaptured = dateCaptured,
            contentFingerprint = contentFingerprint,
            ocrText = ocrText,
            indexingStatus = indexingStatus,
            lastIndexedAt = lastIndexedAt,
            embeddingModelVersion = embeddingModelVersion,
            embeddingDimension = embeddingDimension,
            embeddingVector = embeddingBlob,
            contentHash = contentHash,
            perceptualHash = perceptualHash,
            visualBlob = visualBlob,
            categoryLabels = categories,
            classificationConfidence = classificationConfidence,
            ocrError = ocrError,
            embeddingError = embeddingError,
            bucketName = bucketName,
            imageSource = imageSource,
            visualModelVersion = visualModelVersion,
            visualDimension = visualDimension,
            visualEmbeddingStatus = visualEmbeddingStatus,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScreenshotEntity
        if (id != other.id) return false
        if (mediaStoreId != other.mediaStoreId) return false
        if (contentUri != other.contentUri) return false
        if (displayName != other.displayName) return false
        if (indexingStatus != other.indexingStatus) return false
        if (ocrText != other.ocrText) return false
        if (contentHash != other.contentHash) return false
        if (perceptualHash != other.perceptualHash) return false
        if (bucketName != other.bucketName) return false
        if (imageSource != other.imageSource) return false
        if (visualModelVersion != other.visualModelVersion) return false
        if (visualDimension != other.visualDimension) return false
        if (visualEmbeddingStatus != other.visualEmbeddingStatus) return false
        if (embeddingBlob != null) {
            if (other.embeddingBlob == null) return false
            if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false
        } else if (other.embeddingBlob != null) return false
        if (visualBlob != null) {
            if (other.visualBlob == null) return false
            if (!visualBlob.contentEquals(other.visualBlob)) return false
        } else if (other.visualBlob != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mediaStoreId.hashCode()
        result = 31 * result + contentUri.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + ocrText.hashCode()
        result = 31 * result + indexingStatus.hashCode()
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + perceptualHash.hashCode()
        result = 31 * result + bucketName.hashCode()
        result = 31 * result + imageSource.hashCode()
        result = 31 * result + visualModelVersion.hashCode()
        result = 31 * result + visualDimension.hashCode()
        result = 31 * result + visualEmbeddingStatus.hashCode()
        result = 31 * result + (embeddingBlob?.contentHashCode() ?: 0)
        result = 31 * result + (visualBlob?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        fun fromDomain(item: ScreenshotItem): ScreenshotEntity {
            val categoriesStr = item.categoryLabels.joinToString(",") { it.name }
            return ScreenshotEntity(
                id = item.id,
                mediaStoreId = item.mediaStoreId,
                contentUri = item.contentUri,
                displayName = item.displayName,
                relativePath = item.relativePath,
                mimeType = item.mimeType,
                width = item.width,
                height = item.height,
                sizeBytes = item.sizeBytes,
                dateAdded = item.dateAdded,
                dateModified = item.dateModified,
                dateCaptured = item.dateCaptured,
                contentFingerprint = item.contentFingerprint,
                ocrText = item.ocrText,
                indexingStatus = item.indexingStatus,
                lastIndexedAt = item.lastIndexedAt,
                embeddingModelVersion = item.embeddingModelVersion,
                embeddingDimension = item.embeddingDimension,
                embeddingBlob = item.embeddingVector,
                contentHash = item.contentHash,
                perceptualHash = item.perceptualHash,
                visualBlob = item.visualBlob,
                categoryLabels = categoriesStr,
                classificationConfidence = item.classificationConfidence,
                ocrError = item.ocrError,
                embeddingError = item.embeddingError,
                bucketName = item.bucketName,
                imageSource = item.imageSource,
                visualModelVersion = item.visualModelVersion,
                visualDimension = item.visualDimension,
                visualEmbeddingStatus = item.visualEmbeddingStatus,
                retryCount = item.retryCount,
                lastAttemptAt = item.lastAttemptAt
            )
        }
    }
}
