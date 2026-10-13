package com.example

import android.app.Application
import android.util.Log
import com.example.core.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecallApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RecallApplication initializing on Android 15 (minSdk 35)")

        // Warm up database and models asynchronously off the main thread
        applicationScope.launch {
            try {
                val repository = ScreenshotRepository.getInstance(this@RecallApplication)
                Log.d(TAG, "Pre-warmed repository and models successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-warming Recall services", e)
            }
        }
    }

    companion object {
        private const val TAG = "RecallApp"
    }
}
