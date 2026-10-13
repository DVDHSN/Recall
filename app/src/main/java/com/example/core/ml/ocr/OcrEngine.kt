package com.example.core.ml.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface OcrEngine {
    suspend fun recognizeText(bitmap: Bitmap): OcrResult
    fun close()
}

data class OcrResult(
    val fullText: String,
    val lineCount: Int,
    val blockCount: Int,
    val isSuccess: Boolean,
    val error: String? = null
)

class MlKitOcrEngine private constructor() : OcrEngine {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    val lines = mutableListOf<String>()
                    var blockCount = 0

                    for (block in visionText.textBlocks) {
                        blockCount++
                        for (line in block.lines) {
                            val trimmedLine = line.text.trim()
                            if (trimmedLine.isNotEmpty()) {
                                lines.add(trimmedLine)
                            }
                        }
                    }

                    // Preserve original technical identifiers and whitespace conservatively
                    val cleanedText = lines.joinToString(separator = "\n")

                    cont.resume(
                        OcrResult(
                            fullText = cleanedText,
                            lineCount = lines.size,
                            blockCount = blockCount,
                            isSuccess = true
                        )
                    )
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "OCR recognition failed", e)
                    cont.resume(
                        OcrResult(
                            fullText = "",
                            lineCount = 0,
                            blockCount = 0,
                            isSuccess = false,
                            error = e.localizedMessage ?: "OCR processing error"
                        )
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initiating OCR", e)
            cont.resume(
                OcrResult(
                    fullText = "",
                    lineCount = 0,
                    blockCount = 0,
                    isSuccess = false,
                    error = e.localizedMessage
                )
            )
        }
    }

    override fun close() {
        recognizer.close()
    }

    companion object {
        private const val TAG = "MlKitOcrEngine"

        @Volatile
        private var INSTANCE: MlKitOcrEngine? = null

        fun getInstance(): MlKitOcrEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = MlKitOcrEngine()
                INSTANCE = instance
                instance
            }
        }
    }
}
