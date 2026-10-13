package com.example.core.ml.tokenizer

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer

class WordPieceTokenizer private constructor(
    private val vocab: Map<String, Long>,
    private val unkToken: String = "[UNK]",
    private val clsToken: String = "[CLS]",
    private val sepToken: String = "[SEP]",
    private val padToken: String = "[PAD]",
    val maxSequenceLength: Int = 128
) {
    val unkId: Long = vocab[unkToken] ?: 100L
    val clsId: Long = vocab[clsToken] ?: 101L
    val sepId: Long = vocab[sepToken] ?: 102L
    val padId: Long = vocab[padToken] ?: 0L

    data class TokenizedInput(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
        val actualTokenLength: Int
    )

    fun tokenize(text: String, maxLen: Int = maxSequenceLength): TokenizedInput {
        val cleanText = normalize(text)
        val basicTokens = basicTokenize(cleanText)
        val wordPieceTokens = mutableListOf<Long>()

        for (token in basicTokens) {
            val subTokens = wordPieceTokenize(token)
            for (subId in subTokens) {
                if (wordPieceTokens.size < maxLen - 2) { // Reserve space for [CLS] and [SEP]
                    wordPieceTokens.add(subId)
                } else {
                    break
                }
            }
            if (wordPieceTokens.size >= maxLen - 2) break
        }

        val totalLength = maxLen
        val inputIds = LongArray(totalLength) { padId }
        val attentionMask = LongArray(totalLength) { 0L }
        val tokenTypeIds = LongArray(totalLength) { 0L }

        // [CLS] at position 0
        inputIds[0] = clsId
        attentionMask[0] = 1L

        // Tokens
        for (i in wordPieceTokens.indices) {
            inputIds[i + 1] = wordPieceTokens[i]
            attentionMask[i + 1] = 1L
        }

        // [SEP] after tokens
        val sepIndex = wordPieceTokens.size + 1
        inputIds[sepIndex] = sepId
        attentionMask[sepIndex] = 1L

        return TokenizedInput(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds,
            actualTokenLength = sepIndex + 1
        )
    }

    private fun normalize(text: String): String {
        // Lowercase and strip accents (BERT uncased)
        val lower = text.lowercase()
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (ch in normalized) {
            val type = Character.getType(ch)
            if (type != Character.NON_SPACING_MARK.toInt()) {
                if (isControl(ch)) {
                    sb.append(' ')
                } else {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        val type = Character.getType(ch)
        return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
    }

    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        if ((cp in 33..47) || (cp in 58..64) || (cp in 91..96) || (cp in 123..126)) return true
        val type = Character.getType(ch)
        return type in listOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt()
        )
    }

    private fun basicTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val currentToken = StringBuilder()

        for (ch in text) {
            if (ch.isWhitespace()) {
                if (currentToken.isNotEmpty()) {
                    tokens.add(currentToken.toString())
                    currentToken.clear()
                }
            } else if (isPunctuation(ch)) {
                if (currentToken.isNotEmpty()) {
                    tokens.add(currentToken.toString())
                    currentToken.clear()
                }
                tokens.add(ch.toString())
            } else {
                currentToken.append(ch)
            }
        }
        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken.toString())
        }
        return tokens
    }

    private fun wordPieceTokenize(word: String): List<Long> {
        if (word.length > 100) {
            return listOf(unkId)
        }

        var isBad = false
        var start = 0
        val subTokens = mutableListOf<Long>()

        while (start < word.length) {
            var end = word.length
            var curSubStrId: Long? = null
            var matchedEnd = -1

            while (start < end) {
                var substr = word.substring(start, end)
                if (start > 0) {
                    substr = "##$substr"
                }
                val id = vocab[substr]
                if (id != null) {
                    curSubStrId = id
                    matchedEnd = end
                    break
                }
                end--
            }

            if (curSubStrId == null) {
                isBad = true
                break
            }

            subTokens.add(curSubStrId)
            start = matchedEnd
        }

        return if (isBad) listOf(unkId) else subTokens
    }

    companion object {
        @Volatile
        private var INSTANCE: WordPieceTokenizer? = null

        fun createFromInputStream(stream: InputStream, maxSequenceLength: Int = 128): WordPieceTokenizer {
            val vocabMap = HashMap<String, Long>(30522)
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String? = reader.readLine()
                var id = 0L
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        vocabMap[trimmed] = id
                    }
                    id++
                    line = reader.readLine()
                }
            }
            return WordPieceTokenizer(vocabMap, maxSequenceLength = maxSequenceLength)
        }

        fun getInstance(context: Context): WordPieceTokenizer {
            return INSTANCE ?: synchronized(this) {
                val instance = context.assets.open("vocab.txt").use { stream ->
                    createFromInputStream(stream)
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
