package com.example.core.indexing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
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
                    setForeground(createForegroundInfo("Processing images in background…"))
                } catch (e: Exception) {
                    Log.w(TAG, "Foreground worker notice: ${e.message}")
                }
            }

            // Drains all awaiting images continuously
            val indexedCount = repository.indexPendingScreenshots()

            setProgress(workDataOf(KEY_INDEXED_COUNT to indexedCount))
            Result.success(workDataOf(KEY_INDEXED_COUNT to indexedCount))
        } catch (e: Exception) {
            Log.e(TAG, "Error in IndexingWorker", e)
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
            .setContentTitle("Recall: Indexing Images in Background")
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
        private const val TAG = "IndexingWorker"
        const val KEY_INDEXED_COUNT = "indexed_count"
        const val NOTIFICATION_ID = 1001
        const val UNIQUE_WORK_NAME = "recall_background_indexing"

        fun enqueue(context: Context) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<IndexingWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not enqueue background IndexingWorker: ${e.message}")
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            } catch (e: Exception) {
                Log.w(TAG, "Could not cancel background IndexingWorker: ${e.message}")
            }
        }
    }
}
