package com.example.core.qa.model

import com.example.core.model.ScreenshotItem

enum class QuestionIntent {
    QUANTITATIVE_RECEIPT,
    FACTUAL_OCR,
    VISUAL_SCENE,
    CATEGORY_TOPIC,
    GENERAL
}

data class EvidenceItem(
    val screenshot: ScreenshotItem,
    val matchedOcrSnippet: String,
    val allOcrText: String,
    val categories: List<String>,
    val relevanceScore: Float,
    val visualSignals: List<String>
)

data class ValidatedCitation(
    val screenshotId: Long,
    val displayName: String,
    val contentUri: String,
    val snippet: String,
    val relevanceReason: String
)

data class ExtractedReceiptAmount(
    val screenshotId: Long,
    val imageDisplayName: String,
    val currency: String,
    val amount: Double,
    val rawTextLine: String,
    val isExplicitTotal: Boolean
)

data class CalculationBreakdown(
    val operation: String, // e.g. "LOWEST_TOTAL", "SPECIFIC_AMOUNT_MATCH", "SUM"
    val candidateAmounts: List<ExtractedReceiptAmount>,
    val winningAmount: ExtractedReceiptAmount? = null,
    val explanation: String,
    val calculationSteps: List<String>
)

enum class AnswerStatus {
    ANSWERED,
    INSUFFICIENT_EVIDENCE,
    NO_MATCHING_IMAGES,
    ERROR
}

data class AskRecallAnswer(
    val question: String,
    val summaryText: String,
    val citations: List<ValidatedCitation>,
    val calculationBreakdown: CalculationBreakdown? = null,
    val status: AnswerStatus = AnswerStatus.ANSWERED,
    val statusExplanation: String = "",
    val modelIdentifier: String = "Local Grounded Reasoner",
    val latencyMs: Long = 0L
)

sealed interface AskRecallUiState {
    object Idle : AskRecallUiState
    data class Loading(val currentStep: String) : AskRecallUiState
    data class Success(val answer: AskRecallAnswer) : AskRecallUiState
    data class InsufficientEvidence(
        val question: String,
        val reason: String,
        val suggestedQuestions: List<String> = emptyList()
    ) : AskRecallUiState
    data class Error(val message: String) : AskRecallUiState
}
