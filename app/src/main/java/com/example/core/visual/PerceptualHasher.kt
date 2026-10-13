package com.example.core.visual

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest

object PerceptualHasher {

    /**
     * Computes a 64-bit difference perceptual hash (dHash).
     * Compares adjacent pixels in a 9x8 grayscale downscaled image.
     * Resistant to resizing, minor compression, and color tweaks.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bitIndex = 0

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = scaled.getPixel(x, y)
                val rightPixel = scaled.getPixel(x + 1, y)

                val leftBrightness = getLuminance(leftPixel)
                val rightBrightness = getLuminance(rightPixel)

                if (leftBrightness > rightBrightness) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }
        return hash
    }

    /**
     * Calculates the Hamming distance (number of differing bits) between two 64-bit hashes.
     * Range: 0 (identical) to 64 (completely inverted).
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Evaluates whether two screenshots are probable near duplicates.
     * Threshold <= 8 bits indicates >= 87.5% visual identity.
     */
    fun isNearDuplicate(hash1: Long, hash2: Long, threshold: Int = 8): Boolean {
        if (hash1 == 0L || hash2 == 0L) return false
        return hammingDistance(hash1, hash2) <= threshold
    }

    /**
     * Computes exact SHA-256 hash of pixel buffer.
     */
    fun computeContentHash(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val byteBuffer = java.nio.ByteBuffer.allocate(pixels.size * 4)
        byteBuffer.asIntBuffer().put(pixels)
        val hashBytes = digest.digest(byteBuffer.array())

        val hexString = StringBuilder()
        for (b in hashBytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    private fun getLuminance(color: Int): Float {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
