package com.example

import com.example.core.ml.tokenizer.WordPieceTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TokenizerTest {

    private fun createTestTokenizer(): WordPieceTokenizer {
        // Minimal vocabulary for testing WordPiece logic
        val vocabLines = listOf(
            "[PAD]",
            "[UNK]",
            "[CLS]",
            "[SEP]",
            "[MASK]",
            "the",
            "recipe",
            "with",
            "chick",
            "##pea",
            "##s",
            "wifi",
            "password",
            "null",
            "pointer",
            "exception",
            "192",
            ".",
            "168",
            "1"
        )
        val stream = ByteArrayInputStream(vocabLines.joinToString("\n").toByteArray(Charsets.UTF_8))
        return WordPieceTokenizer.createFromInputStream(stream, maxSequenceLength = 16)
    }

    @Test
    fun testSpecialTokensPlacement() {
        val tokenizer = createTestTokenizer()
        val result = tokenizer.tokenize("the recipe")

        // First token must be [CLS]
        assertEquals(tokenizer.clsId, result.inputIds[0])
        assertEquals(1L, result.attentionMask[0])

        // Last token must be [SEP]
        val sepIndex = result.actualTokenLength - 1
        assertEquals(tokenizer.sepId, result.inputIds[sepIndex])
        assertEquals(1L, result.attentionMask[sepIndex])

        // Trailing elements must be [PAD] with attention mask 0
        for (i in (sepIndex + 1) until result.inputIds.size) {
            assertEquals(tokenizer.padId, result.inputIds[i])
            assertEquals(0L, result.attentionMask[i])
        }
    }

    @Test
    fun testSubwordTokenization() {
        val tokenizer = createTestTokenizer()
        val result = tokenizer.tokenize("chickpeas")

        // "chickpeas" should split into "chick", "##pea", "##s"
        assertTrue(result.actualTokenLength >= 5) // [CLS] + chick + ##pea + ##s + [SEP]
    }

    @Test
    fun testLowercaseNormalization() {
        val tokenizer = createTestTokenizer()
        val lowerResult = tokenizer.tokenize("wifi password")
        val upperResult = tokenizer.tokenize("WIFI PASSWORD")

        assertEquals(lowerResult.inputIds[1], upperResult.inputIds[1])
        assertEquals(lowerResult.inputIds[2], upperResult.inputIds[2])
    }

    @Test
    fun testTechnicalTokensPreserved() {
        val tokenizer = createTestTokenizer()
        val result = tokenizer.tokenize("192.168.1.1")
        assertTrue(result.actualTokenLength > 2)
    }
}
