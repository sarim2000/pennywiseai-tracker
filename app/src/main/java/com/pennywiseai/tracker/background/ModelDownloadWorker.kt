package com.pennywiseai.tracker.background

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.llm.PersistentModelDownloader
import com.pennywiseai.tracker.firebase.FirebaseHelper
import kotlinx.coroutines.flow.collect
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ModelDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "model_download_channel"
        private const val WORK_NAME = "model_download_work"
        
        fun enqueue(context: Context) {
            val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .addTag(WORK_NAME)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only for 2.7GB download
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true) // Ensure enough storage
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP, // Keep existing work to allow resume
                downloadRequest
            )
            
            Log.i(TAG, "📥 Background model download enqueued")
        }
        
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "❌ Background model download cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "📥 Starting background model download...")
            
            // Validate network connectivity
            if (!isNetworkSuitable()) {
                Log.w(TAG, "⚠️ Network not suitable for download")
                return Result.retry()
            }
            
            // Create notification channel
            createNotificationChannel()
            
            // Show initial notification
            showNotification("Starting model download...", 0, 0, 0)
            
            val modelDownloader = PersistentModelDownloader(applicationContext)
            val startTime = System.currentTimeMillis()
            
            // Check if there's already a download in progress
            val downloadState = modelDownloader.getDownloadState()
            if (downloadState.canResume) {
                Log.i(TAG, "🔄 Resuming download from ${downloadState.downloadedBytes / (1024 * 1024)}MB")
            }
            
            // Start download
            var downloadResult = Result.success()
            
            modelDownloader.downloadModel().collect { progress ->
                val downloadedMB = progress.bytesDownloaded / (1024 * 1024)
                val totalMB = progress.totalBytes / (1024 * 1024)
                val speedKBps = calculateSpeed(progress.bytesDownloaded, startTime)
                
                // Report progress to WorkManager
                val progressData = workDataOf(
                    "bytesDownloaded" to progress.bytesDownloaded,
                    "totalBytes" to progress.totalBytes,
                    "progressPercentage" to progress.progressPercentage,
                    "speedBytesPerSecond" to progress.speedBytesPerSecond,
                    "isComplete" to progress.isComplete,
                    "error" to (progress.error ?: "")
                )
                setProgressAsync(progressData)
                
                if (progress.isComplete) {
                    if (progress.error != null) {
                        Log.e(TAG, "❌ Background download failed: ${progress.error}")
                        showNotification("Model download failed: ${progress.error}", 100, totalMB, 0)
                        FirebaseHelper.logException(RuntimeException("Model download failed: ${progress.error}"))
                        downloadResult = Result.failure()
                    } else {
                        Log.i(TAG, "✅ Background download completed: ${totalMB}MB downloaded")
                        showNotification("Model download completed! (${totalMB}MB)", 100, totalMB, speedKBps)
                        FirebaseHelper.logModelDownload(true, totalMB * 1024, System.currentTimeMillis() - startTime)
                    }
                } else {
                    showNotification(
                        "Downloading model... ${downloadedMB}MB / ${totalMB}MB",
                        progress.progressPercentage,
                        downloadedMB,
                        speedKBps
                    )
                }
            }
            
            downloadResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background download error: ${e.message}")
            
            // Check if we should retry
            val shouldRetry = when {
                runAttemptCount < 3 -> true // Retry up to 3 times
                e is java.net.SocketTimeoutException -> true
                e is java.net.UnknownHostException -> true
                e is java.io.IOException && e.message?.contains("ECONNRESET") == true -> true
                else -> false
            }
            
            return if (shouldRetry) {
                Log.i(TAG, "🔄 Will retry download (attempt ${runAttemptCount + 1})")
                showNotification("Download failed, will retry...", 100, 0, 0)
                Result.retry()
            } else {
                showNotification("Model download failed: ${e.message}", 100, 0, 0)
                FirebaseHelper.logException(e)
                Result.failure()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of AI model download in background"
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(message: String, progress: Int, downloadedMB: Long, speedKBps: Long) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val speedText = if (speedKBps > 0) " • ${speedKBps}KB/s" else ""
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("AI Model Download")
            .setContentText(message + speedText)
            .setSmallIcon(R.drawable.ic_dashboard)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(progress < 100)
            .apply {
                if (progress < 100) {
                    setProgress(100, progress, false)
                }
            }
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        // Remove notification after completion
        if (progress >= 100) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                notificationManager.cancel(NOTIFICATION_ID)
            }, 5000)
        }
    }
    
    private fun calculateSpeed(bytesDownloaded: Long, startTime: Long): Long {
        val elapsedMs = System.currentTimeMillis() - startTime
        return if (elapsedMs > 0) {
            (bytesDownloaded / 1024) / (elapsedMs / 1000).coerceAtLeast(1)
        } else 0
    }
    
    private fun isNetworkSuitable(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            // Check if network is unmetered (WiFi)
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            // Fallback for older devices
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }
}
