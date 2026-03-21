package com.pennywiseai.tracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import com.pennywiseai.tracker.data.repository.BankNotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Retries processing for bank notifications that previously failed.
 * Enqueued as a one-shot delayed job when a notification fails to parse.
 */
@HiltWorker
class BankNotificationRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: SmsTransactionProcessor,
    private val notificationRepository: BankNotificationRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BankNotifRetryWorker"
        private const val WORK_NAME = "bank_notification_retry"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BankNotificationRetryWorker>()
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        val unprocessed = notificationRepository.getUnprocessed()
        if (unprocessed.isEmpty()) return Result.success()

        Log.d(TAG, "Retrying ${unprocessed.size} unprocessed notifications")

        for (notification in unprocessed) {
            try {
                val result = processor.processAndSaveTransaction(
                    sender = notification.senderAlias,
                    body = notification.messageBody,
                    timestamp = notification.postedAt
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
                if (result.success) {
                    notificationRepository.markProcessed(notification.id, result.transactionId)
                    Log.d(TAG, "Retry succeeded for notification ${notification.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed for notification ${notification.id}", e)
            }
        }

        return Result.success()
    }
}
