package com.example.core.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.core.model.IndexingStatus
import com.example.core.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

class ScreenshotDiscovery(private val context: Context) {

    suspend fun discoverScreenshots(screenshotsOnly: Boolean = false): List<ScreenshotItem> = withContext(Dispatchers.IO) {
        val screenshots = mutableListOf<ScreenshotItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        )

        // Heuristics: if screenshotsOnly is true, filter by screenshot patterns.
        // Otherwise, discover all images across the device (Camera, Pictures, Downloads, etc.)
        val (selection, selectionArgs) = if (screenshotsOnly) {
            val sel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ? OR " +
                "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            }
            sel to arrayOf("%Screenshot%", "%Screenshot%", "%Screenshot%")
        } else {
            // Null selection queries all valid images in MediaStore
            null to null
        }
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = it.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = it.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeCol = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                val addedCol = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val modifiedCol = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    it.getColumnIndex(MediaStore.Images.Media.DATA)
                }

                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Image_$mediaId"
                    val mime = it.getString(mimeCol) ?: "image/jpeg"
                    val width = if (widthCol >= 0) it.getInt(widthCol) else 1080
                    val height = if (heightCol >= 0) it.getInt(heightCol) else 1920
                    val sizeBytes = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val dateAdded = if (addedCol >= 0) it.getLong(addedCol) else System.currentTimeMillis() / 1000
                    val dateModified = if (modifiedCol >= 0) it.getLong(modifiedCol) else System.currentTimeMillis() / 1000
                    val path = if (pathCol >= 0) it.getString(pathCol) ?: "Pictures" else "Pictures"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    ).toString()

                    screenshots.add(
                        ScreenshotItem(
                            mediaStoreId = mediaId,
                            contentUri = contentUri,
                            displayName = name,
                            relativePath = path,
                            mimeType = mime,
                            width = width,
                            height = height,
                            sizeBytes = sizeBytes,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            indexingStatus = IndexingStatus.PENDING
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying MediaStore images: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore images", e)
        }

        screenshots
    }

    suspend fun extractItemFromUri(uri: Uri): ScreenshotItem? = withContext(Dispatchers.IO) {
        try {
            var name = "Image_${System.currentTimeMillis()}"
            var sizeBytes = 0L
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val nowSec = System.currentTimeMillis() / 1000

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                    if (sizeIndex >= 0) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            var width = 1080
            var height = 1920
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        width = options.outWidth
                        height = options.outHeight
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading dimensions for $uri: ${e.message}")
            }

            val generatedId = kotlin.math.abs((uri.toString().hashCode().toLong() shl 16) xor System.currentTimeMillis())

            ScreenshotItem(
                mediaStoreId = generatedId,
                contentUri = uri.toString(),
                displayName = name,
                relativePath = "Imported",
                mimeType = mime,
                width = width,
                height = height,
                sizeBytes = sizeBytes,
                dateAdded = nowSec,
                dateModified = nowSec,
                indexingStatus = IndexingStatus.PENDING
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URI $uri", e)
            null
        }
    }

    suspend fun loadDownscaledBitmap(uriString: String, maxDimension: Int = 1920): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            // First decode bounds
            var input: InputStream? = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            // Calculate sample size
            var sampleSize = 1
            val largestDim = max(origWidth, origHeight)
            while (largestDim / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Low memory footprint for OCR

            input = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input, null, options)
            input?.close()
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bitmap for $uriString: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ScreenshotDiscovery"

        fun isScreenshot(displayName: String, relativePath: String): Boolean {
            val combined = "$displayName $relativePath".lowercase()
            return combined.contains("screenshot") ||
                    combined.contains("screen_shot") ||
                    combined.contains("screencap") ||
                    combined.contains("screen-shot")
        }

        fun isCameraPhoto(displayName: String, relativePath: String): Boolean {
            val combined = "$displayName $relativePath".lowercase()
            return combined.contains("dcim") ||
                    combined.contains("camera") ||
                    displayName.lowercase().startsWith("img_") ||
                    displayName.lowercase().startsWith("pxl_") ||
                    displayName.lowercase().startsWith("dsc_")
        }
    }
}
