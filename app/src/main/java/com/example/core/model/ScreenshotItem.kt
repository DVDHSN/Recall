package com.example.core.model

data class ScreenshotItem(
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
    val embeddingVector: FloatArray? = null,
    val contentHash: String = "",
    val perceptualHash: Long = 0L,
    val visualVector: FloatArray? = null,
    val categoryLabels: List<ScreenshotCategory> = emptyList(),
    val classificationConfidence: Float = 0f,
    val ocrError: String? = null,
    val embeddingError: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScreenshotItem
        if (id != other.id) return false
        if (mediaStoreId != other.mediaStoreId) return false
        if (contentUri != other.contentUri) return false
        if (displayName != other.displayName) return false
        if (ocrText != other.ocrText) return false
        if (indexingStatus != other.indexingStatus) return false
        if (contentHash != other.contentHash) return false
        if (perceptualHash != other.perceptualHash) return false
        if (embeddingVector != null) {
            if (other.embeddingVector == null) return false
            if (!embeddingVector.contentEquals(other.embeddingVector)) return false
        } else if (other.embeddingVector != null) return false
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
        result = 31 * result + (embeddingVector?.contentHashCode() ?: 0)
        return result
    }
}
