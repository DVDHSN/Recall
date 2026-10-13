package com.example.core.search.fusion

import com.example.core.database.ScreenshotEntity
import com.example.core.model.SearchResult
import com.example.core.search.lexical.LexicalCandidate
import com.example.core.search.query.ParsedQuery
import com.example.core.search.query.SearchMode
import com.example.core.search.semantic.SemanticCandidate
import com.example.core.visual.PerceptualHasher
import kotlin.math.max
import kotlin.math.min

class CandidateFusion {

    fun fuseAndRank(
        query: ParsedQuery,
        semanticCandidates: List<SemanticCandidate>,
        lexicalCandidates: List<LexicalCandidate>,
        allIndexedCorpus: List<ScreenshotEntity>,
        limit: Int = 50
    ): List<SearchResult> {
        val candidateMap = HashMap<Long, ScreenshotEntity>()
        val semanticRankMap = HashMap<Long, Int>()
        val lexicalRankMap = HashMap<Long, Int>()
        val semanticScoreMap = HashMap<Long, Float>()
        val lexicalScoreMap = HashMap<Long, Float>()
        val lexicalMatchesMap = HashMap<Long, List<String>>()
        val hasExactIdentifierMap = HashMap<Long, Boolean>()

        for ((idx, sc) in semanticCandidates.withIndex()) {
            candidateMap[sc.entity.id] = sc.entity
            semanticRankMap[sc.entity.id] = idx + 1
            semanticScoreMap[sc.entity.id] = sc.cosineSimilarity
        }

        for ((idx, lc) in lexicalCandidates.withIndex()) {
            candidateMap[lc.entity.id] = lc.entity
            lexicalRankMap[lc.entity.id] = idx + 1
            lexicalScoreMap[lc.entity.id] = lc.score
            lexicalMatchesMap[lc.entity.id] = lc.matchedTerms
            if (lc.hasExactIdentifierMatch) {
                hasExactIdentifierMap[lc.entity.id] = true
            }
        }

        if (candidateMap.isEmpty()) return emptyList()

        val k = 60.0 // Standard RRF smoothing constant
        val semanticWeight = if (query.searchMode == SearchMode.EXACT_ONLY) 0.1 else 0.65
        val lexicalWeight = if (query.searchMode == SearchMode.SEMANTIC_ONLY) 0.1 else 0.35

        val rawResults = mutableListOf<SearchResult>()

        for ((id, entity) in candidateMap) {
            val sRank = semanticRankMap[id]
            val lRank = lexicalRankMap[id]

            // Reciprocal Rank Fusion
            var rrf = 0.0
            if (sRank != null) {
                rrf += semanticWeight / (k + sRank)
            }
            if (lRank != null) {
                rrf += lexicalWeight / (k + lRank)
            }

            // Scale RRF to un-clamped score
            var combinedScore = (rrf * (k + 1.0) * 1.5).toFloat()

            // Exact identifier boost (e.g. 192.168.1.1, NullPointerException, $599, #4829)
            val hasExactIdentifier = hasExactIdentifierMap[id] == true
            if (hasExactIdentifier) {
                combinedScore += 0.8f
            }

            // Topic intent bonus
            if (query.topicConstraint != null) {
                if (entity.categoryLabels.contains(query.topicConstraint.name)) {
                    combinedScore = min(1.0f, combinedScore + 0.15f)
                }
            }

            // Date constraint check
            val dateConstraint = query.dateConstraint
            val matchesDate = if (dateConstraint != null) {
                dateConstraint.matches(entity.dateModified)
            } else true

            if (!matchesDate) {
                // If query specified an explicit date constraint, demote non-matching
                combinedScore *= 0.3f
            }

            // Negative term penalty
            for (neg in query.negativeTerms) {
                if (entity.ocrText.contains(neg, ignoreCase = true) || entity.displayName.contains(neg, ignoreCase = true)) {
                    combinedScore *= 0.25f // Strong penalty for negative term
                }
            }

            val semanticScore = semanticScoreMap[id] ?: 0f
            val lexicalScore = lexicalScoreMap[id] ?: 0f
            val matchedTerms = lexicalMatchesMap[id] ?: emptyList()

            // Build explanation
            val signals = mutableListOf<String>()
            if (hasExactIdentifier) signals.add("Exact identifier match")
            if (sRank != null && sRank <= 3) signals.add("High semantic relevance")
            if (lRank != null && lRank <= 3) signals.add("Direct keyword match")
            if (dateConstraint != null && matchesDate) signals.add("Matches date (${dateConstraint.label})")
            if (query.topicConstraint != null && entity.categoryLabels.contains(query.topicConstraint.name)) {
                signals.add("Topic match (${query.topicConstraint.displayName})")
            }

            val explanation = buildExplanation(
                hasExactIdentifier = hasExactIdentifier,
                semanticScore = semanticScore,
                lexicalScore = lexicalScore,
                matchedTerms = matchedTerms,
                signals = signals
            )

            val snippet = extractSnippet(entity.ocrText, matchedTerms)

            rawResults.add(
                SearchResult(
                    screenshot = entity.toDomain(),
                    semanticScore = semanticScore,
                    lexicalScore = lexicalScore,
                    visualScore = 0f,
                    combinedScore = combinedScore,
                    snippet = snippet,
                    matchedTerms = matchedTerms,
                    explanation = explanation,
                    retrievalSignals = signals
                )
            )
        }

        // Sort descending by combined score, prioritizing exact identifier matches
        rawResults.sortWith(
            compareByDescending<SearchResult> {
                if (hasExactIdentifierMap[it.screenshot.id] == true) 1 else 0
            }.thenByDescending { it.combinedScore }
        )

        // Normalize combinedScore to [0.01f, 1.0f]
        val maxScore = rawResults.maxOfOrNull { it.combinedScore } ?: 1.0f
        val normalizedResults = if (maxScore > 1.0f) {
            rawResults.map { it.copy(combinedScore = (it.combinedScore / maxScore).coerceIn(0.01f, 1.0f)) }
        } else {
            rawResults.map { it.copy(combinedScore = it.combinedScore.coerceIn(0.01f, 1.0f)) }
        }

        // Apply near-duplicate clustering & diversity (MMR)
        return applyDiversityAndDuplicateClustering(normalizedResults, limit)
    }

