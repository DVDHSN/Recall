package com.example.core.search.query

import com.example.core.model.ScreenshotCategory
import java.util.Calendar
import java.util.Locale

class QueryParser(
    private val clockNowMsProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val ipRegex = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
    private val exceptionRegex = Regex("""\b[A-Za-z0-9_]*Exception\b""")
    private val httpErrorRegex = Regex("""\b(?:HTTP\s+)?(4\d{2}|5\d{2})\b""", RegexOption.IGNORE_CASE)
    private val currencyRegex = Regex("""\$\d+(?:,\d{3})*(?:\.\d{2})?""")
    private val codeOrHashRegex = Regex("""#[A-Za-z0-9_-]+\b""")
    private val snakeOrCompoundRegex = Regex("""\b[a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)+\b""")
    private val fileOrDomainRegex = Regex("""\b[A-Za-z0-9_.-]+\.(?:py|kt|java|js|ts|json|xml|html|com|org|net|io|edu|gov|png|jpg)\b""", RegexOption.IGNORE_CASE)
    private val specialTokenRegex = Regex("""\b[A-Za-z0-9]+#[A-Za-z0-9]+\b""")
    private val quotedRegex = Regex(""""([^"]+)"""")
    private val negativeRegex = Regex("""\b(?:not|without|exclude|except)\s+([a-zA-Z0-9_-]+)\b""", RegexOption.IGNORE_CASE)

    fun parse(rawQuery: String, overrideSearchMode: SearchMode? = null): ParsedQuery {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return ParsedQuery(rawQuery = "", cleanSearchText = "")
        }

        val inferredFilters = mutableListOf<InferredFilter>()
        var workingText = trimmed

        // 1. Extract exact quoted phrases
        val exactPhrases = mutableListOf<String>()
        quotedRegex.findAll(workingText).forEach { match ->
            val phrase = match.groupValues[1].trim()
            if (phrase.isNotEmpty()) {
                exactPhrases.add(phrase)
            }
        }
        workingText = workingText.replace(quotedRegex, " ")

        // 2. Extract technical identifiers
        val technicalIdentifiers = mutableListOf<String>()
        ipRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        exceptionRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        httpErrorRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        currencyRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        codeOrHashRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        snakeOrCompoundRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        fileOrDomainRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }
        specialTokenRegex.findAll(trimmed).forEach { technicalIdentifiers.add(it.value) }

        // 3. Extract negative exclusions (e.g. "not electrical")
        val negativeTerms = mutableListOf<String>()
        negativeRegex.findAll(workingText).forEach { match ->
            val term = match.groupValues[1].trim().lowercase()
            if (term.isNotEmpty()) {
                negativeTerms.add(term)
                inferredFilters.add(
                    InferredFilter(
                        id = "neg_$term",
                        label = "Exclude: $term",
                        description = "Negative filter excluding terms matching '$term'"
                    )
                )
            }
        }
        workingText = workingText.replace(negativeRegex, " ")

        // 4. Extract date constraints
        val (dateConstraint, textAfterDate) = extractDateConstraint(workingText)
        if (dateConstraint != null) {
            inferredFilters.add(
                InferredFilter(
                    id = "date_${dateConstraint.label}",
                    label = "Date: ${dateConstraint.label}",
                    description = "Filtering screenshots within ${dateConstraint.label}"
                )
            )
            workingText = textAfterDate
        }

        // 5. Extract topic intent
        val (topicConstraint, textAfterTopic) = extractTopicIntent(workingText)
        if (topicConstraint != null) {
            inferredFilters.add(
                InferredFilter(
                    id = "topic_${topicConstraint.name}",
                    label = "Topic: ${topicConstraint.displayName}",
                    description = "Focusing on ${topicConstraint.displayName}"
                )
            )
            workingText = textAfterTopic
        }

        // 6. Clean search text for embedding generation
        val cleanSearchText = workingText.replace(Regex("""\s+"""), " ").trim()
        val finalCleanText = cleanSearchText.ifEmpty { trimmed }

        val searchMode = overrideSearchMode ?: when {
            exactPhrases.isNotEmpty() && cleanSearchText.isEmpty() -> SearchMode.EXACT_ONLY
            else -> SearchMode.HYBRID
        }

        val queryIntent = classifyQueryIntent(
            rawQuery = trimmed,
            cleanSearchText = finalCleanText,
            technicalIdentifiers = technicalIdentifiers,
            exactPhrases = exactPhrases
        )

        return ParsedQuery(
            rawQuery = trimmed,
            cleanSearchText = finalCleanText,
            dateConstraint = dateConstraint,
            topicConstraint = topicConstraint,
            negativeTerms = negativeTerms,
            exactPhrases = exactPhrases,
            technicalIdentifiers = technicalIdentifiers.distinct(),
            searchMode = searchMode,
            queryIntent = queryIntent,
            inferredFilters = inferredFilters
        )
    }

    private fun classifyQueryIntent(
        rawQuery: String,
        cleanSearchText: String,
        technicalIdentifiers: List<String>,
        exactPhrases: List<String>
    ): QueryIntent {
        if (technicalIdentifiers.isNotEmpty() || exactPhrases.isNotEmpty()) {
            return QueryIntent.TEXTUAL_DOMINANT
        }

        val lower = cleanSearchText.lowercase(Locale.ROOT)
        val textKeywords = listOf(
            "exception", "error", "stacktrace", "traceback", "nullpointer", "password",
            "token", "http", "api", "json", "code", "database", "query", "sql", "import",
            "class", "port", "log", "debug", "failed", "warning"
        )
        if (textKeywords.any { lower.contains(it) }) {
            return QueryIntent.TEXTUAL_DOMINANT
        }

        val visualKeywords = listOf(
            "red", "green", "blue", "yellow", "golden", "gold", "orange", "purple", "white", "black", "pink",
            "photo", "picture", "image", "sunset", "sunrise", "ocean", "sea", "mountain", "car", "ferrari",
            "cat", "dog", "sofa", "couch", "landscape", "diagram", "box", "package", "sky", "forest", "beach",
            "night", "sleeping", "above", "label", "nature", "scenery", "drawing", "illustration",
            "water", "tree", "flowers", "view", "aerial"
        )
        val matchesVisual = visualKeywords.any { lower.contains(it) }

        return if (matchesVisual) {
            QueryIntent.VISUAL_DOMINANT
        } else {
            QueryIntent.BALANCED_HYBRID
        }
    }

    private fun extractDateConstraint(text: String): Pair<DateRangeConstraint?, String> {
        val lower = text.lowercase(Locale.ROOT)
        val nowMs = clockNowMsProvider()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }

        // Start of today
        val startOfToday = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000

        val endOfToday = startOfToday + 86400

        return when {
            lower.contains("today") -> {
                val constraint = DateRangeConstraint("Today", startOfToday, endOfToday)
                val newText = text.replace(Regex("""\btoday\b""", RegexOption.IGNORE_CASE), " ")
                Pair(constraint, newText)
            }
            lower.contains("yesterday") -> {
                val startOfYesterday = startOfToday - 86400
                val constraint = DateRangeConstraint("Yesterday", startOfYesterday, startOfToday)
                val newText = text.replace(Regex("""\byesterday\b""", RegexOption.IGNORE_CASE), " ")
                Pair(constraint, newText)
            }
            lower.contains("this week") -> {
                val startOfWeek = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis / 1000
                val constraint = DateRangeConstraint("This Week", startOfWeek, endOfToday)
                val newText = text.replace(Regex("""\bthis\s+week\b""", RegexOption.IGNORE_CASE), " ")
                Pair(constraint, newText)
            }
            lower.contains("last week") || lower.contains("past week") -> {
                val startOfPast7Days = (nowMs / 1000) - (7 * 86400)
                val constraint = DateRangeConstraint("Past 7 Days", startOfPast7Days, endOfToday)
                val newText = text.replace(Regex("""\b(?:last|past)\s+week\b""", RegexOption.IGNORE_CASE), " ")
                Pair(constraint, newText)
            }
            lower.contains("this month") -> {
                val startOfMonth = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis / 1000
                val constraint = DateRangeConstraint("This Month", startOfMonth, endOfToday)
                val newText = text.replace(Regex("""\bthis\s+month\b""", RegexOption.IGNORE_CASE), " ")
                Pair(constraint, newText)
            }
            lower.contains("last month") -> {
                val startOfPast30Days = (nowMs / 1000) - (30 * 86400)
                val constraint = DateRangeConstraint("Past Month", startOfPast30Days, endOfToday)
                val newText = text.replace(Regex("""\blast\s+month\b""", RegexOption.IGNORE_CASE), " ")
                Pair(constraint, newText)
            }
            else -> Pair(null, text)
        }
    }

    private fun extractTopicIntent(text: String): Pair<ScreenshotCategory?, String> {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("recipe") || lower.contains("ingredients") || lower.contains("dish") -> {
                Pair(ScreenshotCategory.RECIPES, text)
            }
            lower.contains("error") || lower.contains("stacktrace") || lower.contains("traceback") || lower.contains("exception") -> {
                Pair(ScreenshotCategory.CODE_ERROR, text)
            }
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("password") || lower.contains("ssid") -> {
                Pair(ScreenshotCategory.NETWORK_TECH, text)
            }
            lower.contains("notes") || lower.contains("lecture") || lower.contains("revision") -> {
                Pair(ScreenshotCategory.STUDY_NOTES, text)
            }
            lower.contains("lease") || lower.contains("rent") || lower.contains("invoice") || lower.contains("receipt") -> {
                Pair(ScreenshotCategory.FINANCE_RECEIPTS, text)
            }
            lower.contains("gpu") || lower.contains("benchmark") || lower.contains("graphics card") -> {
                Pair(ScreenshotCategory.SHOPPING_PRICES, text)
            }
            else -> Pair(null, text)
        }
    }
}
