package com.example

import com.example.core.model.ScreenshotItem
import com.example.core.qa.engine.CitationValidator
import com.example.core.qa.model.EvidenceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationValidatorTest {

    private fun createEvidence(id: Long, name: String, ocr: String): EvidenceItem {
        val item = ScreenshotItem(
            id = id,
            mediaStoreId = id * 10,
            contentUri = "content://media/$id",
            displayName = name,
            relativePath = "Pictures",
            mimeType = "image/png",
            width = 1000,
            height = 1000,
            sizeBytes = 10000,
            dateAdded = 0,
            dateModified = 0,
            ocrText = ocr
        )
        return EvidenceItem(
            screenshot = item,
            matchedOcrSnippet = ocr,
            allOcrText = ocr,
            categories = listOf("Document"),
            relevanceScore = 0.85f,
            visualSignals = emptyList()
        )
    }

    @Test
    fun testValidateCitations_GenuineIdPreserved() {
        val evidenceList = listOf(
            createEvidence(101L, "receipt_dinner.png", "TOTAL: RM 80.00"),
            createEvidence(102L, "receipt_coffee.png", "TOTAL: RM 12.00")
        )

        val modelAnswer = "The receipt showing RM80 is [IMG:101] from dinner."
        val validated = CitationValidator.validateCitations(modelAnswer, evidenceList)

        assertEquals(1, validated.size)
        assertEquals(101L, validated[0].screenshotId)
        assertEquals("receipt_dinner.png", validated[0].displayName)
    }

    @Test
    fun testValidateCitations_HallucinatedIdRejected() {
        val evidenceList = listOf(
            createEvidence(101L, "receipt_dinner.png", "TOTAL: RM 80.00")
        )

        // Model hallucinates ID 999 which does NOT exist in evidence
        val modelAnswer = "Found the document at [IMG:999] with extra details at [IMG:101]."
        val validated = CitationValidator.validateCitations(modelAnswer, evidenceList)

        // Only genuine ID 101 should be validated; 999 must be discarded
        assertEquals(1, validated.size)
        assertEquals(101L, validated[0].screenshotId)

        val sanitized = CitationValidator.sanitizeAnswerText(modelAnswer, validated)
        assertFalse(sanitized.contains("999"))
        assertTrue(sanitized.contains("[receipt_dinner.png]"))
    }

    @Test
    fun testValidateCitations_EmptyEvidence_ReturnsEmpty() {
        val modelAnswer = "Found in [IMG:50]."
        val validated = CitationValidator.validateCitations(modelAnswer, emptyList())

        assertTrue(validated.isEmpty())
    }

    @Test
    fun testSanitizeAnswerText_ReplacesWithDisplayName() {
        val evidenceList = listOf(
            createEvidence(42L, "error_screenshot.png", "Error 404 Not Found")
        )
        val validated = CitationValidator.validateCitations("See [IMG:42]", evidenceList)
        val sanitized = CitationValidator.sanitizeAnswerText("See [IMG:42]", validated)

        assertEquals("See [error_screenshot.png]", sanitized)
    }
}
