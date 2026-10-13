package com.example.core.visual

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

object VisualFeatureExtractor {

    const val FEATURE_DIMENSION = 32

    /**
     * Extracts a compact 32-dimensional spatial-color layout embedding vector.
     * Uses a 4x4 spatial grid (16 cells) capturing regional luminance and color chromaticity.
     * Vector is normalized to unit length for fast dot-product cosine similarity.
     */
    fun extractVisualVector(bitmap: Bitmap): FloatArray {
        val features = FloatArray(FEATURE_DIMENSION)
        val gridRows = 4
        val gridCols = 4
        val cellWidth = bitmap.width / gridCols
        val cellHeight = bitmap.height / gridRows

        if (cellWidth <= 0 || cellHeight <= 0) return features

        var featureIdx = 0
        for (row in 0 until gridRows) {
            for (col in 0 until gridCols) {
                val startX = col * cellWidth
                val startY = row * cellHeight

                var sumL = 0f
                var sumChromatic = 0f
                var sampleCount = 0

                // Step by 4 for speed
                val step = 4
                for (y in startY until (startY + cellHeight) step step) {
                    for (x in startX until (startX + cellWidth) step step) {
                        if (x < bitmap.width && y < bitmap.height) {
                            val pixel = bitmap.getPixel(x, y)
                            val r = Color.red(pixel) / 255f
                            val g = Color.green(pixel) / 255f
                            val b = Color.blue(pixel) / 255f

                            val l = 0.299f * r + 0.587f * g + 0.114f * b
                            val chromaticity = (r - g) * (r - g) + (g - b) * (g - b) + (b - r) * (b - r)

                            sumL += l
                            sumChromatic += chromaticity
                            sampleCount++
                        }
                    }
                }

                val avgL = if (sampleCount > 0) sumL / sampleCount else 0.5f
                val avgChromatic = if (sampleCount > 0) sumChromatic / sampleCount else 0f

                features[featureIdx] = avgL
                features[featureIdx + 1] = avgChromatic
                featureIdx += 2
            }
        }

        // L2 Normalize
        var sumSquares = 0f
        for (v in features) sumSquares += v * v
        val norm = sqrt(sumSquares)
        if (norm > 1e-6f) {
            for (i in features.indices) {
                features[i] /= norm
            }
        }

        return features
    }

    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size || vec1.isEmpty()) return 0f
        var dot = 0f
        for (i in vec1.indices) {
            dot += vec1[i] * vec2[i]
        }
        return dot.coerceIn(0f, 1f)
    }
}
