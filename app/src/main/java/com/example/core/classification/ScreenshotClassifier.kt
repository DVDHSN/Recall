package com.example.core.classification

import com.example.core.model.ScreenshotCategory
import kotlin.math.min

object ScreenshotClassifier {

    data class ClassificationResult(
        val categories: List<ScreenshotCategory>,
        val primaryCategory: ScreenshotCategory,
        val confidence: Float
    )

    private val codeKeywords = listOf(
        "traceback", "exception", "error", "nullpointer", "stacktrace",
        "file \"", "line ", "def ", "class ", "import ", "const ",
        "syntaxerror", "attributeerror", "val ", "var ", "function", "return", "catch", "throw"
    )

    private val recipeKeywords = listOf(
        "ingredient", "recipe", "tablespoon", "tbsp", "teaspoon", "tsp",
        "cup", "cups", "simmer", "sauté", "bake", "oven", "chickpea", "garbanzo",
        "coconut milk", "onion", "garlic", "spinach", "salt", "pepper", "curry", "servings", "prep time"
    )

    private val networkKeywords = listOf(
        "ssid", "wifi", "wi-fi", "password", "wpa", "wpa2", "wpa3",
        "gateway", "subnet", "dhcp", "dns", "ip address", "192.168.",
        "router", "ethernet", "access point", "bandwidth", "signal strength"
    )

    private val studyKeywords = listOf(
        "lecture", "notes", "concept", "chapter", "exam", "quiz",
        "phototropism", "biology", "physiology", "auxin", "cell", "gravity",
        "definition", "hypothesis", "experiment", "summary", "key terms", "stimulus"
    )

    private val financeKeywords = listOf(
        "rent", "lease", "deposit", "due date", "payment", "invoice",
        "monthly", "tenant", "receipt", "total:", "subtotal", "tax", "balance due",
        "escrow", "account number", "billing", "usd"
    )

    private val shoppingKeywords = listOf(
        "benchmark", "msrp", "fps", "rtx", "radeon", "geforce", "gpu",
        "add to cart", "checkout", "shipping", "in stock", "rating", "reviews",
        "gddr6", "displayport", "hdmi", "warranty"
    )

    private val commsKeywords = listOf(
        "message", "messages", "chat", "unread", "sent", "delivered",
        "reply", "inbox", "conversation", "today at", "yesterday at"
    )

    fun classify(text: String, displayName: String = ""): ClassificationResult {
        val lower = (text + " " + displayName).lowercase()
        val scores = mutableMapOf<ScreenshotCategory, Float>()

        scores[ScreenshotCategory.CODE_ERROR] = calculateCategoryScore(lower, codeKeywords)
        scores[ScreenshotCategory.RECIPES] = calculateCategoryScore(lower, recipeKeywords)
        scores[ScreenshotCategory.NETWORK_TECH] = calculateCategoryScore(lower, networkKeywords)
        scores[ScreenshotCategory.STUDY_NOTES] = calculateCategoryScore(lower, studyKeywords)
        scores[ScreenshotCategory.FINANCE_RECEIPTS] = calculateCategoryScore(lower, financeKeywords)
        scores[ScreenshotCategory.SHOPPING_PRICES] = calculateCategoryScore(lower, shoppingKeywords)
        scores[ScreenshotCategory.COMMUNICATION] = calculateCategoryScore(lower, commsKeywords)

        // Boost for specific patterns
        if (lower.contains("192.168.") || lower.contains("ssid:")) {
            scores[ScreenshotCategory.NETWORK_TECH] = (scores[ScreenshotCategory.NETWORK_TECH] ?: 0f) + 0.4f
        }
        if (lower.contains("nullpointer") || lower.contains("traceback")) {
            scores[ScreenshotCategory.CODE_ERROR] = (scores[ScreenshotCategory.CODE_ERROR] ?: 0f) + 0.4f
        }
        if (lower.contains("garbanzo") || lower.contains("ingredients:")) {
            scores[ScreenshotCategory.RECIPES] = (scores[ScreenshotCategory.RECIPES] ?: 0f) + 0.4f
        }
        if (lower.contains("phototropism") || lower.contains("auxin")) {
            scores[ScreenshotCategory.STUDY_NOTES] = (scores[ScreenshotCategory.STUDY_NOTES] ?: 0f) + 0.4f
        }

        val sorted = scores.entries
            .filter { it.value > 0.15f }
            .sortedByDescending { it.value }

        if (sorted.isEmpty()) {
            return ClassificationResult(
                categories = listOf(ScreenshotCategory.GENERAL_REFERENCE),
                primaryCategory = ScreenshotCategory.GENERAL_REFERENCE,
                confidence = 0.2f
            )
        }

        val primary = sorted.first()
        val multiCategories = sorted.take(2).map { it.key }
        val confidence = min(1.0f, primary.value / 1.5f)

        return ClassificationResult(
            categories = multiCategories,
            primaryCategory = primary.key,
            confidence = confidence
        )
    }

    private fun calculateCategoryScore(text: String, keywords: List<String>): Float {
        var matchCount = 0
        for (kw in keywords) {
            if (text.contains(kw)) {
                matchCount++
            }
        }
        return if (keywords.isNotEmpty()) (matchCount.toFloat() / 3f).coerceAtMost(1.5f) else 0f
    }
}
