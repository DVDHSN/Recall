package com.example.core.qa.engine

import com.example.core.qa.model.EvidenceItem
import com.example.core.qa.model.ValidatedCitation
import java.util.regex.Pattern

object CitationValidator {

    // Matches citation tokens like [IMG:123], [img:123], [123], or #123
    private val CITATION_TOKEN_PATTERN = Pattern.compile(
        """\[(?:IMG:|img:)?(\d+)\]|#(\d+)"""
    )

    /**
     * Extracts raw candidate IDs mentioned in model answer text, validates them
     * against actual retrieved evidence records, and constructs ValidatedCitation instances.
     */
    fun validateCitations(
        modelAnswerText: String,
        retrievedEvidence: List<EvidenceItem>
    ): List<ValidatedCitation> {
        val validMap = retrievedEvidence.associateBy { it.screenshot.id }
        val referencedIds = mutableSetOf<Long>()

        val matcher = CITATION_TOKEN_PATTERN.matcher(modelAnswerText)
        while (matcher.find()) {
            val idStr = matcher.group(1) ?: matcher.group(2)
            idStr?.toLongOrNull()?.let { id ->
                if (validMap.containsKey(id)) {
                    referencedIds.add(id)
                }
            }
        }

        // If the model answer did not use explicit [IMG:id] tokens but is grounded in evidence,
        // match candidate IDs whose displayName or extracted keywords are mentioned in the answer.
        if (referencedIds.isEmpty()) {
            for (evidence in retrievedEvidence) {
                val item = evidence.screenshot
                val nameWithoutExt = item.displayName.substringBeforeLast(".")
                if (nameWithoutExt.length > 3 && modelAnswerText.contains(nameWithoutExt, ignoreCase = true)) {
                    referencedIds.add(item.id)
                }
            }
        }

        // If still empty and evidence was retrieved and directly relevant, associate the top-ranked evidence
        if (referencedIds.isEmpty() && retrievedEvidence.isNotEmpty()) {
            val topEvidence = retrievedEvidence.first()
            if (topEvidence.relevanceScore >= 0.25f) {
                referencedIds.add(topEvidence.screenshot.id)
            }
        }

        return referencedIds.mapNotNull { id ->
            val evidence = validMap[id] ?: return@mapNotNull null
            ValidatedCitation(
                screenshotId = id,
                displayName = evidence.screenshot.displayName,
                contentUri = evidence.screenshot.contentUri,
                snippet = evidence.matchedOcrSnippet.ifBlank {
                    evidence.allOcrText.take(120)
                },
                relevanceReason = if (evidence.visualSignals.isNotEmpty()) {
                    evidence.visualSignals.first()
                } else {
                    "Matched in OCR text"
                }
            )
        }
    }

    /**
     * Replaces raw citation patterns with user-friendly label tokens or cleans them up.
     */
    fun sanitizeAnswerText(rawAnswer: String, validCitations: List<ValidatedCitation>): String {
        val validIds = validCitations.map { it.screenshotId }.toSet()
        val buffer = StringBuffer()
        val matcher = CITATION_TOKEN_PATTERN.matcher(rawAnswer)

        while (matcher.find()) {
            val idStr = matcher.group(1) ?: matcher.group(2)
            val id = idStr?.toLongOrNull()
            if (id != null && validIds.contains(id)) {
                val citation = validCitations.first { it.screenshotId == id }
                matcher.appendReplacement(buffer, "[${citation.displayName}]")
            } else {
                // Remove hallucinated or unverified citation tokens
                matcher.appendReplacement(buffer, "")
            }
        }
        matcher.appendTail(buffer)
        return buffer.toString().trim()
    }
}
