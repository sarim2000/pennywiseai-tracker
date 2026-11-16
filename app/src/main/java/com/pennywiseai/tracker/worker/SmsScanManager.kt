package com.pennywiseai.tracker.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
     * [rescanAll] re-reads the whole scan window without deleting anything, so it
     * adds what an earlier scan missed and keeps every edit.
     */
    fun startSmsLoggingScan(rescanAll: Boolean = false) {
        val smsReaderWork = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .setInputData(workDataOf(OptimizedSmsReaderWorker.INPUT_RESCAN_ALL to rescanAll))
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

    /** Whether a scan is queued or running, whoever started it. */
    fun isScanRunning(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(OptimizedSmsReaderWorker.WORK_NAME)
            .map { infos -> infos.any { !it.state.isFinished } }

    /**
     * Gets the work info for monitoring progress of the SMS scan.
     */
    fun getSmsScanWorkInfo() = workManager.getWorkInfosByTagLiveData("optimized_sms_processing")
}
