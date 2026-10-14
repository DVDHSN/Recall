package com.example.core.search.suggestion

import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import java.util.Locale

data class CategorySuggestion(
    val category: ScreenshotCategory,
    val count: Int,
    val label: String
)

data class OcrKeywordSuggestion(
    val keyword: String,
    val count: Int
)

data class ProactiveSearchSuggestions(
    val recentQueries: List<String> = emptyList(),
    val suggestedCategories: List<CategorySuggestion> = emptyList(),
    val topOcrKeywords: List<OcrKeywordSuggestion> = emptyList(),
    val recentItems: List<ScreenshotItem> = emptyList()
)

object ProactiveSearchEngine {

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "are", "was",
        "were", "will", "your", "all", "can", "has", "not", "but", "you", "they",
        "our", "what", "which", "when", "where", "how", "more", "about", "into",
        "than", "them", "some", "other", "there", "then", "also", "been", "only",
        "time", "com", "http", "https", "www", "null", "true", "false", "item"
    )

    fun computeSuggestions(
        screenshots: List<ScreenshotItem>,
        recentQueries: List<String>
    ): ProactiveSearchSuggestions {
        // 1. Compute category counts from actual indexed items
        val categoryCountMap = mutableMapOf<ScreenshotCategory, Int>()
        for (item in screenshots) {
            for (cat in item.categoryLabels) {
                categoryCountMap[cat] = (categoryCountMap[cat] ?: 0) + 1
            }
        }

        val suggestedCategories = ScreenshotCategory.entries
            .map { cat ->
                CategorySuggestion(
                    category = cat,
                    count = categoryCountMap[cat] ?: 0,
                    label = cat.displayName
                )
            }
            .filter { it.count > 0 || it.category == ScreenshotCategory.FINANCE_RECEIPTS || it.category == ScreenshotCategory.CODE_ERROR }
            .sortedByDescending { it.count }

        // 2. Extract high-frequency, high-signal OCR keywords
        val keywordFrequency = mutableMapOf<String, Int>()
        for (item in screenshots) {
            val text = item.ocrText
            if (text.isBlank()) continue

            // Split into tokens, sanitize punctuation
            val tokens = text.split("\\s+".toRegex())
            val seenInItem = mutableSetOf<String>()

            for (rawToken in tokens) {
                val clean = rawToken.replace("[^a-zA-Z0-9._-]".toRegex(), "").trim()
                if (clean.length in 3..25) {
                    val lower = clean.lowercase(Locale.ROOT)
                    if (!STOP_WORDS.contains(lower) && !clean.all { it.isDigit() }) {
                        // Keep proper capitalization if available
                        val displayWord = if (clean.all { it.isUpperCase() }) clean else clean.replaceFirstChar { it.uppercase() }
                        if (seenInItem.add(lower)) {
                            keywordFrequency[displayWord] = (keywordFrequency[displayWord] ?: 0) + 1
                        }
                    }
                }
            }
        }

        val topOcrKeywords = keywordFrequency.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { OcrKeywordSuggestion(keyword = it.key, count = it.value) }

        // 3. Extract most recently captured / modified items
        val recentItems = screenshots
            .sortedByDescending { it.dateModified }
            .take(6)

        return ProactiveSearchSuggestions(
            recentQueries = recentQueries.take(6),
            suggestedCategories = suggestedCategories,
            topOcrKeywords = topOcrKeywords,
            recentItems = recentItems
        )
    }
}
