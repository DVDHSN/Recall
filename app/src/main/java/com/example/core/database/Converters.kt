package com.example.core.database

import androidx.room.TypeConverter
import com.example.core.model.IndexingStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun fromIndexingStatus(status: IndexingStatus?): String {
        return status?.name ?: IndexingStatus.PENDING.name
    }

    @TypeConverter
    fun toIndexingStatus(value: String?): IndexingStatus {
        return value?.let {
            try {
                IndexingStatus.valueOf(it)
            } catch (e: Exception) {
                IndexingStatus.PENDING
            }
        } ?: IndexingStatus.PENDING
    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? {
        if (array == null || array.isEmpty()) return null
        val buffer = ByteBuffer.allocate(array.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in array) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.isEmpty()) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatCount = bytes.size / 4
        val array = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            array[i] = buffer.float
        }
        return array
    }
}
