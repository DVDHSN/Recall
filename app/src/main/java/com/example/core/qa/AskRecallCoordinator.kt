package com.example.core.qa

import android.content.Context
import android.util.Log
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.model.ScreenshotItem
import com.example.core.model.SearchResult
import com.example.core.qa.calculator.ReceiptCalculator
import com.example.core.qa.engine.CitationValidator
import com.example.core.qa.engine.DownloadableLiteRtEngine
import com.example.core.qa.engine.LocalGroundedSynthesizer
import com.example.core.qa.engine.OnDeviceLanguageModel
import com.example.core.qa.model.AnswerStatus
import com.example.core.qa.model.AskRecallAnswer
import com.example.core.qa.model.CalculationBreakdown
import com.example.core.qa.model.EvidenceItem
import com.example.core.qa.model.ValidatedCitation
import com.example.core.search.SemanticSearchEngine
import com.example.core.search.query.SearchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class AskRecallCoordinator(
    private val context: Context,
    private val searchEngine: SemanticSearchEngine,
    private val screenshotDao: ScreenshotDao,
    private val languageModel: OnDeviceLanguageModel = DownloadableLiteRtEngine(context, LocalGroundedSynthesizer())
) {

    private val amountSearchPattern = Pattern.compile("""(?i)(?:(RM|MYR|USD|\$|EUR|€|GBP|£|SGD)\s*)?(\d+(?:\.\d+)?)(?:\s*(RM|MYR|USD|\$|EUR|€|GBP|£|SGD))?""")

    suspend fun answerQuestion(
        rawQuestion: String,
        onProgressUpdate: ((String) -> Unit)? = null
    ): AskRecallAnswer = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val question = rawQuestion.trim()

        if (question.isBlank()) {
            return@withContext AskRecallAnswer(
                question = question,
                summaryText = "Please ask a question about your indexed images.",
                citations = emptyList(),
                status = AnswerStatus.INSUFFICIENT_EVIDENCE,
                statusExplanation = "Question was empty.",
                latencyMs = 0L
            )
        }

        onProgressUpdate?.invoke("Searching image library...")

        // Step 1: Retrieval via established hybrid search engine
        val searchResults: List<SearchResult> = try {
            searchEngine.search(
                rawQuery = question,
                overrideMode = SearchMode.HYBRID,
                limit = 10
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search retrieval failed", e)
            emptyList()
        }

        // If specific search returned few results, fallback to all receipts if asking about receipts
        val candidateItems = if (searchResults.isEmpty() && isReceiptQuery(question)) {
            val allIndexed = withContext(Dispatchers.IO) { screenshotDao.getAllSearchable() }
            allIndexed.filter { it.ocrText.isNotBlank() }.map { it.toDomain() }.take(10)
        } else {
            searchResults.map { it.screenshot }
        }

        if (candidateItems.isEmpty()) {
            return@withContext AskRecallAnswer(
                question = question,
                summaryText = "I couldn't find any images in your library related to \"$question\".",
                citations = emptyList(),
                status = AnswerStatus.NO_MATCHING_IMAGES,
                statusExplanation = "No candidate images found matching query.",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        onProgressUpdate?.invoke("Extracting and bounding evidence...")

        // Step 2: Extract & bound evidence
        val evidenceList: List<EvidenceItem> = searchResults.map { result ->
            EvidenceItem(
                screenshot = result.screenshot,
                matchedOcrSnippet = result.snippet,
                allOcrText = result.screenshot.ocrText,
                categories = result.screenshot.categoryLabels.map { it.displayName },
                relevanceScore = result.combinedScore,
                visualSignals = result.retrievalSignals
            )
        }.ifEmpty {
            candidateItems.map { item ->
                EvidenceItem(
                    screenshot = item,
                    matchedOcrSnippet = item.ocrText.lines().firstOrNull { it.isNotBlank() } ?: "",
                    allOcrText = item.ocrText,
                    categories = item.categoryLabels.map { it.displayName },
                    relevanceScore = 0.5f,
                    visualSignals = emptyList()
                )
            }
        }.take(8) // Bounded context size

        onProgressUpdate?.invoke("Analyzing calculations & facts...")

        // Step 3: Deterministic calculation analysis if question is quantitative
        val isCalculation = ReceiptCalculator.isCalculationQuery(question) || isReceiptQuery(question)
        var calculationBreakdown: CalculationBreakdown? = null

        if (isCalculation) {
            val lower = question.lowercase()
            when {
                lower.contains("lowest") || lower.contains("cheapest") || lower.contains("minimum") -> {
                    calculationBreakdown = ReceiptCalculator.findLowestTotal(candidateItems)
                }
                lower.contains("highest") || lower.contains("maximum") || lower.contains("most expensive") -> {
                    calculationBreakdown = ReceiptCalculator.findHighestTotal(candidateItems)
                }
                else -> {
                    // Check if question asks for specific amount (e.g., "shows RM80")
                    val matcher = amountSearchPattern.matcher(question)
                    if (matcher.find()) {
                        val prefix = matcher.group(1)
                        val numStr = matcher.group(2)
                        val suffix = matcher.group(3)
                        val amount = numStr?.toDoubleOrNull()
                        val curr = (prefix ?: suffix ?: "").uppercase()
                        if (amount != null) {
                            calculationBreakdown = ReceiptCalculator.findSpecificAmount(
                                targetAmount = amount,
                                targetCurrency = curr.ifBlank { null },
                                candidates = candidateItems
                            )
                        }
                    }
                }
            }
        }

        onProgressUpdate?.invoke("Synthesizing answer with on-device AI...")

        // Step 4: On-device model inference
        val modelOutput = try {
            languageModel.generateAnswer(question, evidenceList, calculationBreakdown)
        } catch (e: Exception) {
            Log.e(TAG, "Language model inference failed", e)
            LocalGroundedSynthesizer().generateAnswer(question, evidenceList, calculationBreakdown)
        }

        onProgressUpdate?.invoke("Validating citations against source images...")

        // Step 5: Citation validation
        val rawCitations = CitationValidator.validateCitations(modelOutput.rawText, evidenceList)
        val validatedCitations = rawCitations.toMutableList()

        // If calculation found a winner, ensure its citation is explicitly included
        val calcWinner = calculationBreakdown?.winningAmount
        if (calcWinner != null && validatedCitations.none { it.screenshotId == calcWinner.screenshotId }) {
            val winnerItem = candidateItems.firstOrNull { it.id == calcWinner.screenshotId }
            if (winnerItem != null) {
                validatedCitations.add(
                    0,
                    ValidatedCitation(
                        screenshotId = winnerItem.id,
                        displayName = winnerItem.displayName,
                        contentUri = winnerItem.contentUri,
                        snippet = calcWinner.rawTextLine,
                        relevanceReason = "Contains verified amount ${calcWinner.currency} ${calcWinner.amount}"
                    )
                )
            }
        }

        val sanitizedText = CitationValidator.sanitizeAnswerText(modelOutput.rawText, validatedCitations)

        val duration = System.currentTimeMillis() - startTime
        val status = if (!modelOutput.isSufficientEvidence && validatedCitations.isEmpty()) {
            AnswerStatus.INSUFFICIENT_EVIDENCE
        } else {
            AnswerStatus.ANSWERED
        }

        AskRecallAnswer(
            question = question,
            summaryText = sanitizedText,
            citations = validatedCitations,
            calculationBreakdown = calculationBreakdown,
            status = status,
            statusExplanation = if (status == AnswerStatus.INSUFFICIENT_EVIDENCE) {
                "Evidence in indexed images was insufficient or inconclusive."
            } else "",
            modelIdentifier = modelOutput.modelName,
            latencyMs = duration
        )
    }

    private fun isReceiptQuery(query: String): Boolean {
        val lower = query.lowercase()
        return lower.contains("receipt") || lower.contains("rm") || lower.contains("total") ||
                lower.contains("bill") || lower.contains("price") || lower.contains("cost") ||
                lower.contains("$") || lower.contains("invoice")
    }

    companion object {
        private const val TAG = "AskRecallCoordinator"
    }
}
