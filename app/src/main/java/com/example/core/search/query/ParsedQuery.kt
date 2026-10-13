package com.example.core.search.query

import com.example.core.model.ScreenshotCategory

data class DateRangeConstraint(
    val label: String,
    val startTimestampSec: Long,
    val endTimestampSec: Long
) {
    fun matches(dateSec: Long): Boolean {
        return dateSec in startTimestampSec..endTimestampSec
    }
}

enum class SearchMode(val displayName: String) {
    HYBRID("Hybrid Search"),
    SEMANTIC_ONLY("Semantic Only"),
    EXACT_ONLY("Exact Text"),
    VISUAL_SIMILAR("Visually Similar")
}

enum class QueryIntent(val displayName: String) {
    VISUAL_DOMINANT("Visual Search"),
    TEXTUAL_DOMINANT("Text & Keywords"),
    BALANCED_HYBRID("Balanced Hybrid")
}

data class InferredFilter(
    val id: String,
    val label: String,
    val description: String
)

data class ParsedQuery(
    val rawQuery: String,
    val cleanSearchText: String,
    val dateConstraint: DateRangeConstraint? = null,
    val topicConstraint: ScreenshotCategory? = null,
    val negativeTerms: List<String> = emptyList(),
    val exactPhrases: List<String> = emptyList(),
    val technicalIdentifiers: List<String> = emptyList(),
    val searchMode: SearchMode = SearchMode.HYBRID,
    val queryIntent: QueryIntent = QueryIntent.BALANCED_HYBRID,
    val inferredFilters: List<InferredFilter> = emptyList()
)
