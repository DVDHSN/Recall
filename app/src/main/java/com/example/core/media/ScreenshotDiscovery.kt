package com.example.core.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.model.ImageSource
import com.example.core.model.IndexingStatus
import com.example.core.model.MediaPermissionState
import com.example.core.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.max

class ScreenshotDiscovery(private val context: Context) {

    fun getMediaPermissionState(): MediaPermissionState {
        val hasFull = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFull) return MediaPermissionState.FULL_ACCESS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasPartial = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPartial) return MediaPermissionState.PARTIAL_ACCESS
        }

        return MediaPermissionState.DENIED
    }

    suspend fun discoverScreenshots(screenshotsOnly: Boolean = false): List<ScreenshotItem> = withContext(Dispatchers.IO) {
        val permState = getMediaPermissionState()
        if (permState == MediaPermissionState.DENIED) {
            Log.w(TAG, "Media permission is DENIED; returning empty list from MediaStore discovery.")
            return@withContext emptyList()
        }

        val images = mutableListOf<ScreenshotItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        // Filter valid non-empty images.
        // If screenshotsOnly is requested, filter specifically by screenshot directory and naming heuristics.
        val (selection, selectionArgs) = if (screenshotsOnly) {
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.RELATIVE_PATH
            } else {
                MediaStore.Images.Media.DATA
            }
            val sel = "($pathCol LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) " +
                    "AND ${MediaStore.Images.Media.SIZE} > 0 AND ${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/%'"
            sel to arrayOf("%Screenshot%", "%Screenshot%", "%Screenshot%")
        } else {
            // Universal discovery across all accessible folders: Camera, Downloads, Screenshots, Social, Notes, Receipts, etc.
            val sel = "${MediaStore.Images.Media.SIZE} > 0 AND ${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/%'"
            sel to null
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
                val mimeCol = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
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
                val bucketCol = it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Image_$mediaId"
                    val mime = if (mimeCol >= 0) it.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val width = if (widthCol >= 0) it.getInt(widthCol) else 1080
                    val height = if (heightCol >= 0) it.getInt(heightCol) else 1920
                    val sizeBytes = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val dateAdded = if (addedCol >= 0) it.getLong(addedCol) else System.currentTimeMillis() / 1000
                    val dateModified = if (modifiedCol >= 0) it.getLong(modifiedCol) else System.currentTimeMillis() / 1000
                    val path = if (pathCol >= 0) it.getString(pathCol) ?: "Pictures" else "Pictures"
                    val bucketName = if (bucketCol >= 0) it.getString(bucketCol) ?: "Pictures" else "Pictures"

                    val source = determineImageSource(name, path, bucketName)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    ).toString()

                    images.add(
                        ScreenshotItem(
                            mediaStoreId = mediaId,
                            contentUri = contentUri,
                            displayName = name,
                            relativePath = path,
                            mimeType = mime,
                            width = if (width > 0) width else 1080,
                            height = if (height > 0) height else 1920,
                            sizeBytes = sizeBytes,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            indexingStatus = IndexingStatus.PENDING,
                            bucketName = bucketName,
                            imageSource = source.name
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying MediaStore images: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore images", e)
        }

        images
    }

    suspend fun extractItemFromUri(uri: Uri): ScreenshotItem? = withContext(Dispatchers.IO) {
        try {
            var name = "Image_${System.currentTimeMillis()}"
            var sizeBytes = 0L
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val nowSec = System.currentTimeMillis() / 1000

            try {
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
            } catch (e: Exception) {
                Log.w(TAG, "Query metadata for $uri failed: ${e.message}")
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
                indexingStatus = IndexingStatus.PENDING,
                bucketName = "Imported",
                imageSource = ImageSource.IMPORTED.name
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URI $uri", e)
            null
        }
    }

    fun isUriAccessible(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: FileNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loadDownscaledBitmap(uriString: String, maxDimension: Int = 1920): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            // First decode bounds
            var input: InputStream? = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "Image file not found (deleted): $uriString")
                return@withContext null
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException opening image: $uriString")
                return@withContext null
            } catch (e: Exception) {
                Log.w(TAG, "Error opening image stream: ${e.message}")
                return@withContext null
            }

            if (input == null) return@withContext null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

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

        fun determineImageSource(displayName: String, relativePath: String, bucketName: String = ""): ImageSource {
            val combined = "$displayName $relativePath $bucketName".lowercase()
            return when {
                relativePath.equals("Imported", ignoreCase = true) -> ImageSource.IMPORTED
                combined.contains("screenshot") ||
                        combined.contains("screen_shot") ||
                        combined.contains("screencap") ||
                        combined.contains("screen-shot") -> ImageSource.SCREENSHOT
                combined.contains("camera") ||
                        combined.contains("dcim") ||
                        displayName.lowercase().startsWith("img_") ||
                        displayName.lowercase().startsWith("pxl_") ||
                        displayName.lowercase().startsWith("dsc_") -> ImageSource.CAMERA
                combined.contains("download") -> ImageSource.DOWNLOAD
                combined.contains("whatsapp") ||
                        combined.contains("telegram") ||
                        combined.contains("instagram") ||
                        combined.contains("twitter") ||
                        combined.contains("reddit") ||
                        combined.contains("facebook") ||
                        combined.contains("messenger") ||
                        combined.contains("snapchat") ||
                        combined.contains("signal") -> ImageSource.SOCIAL_MESSAGING
                combined.contains("document") ||
                        combined.contains("note") ||
                        combined.contains("scan") ||
                        combined.contains("receipt") -> ImageSource.DOCUMENTS_NOTES
                else -> ImageSource.OTHER
            }
        }

        fun isScreenshot(displayName: String, relativePath: String, bucketName: String = ""): Boolean {
            return determineImageSource(displayName, relativePath, bucketName) == ImageSource.SCREENSHOT
        }

        fun isCameraPhoto(displayName: String, relativePath: String, bucketName: String = ""): Boolean {
            return determineImageSource(displayName, relativePath, bucketName) == ImageSource.CAMERA
        }
    }
}
