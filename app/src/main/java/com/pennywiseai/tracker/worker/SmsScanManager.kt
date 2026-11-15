package com.pennywiseai.tracker.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SMS scanning operations using WorkManager.
 * Uses OptimizedSmsReaderWorker for parallel processing and progress tracking.
 */
@Singleton
class SmsScanManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Starts a one-time SMS scan using the optimized worker for faster processing.
     */
    fun startSmsLoggingScan() {
        val smsReaderWork = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .addTag("sms_logging")
            .addTag("optimized_sms_processing")
            .build()

        workManager.enqueueUniqueWork(
            OptimizedSmsReaderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            smsReaderWork
        )
    }

    /**
     * Cancels any ongoing SMS scanning work.
     */
    fun cancelSmsScanning() {
        workManager.cancelUniqueWork(OptimizedSmsReaderWorker.WORK_NAME)
    }

    /**
     * Gets the work info for monitoring progress of the SMS scan.
     */
    fun getSmsScanWorkInfo() = workManager.getWorkInfosByTagLiveData("optimized_sms_processing")
}
