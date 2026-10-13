package com.example

import com.example.core.visual.PerceptualHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHasherTest {

    @Test
    fun testHammingDistance() {
        val hashA = 0b10101010L
        val hashB = 0b10101010L
        assertEquals(0, PerceptualHasher.hammingDistance(hashA, hashB))

        val hashC = 0b10101011L
        assertEquals(1, PerceptualHasher.hammingDistance(hashA, hashC))

        val hashD = 0b00000000L
        assertEquals(4, PerceptualHasher.hammingDistance(hashA, hashD))
    }

    @Test
    fun testNearDuplicateThreshold() {
        val baseHash = 0x1234567890ABCDEFL
        // 4 differing bits
        val minorDiffHash = baseHash xor 0b00001111L
        assertTrue(PerceptualHasher.isNearDuplicate(baseHash, minorDiffHash, threshold = 8))

        // 16 differing bits
        val majorDiffHash = baseHash xor 0xFFFF0000L
        assertFalse(PerceptualHasher.isNearDuplicate(baseHash, majorDiffHash, threshold = 8))
    }
}
