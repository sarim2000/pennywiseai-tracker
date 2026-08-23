package com.pennywiseai.tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Materialises due recurring / scheduled manual transaction templates (#706).
 *
 * Runs daily (periodic) plus a one-shot catch-up at app start so a device that
 * was off still materialises anything that came due while it was down. All the
 * double-insertion safety lives in
 * [RecurringTransactionRepository.materializeDue].
 */
@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringTransactionRepository: RecurringTransactionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME_PERIODIC = "recurring_transactions_materialize_periodic"
        private const val WORK_NAME_ONE_SHOT = "recurring_transactions_materialize_one_shot"

        /**
         * Daily periodic materialisation. KEEP so re-enqueuing on every app
         * start doesn't reset the schedule.
         */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
                1, TimeUnit.DAYS
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        /**
         * One-shot catch-up run — used at app start so due templates materialise
         * immediately rather than waiting up to a day for the periodic run.
         */
        fun enqueueOneShotCatchUp(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecurringTransactionWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val created = recurringTransactionRepository.materializeDue(LocalDate.now())
            if (created > 0) {
                // New transactions landed — keep the home/transactions widgets fresh.
                WidgetRefresher.refreshTransactionWidgets(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
