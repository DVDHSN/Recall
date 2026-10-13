package com.example

import com.example.core.model.ScreenshotCategory
import com.example.core.search.query.QueryParser
import com.example.core.search.query.SearchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParserTest {

    private val parser = QueryParser(clockNowMsProvider = { 1791870000000L }) // fixed mock timestamp

    @Test
    fun testDateConstraintExtraction() {
        val parsedYesterday = parser.parse("The Python error I saw yesterday")
        assertNotNull(parsedYesterday.dateConstraint)
        assertEquals("Yesterday", parsedYesterday.dateConstraint?.label)
        assertTrue(parsedYesterday.inferredFilters.any { it.label.contains("Yesterday") })

        val parsedLastWeek = parser.parse("wifi password from last week")
        assertNotNull(parsedLastWeek.dateConstraint)
        assertEquals("Past 7 Days", parsedLastWeek.dateConstraint?.label)
    }

    @Test
    fun testTopicIntentExtraction() {
        val parsedRecipe = parser.parse("That recipe with chickpeas and coconut milk")
        assertEquals(ScreenshotCategory.RECIPES, parsedRecipe.topicConstraint)

        val parsedCode = parser.parse("NullPointerException traceback error")
        assertEquals(ScreenshotCategory.CODE_ERROR, parsedCode.topicConstraint)

        val parsedWifi = parser.parse("school wifi password")
        assertEquals(ScreenshotCategory.NETWORK_TECH, parsedWifi.topicConstraint)
    }

    @Test
    fun testNegativeTermExtraction() {
        val parsed = parser.parse("transformers not electrical")
        assertTrue(parsed.negativeTerms.contains("electrical"))
        assertTrue(parsed.inferredFilters.any { it.label.contains("electrical") })
    }

    @Test
    fun testTechnicalIdentifierExtraction() {
        val parsed = parser.parse("connect to 192.168.1.1 on port 80")
        assertTrue(parsed.technicalIdentifiers.contains("192.168.1.1"))

        val parsedError = parser.parse("HTTP 404 resource missing")
        assertTrue(parsedError.technicalIdentifiers.any { it.contains("404") })
    }

    @Test
    fun testQuotedExactPhraseExtraction() {
        val parsed = parser.parse("\"Campus_HighSpeed_5G\" credentials")
        assertTrue(parsed.exactPhrases.contains("Campus_HighSpeed_5G"))
    }
}