    private fun applyDiversityAndDuplicateClustering(
        results: List<SearchResult>,
        limit: Int
    ): List<SearchResult> {
        val finalResults = mutableListOf<SearchResult>()
        val seenHashes = mutableListOf<Long>()

        for (result in results) {
            val pHash = result.screenshot.perceptualHash
            if (pHash != 0L) {
                // Check if any existing selected result is a near-duplicate
                val nearDupIdx = seenHashes.indexOfFirst {
                    PerceptualHasher.isNearDuplicate(it, pHash, threshold = 8)
                }

                if (nearDupIdx >= 0) {
                    // Update previous item's nearDuplicateCount
                    val prev = finalResults[nearDupIdx]
                    finalResults[nearDupIdx] = prev.copy(
                        hasNearDuplicates = true,
                        nearDuplicateCount = prev.nearDuplicateCount + 1
                    )
                    // If near-duplicate is substantially lower or equal, diversify
                    if (finalResults.size >= limit / 2) {
                        continue // Skip crowding the top list
                    }
                } else {
                    seenHashes.add(pHash)
                }
            }

            finalResults.add(result)
            if (finalResults.size >= limit) break
        }

        return finalResults
    }

    private fun buildExplanation(
        hasExactIdentifier: Boolean,
        semanticScore: Float,
        lexicalScore: Float,
        matchedTerms: List<String>,
        signals: List<String>
    ): String {
        return when {
            hasExactIdentifier -> "Exact technical identifier match"
            semanticScore > 0.7f && lexicalScore > 0.4f -> "Matched both meaning & exact keywords (${matchedTerms.take(2).joinToString()})"
            semanticScore > 0.6f -> "Semantically related concept (${(semanticScore * 100).toInt()}% meaning similarity)"
            lexicalScore > 0.5f -> "Direct phrase match on '${matchedTerms.take(2).joinToString()}'"
            signals.isNotEmpty() -> signals.joinToString(" • ")
            else -> "Matched query relevance"
        }
    }

    private fun extractSnippet(ocrText: String, terms: List<String>): String {
        if (ocrText.isBlank()) return "No text extracted from screenshot"
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ocrText.take(150)

        var bestLineIndex = 0
        var bestMatchCount = -1

        for ((idx, line) in lines.withIndex()) {
            val lower = line.lowercase()
            var matches = 0
            for (t in terms) {
                if (lower.contains(t.lowercase())) matches++
            }
            if (matches > bestMatchCount) {
                bestMatchCount = matches
                bestLineIndex = idx
            }
        }

        val start = max(0, bestLineIndex - 1)
        val end = min(lines.size - 1, bestLineIndex + 1)
        return lines.subList(start, end + 1).joinToString(" • ").take(220)
    }
}
