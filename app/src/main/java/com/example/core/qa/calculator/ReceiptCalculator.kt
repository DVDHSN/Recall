package com.example.core.qa.calculator

import com.example.core.model.ScreenshotItem
import com.example.core.qa.model.CalculationBreakdown
import com.example.core.qa.model.ExtractedReceiptAmount
import java.util.Locale
import java.util.regex.Pattern

object ReceiptCalculator {

    // Regex for matching currencies and numbers like "RM 80.00", "$14.50", "80.00 MYR", "TOTAL: 120"
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?i)(?:(RM|MYR|USD|\$|EUR|€|GBP|£|SGD)\s*)?(\d{1,6}(?:[.,]\d{2})?)(?:\s*(RM|MYR|USD|\$|EUR|€|GBP|£|SGD))?"""
    )

    private val TOTAL_KEYWORD_PATTERN = Pattern.compile(
        """(?i)\b(total|grand\s*total|amount\s*due|net\s*total|total\s*due|subtotal|balance|paid)\b"""
    )

    /**
     * Extracts candidate monetary amounts from OCR text lines.
     * Prioritizes explicit total lines if available.
     */
    fun extractAmounts(screenshot: ScreenshotItem): List<ExtractedReceiptAmount> {
        val ocr = screenshot.ocrText
        if (ocr.isBlank()) return emptyList()

        val results = mutableListOf<ExtractedReceiptAmount>()
        val lines = ocr.lines()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            val isExplicitTotal = TOTAL_KEYWORD_PATTERN.matcher(trimmedLine).find()
            val matcher = AMOUNT_PATTERN.matcher(trimmedLine)

            while (matcher.find()) {
                val prefixCurr = matcher.group(1)
                val numStr = matcher.group(2)
                val suffixCurr = matcher.group(3)

                if (numStr != null) {
                    val normalizedNum = numStr.replace(',', '.')
                    val parsedVal = normalizedNum.toDoubleOrNull() ?: continue

                    // Filter out years (like 2024, 2025) or unrealistic single-digit standalone integers without currency
                    if (prefixCurr == null && suffixCurr == null && !isExplicitTotal) {
                        if (parsedVal in 1900.0..2099.0 && !normalizedNum.contains('.')) {
                            continue
                        }
                    }

                    val currency = (prefixCurr ?: suffixCurr ?: inferCurrencyFromDoc(ocr)).uppercase()

                    results.add(
                        ExtractedReceiptAmount(
                            screenshotId = screenshot.id,
                            imageDisplayName = screenshot.displayName,
                            currency = currency,
                            amount = parsedVal,
                            rawTextLine = trimmedLine,
                            isExplicitTotal = isExplicitTotal
                        )
                    )
                }
            }
        }

        return results
    }

    private fun inferCurrencyFromDoc(docText: String): String {
        val upper = docText.uppercase()
        return when {
            upper.contains("RM") || upper.contains("MYR") || upper.contains("RINGGIT") -> "RM"
            upper.contains("$") || upper.contains("USD") -> "$"
            upper.contains("EUR") || upper.contains("€") -> "€"
            upper.contains("GBP") || upper.contains("£") -> "£"
            upper.contains("SGD") -> "SGD"
            else -> ""
        }
    }

    /**
     * Finds receipt that matches a specific requested amount (e.g., "Find the receipt that shows RM80").
     */
    fun findSpecificAmount(
        targetAmount: Double,
        targetCurrency: String?,
        candidates: List<ScreenshotItem>
    ): CalculationBreakdown {
        val allExtracted = candidates.flatMap { extractAmounts(it) }
        val matching = allExtracted.filter { candidate ->
            val amountMatches = Math.abs(candidate.amount - targetAmount) < 0.01
            val currencyMatches = if (!targetCurrency.isNullOrBlank() && candidate.currency.isNotBlank()) {
                candidate.currency.equals(targetCurrency, ignoreCase = true)
            } else true
            amountMatches && currencyMatches
        }

        val winner = matching.firstOrNull { it.isExplicitTotal } ?: matching.firstOrNull()

        val steps = mutableListOf<String>()
        steps.add("Searched ${candidates.size} candidate receipts for amount: ${targetCurrency ?: ""} $targetAmount")
        if (winner != null) {
            steps.add("Found exact match in image '${winner.imageDisplayName}' (ID: ${winner.screenshotId}) on line: \"${winner.rawTextLine}\"")
            steps.add("Verified value: ${winner.currency} ${String.format(Locale.US, "%.2f", winner.amount)}")
        } else {
            steps.add("No candidate receipt matched amount ${targetCurrency ?: ""} $targetAmount exactly.")
        }

        return CalculationBreakdown(
            operation = "SPECIFIC_AMOUNT_MATCH",
            candidateAmounts = allExtracted,
            winningAmount = winner,
            explanation = if (winner != null) {
                "Receipt '${winner.imageDisplayName}' explicitly shows ${winner.currency} ${String.format(Locale.US, "%.2f", winner.amount)}."
            } else {
                "No receipt found showing ${targetCurrency ?: ""} $targetAmount."
            },
            calculationSteps = steps
        )
    }

    /**
     * Finds the receipt with the lowest total among candidate receipts.
     */
    fun findLowestTotal(candidates: List<ScreenshotItem>): CalculationBreakdown {
        val candidateBestTotals = mutableListOf<ExtractedReceiptAmount>()

        for (item in candidates) {
            val amounts = extractAmounts(item)
            if (amounts.isEmpty()) continue

            // Prefer lines tagged with "TOTAL" / "GRAND TOTAL", otherwise take max amount as likely receipt total
            val explicitTotals = amounts.filter { it.isExplicitTotal }
            val chosen = if (explicitTotals.isNotEmpty()) {
                explicitTotals.maxByOrNull { it.amount }
            } else {
                amounts.maxByOrNull { it.amount }
            }
            if (chosen != null) {
                candidateBestTotals.add(chosen)
            }
        }

        val steps = mutableListOf<String>()
        steps.add("Inspected ${candidates.size} receipts; extracted representative totals from ${candidateBestTotals.size} receipts.")

        candidateBestTotals.forEach { item ->
            steps.add("• '${item.imageDisplayName}': ${item.currency} ${String.format(Locale.US, "%.2f", item.amount)} (Line: \"${item.rawTextLine}\")")
        }

        val winner = candidateBestTotals.minByOrNull { it.amount }

        if (winner != null) {
            steps.add("Deterministic comparison: ${winner.currency} ${String.format(Locale.US, "%.2f", winner.amount)} is the minimum among all candidate receipts.")
        } else {
            steps.add("Could not extract any valid receipt totals from the provided images.")
        }

        return CalculationBreakdown(
            operation = "LOWEST_TOTAL",
            candidateAmounts = candidateBestTotals,
            winningAmount = winner,
            explanation = if (winner != null) {
                "Receipt '${winner.imageDisplayName}' has the lowest total of ${winner.currency} ${String.format(Locale.US, "%.2f", winner.amount)}."
            } else {
                "No numerical receipt totals could be reliably extracted."
            },
            calculationSteps = steps
        )
    }

    /**
     * Finds the receipt with the highest total among candidate receipts.
     */
    fun findHighestTotal(candidates: List<ScreenshotItem>): CalculationBreakdown {
        val candidateBestTotals = mutableListOf<ExtractedReceiptAmount>()

        for (item in candidates) {
            val amounts = extractAmounts(item)
            if (amounts.isEmpty()) continue

            val explicitTotals = amounts.filter { it.isExplicitTotal }
            val chosen = if (explicitTotals.isNotEmpty()) {
                explicitTotals.maxByOrNull { it.amount }
            } else {
                amounts.maxByOrNull { it.amount }
            }
            if (chosen != null) {
                candidateBestTotals.add(chosen)
            }
        }

        val steps = mutableListOf<String>()
        candidateBestTotals.forEach { item ->
            steps.add("• '${item.imageDisplayName}': ${item.currency} ${String.format(Locale.US, "%.2f", item.amount)}")
        }

        val winner = candidateBestTotals.maxByOrNull { it.amount }
        if (winner != null) {
            steps.add("Deterministic comparison: ${winner.currency} ${String.format(Locale.US, "%.2f", winner.amount)} is the maximum total.")
        }

        return CalculationBreakdown(
            operation = "HIGHEST_TOTAL",
            candidateAmounts = candidateBestTotals,
            winningAmount = winner,
            explanation = if (winner != null) {
                "Receipt '${winner.imageDisplayName}' has the highest total of ${winner.currency} ${String.format(Locale.US, "%.2f", winner.amount)}."
            } else {
                "No numerical receipt totals could be extracted."
            },
            calculationSteps = steps
        )
    }

    /**
     * Determines whether the user query represents an arithmetic or quantitative receipt question.
     */
    fun isCalculationQuery(query: String): Boolean {
        val lower = query.lowercase()
        val containsMoneyWord = lower.contains("receipt") || lower.contains("price") ||
                lower.contains("cost") || lower.contains("total") || lower.contains("rm") ||
                lower.contains("dollar") || lower.contains("$") || lower.contains("eur") ||
                lower.contains("bill") || lower.contains("paid") || lower.contains("invoice")

        val containsComparison = lower.contains("lowest") || lower.contains("cheapest") ||
                lower.contains("minimum") || lower.contains("highest") || lower.contains("most expensive") ||
                lower.contains("maximum") || lower.contains("sum") || lower.contains("shows") ||
                AMOUNT_PATTERN.matcher(query).find()

        return containsMoneyWord && containsComparison
    }
}
