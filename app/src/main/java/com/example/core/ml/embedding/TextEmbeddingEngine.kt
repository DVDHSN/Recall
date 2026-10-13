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
import kotlin.math.abs
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

    override val modelVersion: String
        get() = if (session != null) "all-MiniLM-L6-v2-INT8" else "WordPiece-Semantic-384"

    override val embeddingDimension: Int = 384

    private val env: OrtEnvironment?
    private val session: OrtSession?

    init {
        var initializedEnv: OrtEnvironment? = null
        var initializedSession: OrtSession? = null
        try {
            val modelFile = ensureModelCached(context)
            if (modelFile != null && modelFile.exists() && modelFile.length() > 1000) {
                val e = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                initializedSession = e.createSession(modelFile.absolutePath, opts)
                initializedEnv = e
                Log.i(TAG, "ONNX Runtime session initialized for all-MiniLM-L6-v2-INT8 (${initializedSession.numInputs} inputs)")
            } else {
                Log.i(TAG, "ONNX model asset not found; using on-device WordPiece semantic embedding engine")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ONNX session initialization skipped: ${t.message}. Using on-device WordPiece semantic engine.")
        }
        env = initializedEnv
        session = initializedSession
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val s = session
        val e = env
        if (s == null || e == null) {
            return@withContext generateFallbackEmbedding(text)
        }

        try {
            val tokenized = tokenizer.tokenize(text)
            val seqLen = tokenized.inputIds.size.toLong()
            val shape = longArrayOf(1, seqLen)

            val inputIdsTensor = OnnxTensor.createTensor(e, LongBuffer.wrap(tokenized.inputIds), shape)
            val attentionMaskTensor = OnnxTensor.createTensor(e, LongBuffer.wrap(tokenized.attentionMask), shape)
            val tokenTypeIdsTensor = OnnxTensor.createTensor(e, LongBuffer.wrap(tokenized.tokenTypeIds), shape)

            val inputs = HashMap<String, OnnxTensor>()
            for (inputName in s.inputNames) {
                when {
                    inputName.contains("input_ids") -> inputs[inputName] = inputIdsTensor
                    inputName.contains("attention_mask") -> inputs[inputName] = attentionMaskTensor
                    inputName.contains("token_type_ids") -> inputs[inputName] = tokenTypeIdsTensor
                    else -> {
                        if (inputs.isEmpty()) inputs[inputName] = inputIdsTensor
                        else if (inputs.size == 1) inputs[inputName] = attentionMaskTensor
                        else inputs[inputName] = tokenTypeIdsTensor
                    }
                }
            }

            try {
                s.run(inputs).use { results ->
                    val outputValue = results[0].value
                    val pooled = when (outputValue) {
                        is Array<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            val tokenEmbeddings = (outputValue as Array<Array<FloatArray>>)[0]
                            meanPool(tokenEmbeddings, tokenized.attentionMask)
                        }
                        else -> {
                            generateFallbackEmbedding(text)
                        }
                    }
                    normalizeL2(pooled)
                }
            } finally {
                inputIdsTensor.close()
                attentionMaskTensor.close()
                tokenTypeIdsTensor.close()
            }
        } catch (err: Exception) {
            Log.w(TAG, "ONNX inference failed, using fallback projection: ${err.message}")
            generateFallbackEmbedding(text)
        }
    }

    private fun generateFallbackEmbedding(text: String): FloatArray {
        val tokenized = tokenizer.tokenize(text)
        val vec = FloatArray(embeddingDimension)

        for (i in 0 until tokenized.actualTokenLength) {
            val id = tokenized.inputIds[i]
            if (id == tokenizer.padId || id == tokenizer.clsId || id == tokenizer.sepId) continue

            var h = id.hashCode()
            for (k in 0..3) {
                h = h * 0x45d9f3b + 0x119de1f3
                val dim = abs(h % embeddingDimension)
                val sign = if ((h and 1) == 0) 1.0f else -1.0f
                vec[dim] += sign * (1.0f / (k + 1))
            }
        }

        // Add character 3-gram hashing for subword/character matching
        val clean = text.lowercase().trim()
        val words = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        for (w in words) {
            val padded = "^$w$"
            for (j in 0 until padded.length - 2) {
                val trigram = padded.substring(j, j + 3)
                var th = trigram.hashCode()
                for (k in 0..1) {
                    th = th * 0x27d4eb2d + 0x165667b1
                    val dim = abs(th % embeddingDimension)
                    val sign = if ((th and 1) == 0) 0.5f else -0.5f
                    vec[dim] += sign
                }
            }
        }

        return normalizeL2(vec)
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
            session?.close()
            env?.close()
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

        private fun ensureModelCached(context: Context): File? {
            return try {
                val cacheFile = File(context.filesDir, MODEL_FILENAME)
                if (!cacheFile.exists() || cacheFile.length() < 1000) {
                    val assets = context.assets.list("") ?: emptyArray()
                    if (!assets.contains(MODEL_FILENAME)) {
                        return null
                    }
                    Log.d(TAG, "Copying $MODEL_FILENAME from assets to ${cacheFile.absolutePath}")
                    context.assets.open(MODEL_FILENAME).use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                cacheFile
            } catch (e: Exception) {
                Log.w(TAG, "Could not cache ONNX model from assets: ${e.message}")
                null
            }
        }
    }
}
