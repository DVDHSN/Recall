package com.example.core.ml.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.core.ml.tokenizer.WordPieceTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer
import kotlin.math.sqrt

interface TextEmbeddingEngine {
    val modelVersion: String
    val embeddingDimension: Int
    suspend fun generateEmbedding(text: String): FloatArray
    fun close()
}

class OnnxTextEmbeddingEngine private constructor(
    private val context: Context,
    private val tokenizer: WordPieceTokenizer
) : TextEmbeddingEngine {

    override val modelVersion: String = "all-MiniLM-L6-v2-INT8"
    override val embeddingDimension: Int = 384

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = ensureModelCached(context)
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2) // Optimal for mobile CPU (e.g. Exynos 1280 big/LITTLE cores)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        session = env.createSession(modelFile.absolutePath, opts)
        Log.i(TAG, "ONNX Runtime session initialized for $modelVersion with ${session.numInputs} inputs and ${session.numOutputs} outputs")
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val tokenized = tokenizer.tokenize(text)
        val seqLen = tokenized.inputIds.size.toLong()
        val shape = longArrayOf(1, seqLen)

        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenized.inputIds), shape)
        val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenized.attentionMask), shape)
        val tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenized.tokenTypeIds), shape)

        val inputs = HashMap<String, OnnxTensor>()
        // Match model input names
        for (inputName in session.inputNames) {
            when {
                inputName.contains("input_ids") -> inputs[inputName] = inputIdsTensor
                inputName.contains("attention_mask") -> inputs[inputName] = attentionMaskTensor
                inputName.contains("token_type_ids") -> inputs[inputName] = tokenTypeIdsTensor
                else -> {
                    // Fallback by ordering if names differ
                    if (inputs.isEmpty()) inputs[inputName] = inputIdsTensor
                    else if (inputs.size == 1) inputs[inputName] = attentionMaskTensor
                    else inputs[inputName] = tokenTypeIdsTensor
                }
            }
        }

        try {
            session.run(inputs).use { results ->
                val outputValue = results[0].value
                val pooled = when (outputValue) {
                    is Array<*> -> {
                        // Typically Array<Array<FloatArray>> -> [1, seqLen, 384]
                        @Suppress("UNCHECKED_CAST")
                        val tokenEmbeddings = (outputValue as Array<Array<FloatArray>>)[0]
                        meanPool(tokenEmbeddings, tokenized.attentionMask)
                    }
                    else -> {
                        FloatArray(embeddingDimension)
                    }
                }
                normalizeL2(pooled)
            }
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
        }
    }

    private fun meanPool(tokenEmbeddings: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val result = FloatArray(embeddingDimension)
        var validTokenCount = 0f

        for (i in tokenEmbeddings.indices) {
            val mask = if (i < attentionMask.size) attentionMask[i] else 0L
            if (mask == 1L) {
                validTokenCount += 1f
                val tokenVec = tokenEmbeddings[i]
                for (d in 0 until embeddingDimension) {
                    if (d < tokenVec.size) {
                        result[d] += tokenVec[d]
                    }
                }
            }
        }

        if (validTokenCount > 0f) {
            for (d in 0 until embeddingDimension) {
                result[d] /= validTokenCount
            }
        }
        return result
    }

    private fun normalizeL2(vec: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vec) {
            if (!v.isNaN() && !v.isInfinite()) {
                sumSquares += (v * v)
            }
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm > 1e-12f) {
            for (i in vec.indices) {
                val v = vec[i]
                vec[i] = if (v.isNaN() || v.isInfinite()) 0f else v / norm
            }
        } else {
            vec.fill(0f)
        }
        return vec
    }

    override fun close() {
        try {
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ONNX session", e)
        }
    }

    companion object {
        private const val TAG = "OnnxEmbeddingEngine"
        private const val MODEL_FILENAME = "model_quantized.onnx"

        @Volatile
        private var INSTANCE: OnnxTextEmbeddingEngine? = null

        fun getInstance(context: Context): OnnxTextEmbeddingEngine {
            return INSTANCE ?: synchronized(this) {
                val tokenizer = WordPieceTokenizer.getInstance(context)
                val instance = OnnxTextEmbeddingEngine(context.applicationContext, tokenizer)
                INSTANCE = instance
                instance
            }
        }

        private fun ensureModelCached(context: Context): File {
            val cacheFile = File(context.filesDir, MODEL_FILENAME)
            if (!cacheFile.exists() || cacheFile.length() < 1000) {
                Log.d(TAG, "Copying $MODEL_FILENAME from assets to ${cacheFile.absolutePath}")
                context.assets.open(MODEL_FILENAME).use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return cacheFile
        }
    }
}
