package com.example

import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import com.example.core.search.suggestion.ProactiveSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveSearchEngineTest {

    @Test
    fun testProactiveCategoryCountingAndPrioritization() {
        val screenshots = listOf(
            ScreenshotItem(
                id = 1,
                mediaStoreId = 101L,
                contentUri = "content://media/1",
                displayName = "receipt_coffee.jpg",
                relativePath = "Pictures/Screenshots",
                mimeType = "image/jpeg",
                dateAdded = 1000L,
                dateModified = 2000L,
                width = 1080,
                height = 1920,
                sizeBytes = 1024L,
                ocrText = "Coffee Shop Total $4.50 Americano",
                categoryLabels = listOf(ScreenshotCategory.FINANCE_RECEIPTS)
            ),
            ScreenshotItem(
                id = 2,
                mediaStoreId = 102L,
                contentUri = "content://media/2",
                displayName = "receipt_dinner.jpg",
                relativePath = "Pictures/Screenshots",
                mimeType = "image/jpeg",
                dateAdded = 1100L,
                dateModified = 2100L,
                width = 1080,
                height = 1920,
                sizeBytes = 1024L,
                ocrText = "Italian Bistro Total $42.00 Pasta",
                categoryLabels = listOf(ScreenshotCategory.FINANCE_RECEIPTS)
            ),
            ScreenshotItem(
                id = 3,
                mediaStoreId = 103L,
                contentUri = "content://media/3",
                displayName = "code_snippet.jpg",
                relativePath = "Pictures/Screenshots",
                mimeType = "image/jpeg",
                dateAdded = 1200L,
                dateModified = 2200L,
                width = 1080,
                height = 1920,
                sizeBytes = 1024L,
                ocrText = "fun executeSearch() { println(error) }",
                categoryLabels = listOf(ScreenshotCategory.CODE_ERROR)
            )
        )

        val recents = listOf("Wifi password", "Dinner receipt")
        val suggestions = ProactiveSearchEngine.computeSuggestions(screenshots, recents)

        // Verify recent queries passed through
        assertEquals(2, suggestions.recentQueries.size)
        assertEquals("Wifi password", suggestions.recentQueries[0])

        // Verify category ranking (FINANCE_RECEIPTS has 2 items, so it should be first)
        assertTrue(suggestions.suggestedCategories.isNotEmpty())
        assertEquals(ScreenshotCategory.FINANCE_RECEIPTS, suggestions.suggestedCategories[0].category)
        assertEquals(2, suggestions.suggestedCategories[0].count)

        // Verify OCR keywords extraction
        val keywords = suggestions.topOcrKeywords.map { it.keyword.lowercase() }
        assertTrue(keywords.contains("coffee") || keywords.contains("bistro") || keywords.contains("pasta") || keywords.contains("executesearch"))

        // Verify recent items ordered by dateModified descending
        assertEquals(3, suggestions.recentItems.size)
        assertEquals(3L, suggestions.recentItems[0].id)
    }
}
