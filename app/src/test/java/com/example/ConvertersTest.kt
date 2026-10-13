package com.example

import com.example.core.database.Converters
import com.example.core.model.IndexingStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testFloatArrayToByteArrayRoundTrip() {
        val original = floatArrayOf(0.123f, -0.456f, 1.0f, 0.0f, -1.0f, 0.9999f)
        val bytes = converters.fromFloatArray(original)
        assertNotNull(bytes)
        assertEquals(original.size * 4, bytes!!.size)

        val restored = converters.toFloatArray(bytes)
        assertNotNull(restored)
        assertArrayEquals(original, restored!!, 1e-6f)
    }

    @Test
    fun testIndexingStatusEnumConversion() {
        assertEquals("INDEXED", converters.fromIndexingStatus(IndexingStatus.INDEXED))
        assertEquals(IndexingStatus.INDEXED, converters.toIndexingStatus("INDEXED"))
        assertEquals(IndexingStatus.PENDING, converters.toIndexingStatus("UNKNOWN_VALUE"))
    }
}
