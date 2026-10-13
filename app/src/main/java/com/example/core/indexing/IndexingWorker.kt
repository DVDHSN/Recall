package com.example.core.indexing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.repository.ScreenshotRepository

class IndexingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repository = ScreenshotRepository.getInstance(appContext)

    override suspend fun doWork(): Result {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    setForeground(createForegroundInfo("Starting screenshot indexing…"))
                } catch (e: Exception) {
                    // Ignored if foreground worker cannot start
                }
            }

            repository.discoverAndReconcile()
            val indexedCount = repository.indexPendingScreenshots(batchLimit = 200)

            setProgress(workDataOf(KEY_INDEXED_COUNT to indexedCount))
            Result.success(workDataOf(KEY_INDEXED_COUNT to indexedCount))
        } catch (e: Exception) {
            if (isStopped) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.localizedMessage ?: "Unknown error")))
            }
        }
    }

    private fun createForegroundInfo(progressText: String): ForegroundInfo {
        val channelId = "recall_indexing_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Recall Indexing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "On-device OCR and embedding indexing"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Recall: Indexing Screenshots")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_INDEXED_COUNT = "indexed_count"
        const val NOTIFICATION_ID = 1001
        const val UNIQUE_WORK_NAME = "recall_background_indexing"
    }
}
