package com.example.core.search.lexical

import com.example.core.database.ScreenshotEntity
import com.example.core.search.query.ParsedQuery
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class LexicalCandidate(
    val entity: ScreenshotEntity,
    val score: Float,
    val matchedTerms: List<String>,
    val hasExactPhraseMatch: Boolean,
    val hasExactIdentifierMatch: Boolean
)

class LexicalRetriever {

    fun retrieve(
        query: ParsedQuery,
        corpus: List<ScreenshotEntity>,
        limit: Int = 100
    ): List<LexicalCandidate> {
        if (corpus.isEmpty()) return emptyList()

        // Tokenize query terms
        val rawTokens = query.cleanSearchText.lowercase()
            .split(Regex("""[^a-zA-Z0-9_.-]+"""))
            .filter { it.length > 1 }

        val exactPhrases = query.exactPhrases.map { it.lowercase() }
        val identifiers = query.technicalIdentifiers.map { it.lowercase() }

        val totalDocs = corpus.size.toDouble()
        // Compute Document Frequency (DF) for each term
        val termDocFreq = HashMap<String, Int>()
        for (token in rawTokens) {
            var count = 0
            for (doc in corpus) {
                if (doc.ocrText.contains(token, ignoreCase = true) || doc.displayName.contains(token, ignoreCase = true)) {
                    count++
                }
            }
            termDocFreq[token] = count
        }

        val candidates = mutableListOf<LexicalCandidate>()

        for (doc in corpus) {
            val docText = (doc.ocrText + " " + doc.displayName).lowercase()
            val matchedTerms = mutableListOf<String>()

            // 1. BM25 / TF-IDF term scoring
            var tfIdfSum = 0.0
            var hasExactPhrase = false
            for (token in rawTokens) {
                val df = termDocFreq[token] ?: 0
                if (df > 0 && docText.contains(token)) {
                    matchedTerms.add(token)
                    // Inverse document frequency: ln((N - n + 0.5) / (n + 0.5) + 1)
                    val idf = ln((totalDocs - df + 0.5) / (df + 0.5) + 1.0)
                    // Term frequency count in doc
                    val tf = countOccurrences(docText, token)
                    val tfWeight = tf.toDouble() / (tf.toDouble() + 1.5)
                    tfIdfSum += idf * tfWeight
                }
            }

            // 2. Exact phrase bonus
            val cleanLower = query.cleanSearchText.lowercase().trim()
            if (cleanLower.length > 4 && docText.contains(cleanLower)) {
                hasExactPhrase = true
                matchedTerms.add(cleanLower)
                tfIdfSum += 5.0
            }

            for (phrase in exactPhrases) {
                if (phrase.isNotEmpty() && docText.contains(phrase)) {
                    hasExactPhrase = true
                    matchedTerms.add(phrase)
                    tfIdfSum += 4.0
                }
            }

            // Check multi-word phrase matching (2-grams)
            val queryWords = query.cleanSearchText.lowercase().split(Regex("""\s+""")).filter { it.length > 2 }
            if (queryWords.size >= 2) {
                for (i in 0..queryWords.size - 2) {
                    val twoGram = "${queryWords[i]} ${queryWords[i + 1]}"
                    if (docText.contains(twoGram)) {
                        hasExactPhrase = true
                        matchedTerms.add(twoGram)
                        tfIdfSum += 3.5
                    }
                }
            }

            // 3. Technical identifier exact match bonus
            var hasIdentifierMatch = false
            for (id in identifiers) {
                if (id.isNotEmpty() && docText.contains(id)) {
                    hasIdentifierMatch = true
                    matchedTerms.add(id)
                    tfIdfSum += 4.0
                }
            }

            if (matchedTerms.isNotEmpty()) {
                val normalizedScore = min(1.0f, (tfIdfSum / 6.0).toFloat())
                candidates.add(
                    LexicalCandidate(
                        entity = doc,
                        score = max(0.01f, normalizedScore),
                        matchedTerms = matchedTerms.distinct(),
                        hasExactPhraseMatch = hasExactPhrase,
                        hasExactIdentifierMatch = hasIdentifierMatch
                    )
                )
            }
        }

        candidates.sortByDescending { it.score }
        return candidates.take(limit)
    }

    private fun countOccurrences(text: String, token: String): Int {
        var count = 0
        var idx = 0
        while (idx < text.length) {
            val found = text.indexOf(token, idx)
            if (found >= 0) {
                count++
                idx = found + token.length
            } else {
                break
            }
        }
        return count
    }
}
