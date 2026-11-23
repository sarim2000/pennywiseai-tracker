package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.domain.spending.UpcomingPaymentsLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Rebuilds the Next bills widget's snapshot from the same payments the calendar
 * shows as due (selected profile, no ignored accounts). Enqueued with the other
 * widgets via [WidgetRefresher], when subscriptions, recurring payments or card bills change
 * (PennyWiseApplication observes them), and on the widget's update period.
 */
@HiltWorker
class NextBillsWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val upcomingPaymentsLoader: UpcomingPaymentsLoader,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "next_bills_widget_update"

        /** Far enough for a yearly bill to be someone's next one. */
        private const val LOOKAHEAD_DAYS = 366L

        fun enqueueOneShot(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<NextBillsWidgetUpdateWorker>().build()
            )
        }
    }

    override suspend fun doWork(): Result = try {
        // Every transaction write asks for this; skip the bill walk when no widget is placed.
        // Adding one runs it again (the receiver's onUpdate).
        if (GlanceAppWidgetManager(applicationContext).getGlanceIds(NextBillsWidget::class.java).isNotEmpty()) {
            val today = LocalDate.now()
            val bills = upcomingPaymentsLoader.observe(today, today.plusDays(LOOKAHEAD_DAYS)).first()
            NextBillsWidgetDataStore.update(applicationContext, NextBillsSnapshot(today.toEpochDay(), nextBills(bills)))
            NextBillsWidget().updateAll(applicationContext)
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
