package com.example.core.model

data class SearchResult(
    val screenshot: ScreenshotItem,
    val semanticScore: Float,
    val lexicalScore: Float,
    val visualScore: Float = 0f,
    val combinedScore: Float,
    val snippet: String,
    val matchedTerms: List<String> = emptyList(),
    val explanation: String = "",
    val retrievalSignals: List<String> = emptyList(),
    val hasNearDuplicates: Boolean = false,
    val nearDuplicateCount: Int = 0
)

data class IndexStats(
    val totalDiscovered: Int = 0,
    val totalIndexed: Int = 0,
    val totalEmptyText: Int = 0,
    val totalFailed: Int = 0,
    val totalPending: Int = 0,
    val isIndexingActive: Boolean = false,
    val currentProcessingName: String? = null,
    val currentProgressPercent: Float = 0f,
    val lastScanTime: Long = 0,
    val modelVersion: String = "all-MiniLM-L6-v2-INT8",
    val modelDimensions: Int = 384,
    val visualModelVersion: String = "SpatialLayoutColor-v1",
    val lastSearchLatencyMs: Long = 0,
    val duplicateGroupCount: Int = 0
)
