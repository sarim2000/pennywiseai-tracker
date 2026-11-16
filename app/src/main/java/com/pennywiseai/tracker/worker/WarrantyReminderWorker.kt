package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.warranty.WarrantyReminders
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.bidiIsolate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * A heads-up 30 days and 1 day before a purchase's warranty ends, each morning
 * alongside the bill reminders. Tapping it opens the transaction, receipt and all.
 * The last day dealt with is kept on this device, so a missed day is caught up
 * and nothing is sent twice. Users silence it from the system's notification
 * settings (channel "Warranty reminders").
 */
@HiltWorker
class WarrantyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Pin the next run back to the bill reminders' 9 AM.
        runCatching {
            WorkManager.getInstance(applicationContext).updateWork(
                PeriodicWorkRequestBuilder<WarrantyReminderWorker>(1, TimeUnit.DAYS)
                    .setId(id)
                    .setNextScheduleTimeOverride(BillReminderWorker.nextRun(ZonedDateTime.now()).toInstant().toEpochMilli())
                    .build()
            )
        }

        val context = applicationContext
        // Notifications off: stay quiet and keep the watermark, so turning them back on
        // still brings the reminders for warranties that haven't ended.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()

        val today = LocalDate.now()
        val due = WarrantyReminders.due(
            transactionDao.getWithWarrantyFrom(today),
            today,
            userPreferencesRepository.warrantyReminderLastDay.first()
        )
        if (due.isNotEmpty()) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.warranty_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.warranty_reminder_channel_description) }
            )
            due.forEach { txn ->
                try {
                    notificationManager.notify(NOTIFICATION_TAG, txn.id.toInt(), build(context, txn, today))
                } catch (e: SecurityException) {
                    // Permission revoked between the check and now.
                    Log.w(TAG, "Warranty reminder not shown", e)
                }
            }
        }
        userPreferencesRepository.setWarrantyReminderLastDay(today)
        return Result.success()
    }

    private fun build(context: Context, txn: TransactionEntity, today: LocalDate) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(context, txn, today))
            .setContentText(
                context.getString(
                    R.string.warranty_reminder_text,
                    txn.warrantyUntil?.format(AppDates.dateFormatter()).orEmpty(),
                    CurrencyFormatter.formatCurrency(txn.amount, txn.currency, maskIfHidden = false),
                    txn.dateTime.toLocalDate().format(AppDates.dateFormatter())
                )
            )
            .setContentIntent(SmsBroadcastReceiver.openTransactionIntent(context, txn.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            // Figures stay off the lock screen, like the other alerts (#734).
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

    private fun title(context: Context, txn: TransactionEntity, today: LocalDate): String {
        val merchant = context.bidiIsolate(WarrantyReminders.itemName(txn))
        val daysLeft = txn.warrantyUntil?.let { ChronoUnit.DAYS.between(today, it) } ?: 0
        return when {
            daysLeft <= 0 -> context.getString(R.string.warranty_reminder_title_today, merchant)
            daysLeft == 1L -> context.getString(R.string.warranty_reminder_title_tomorrow, merchant)
            else -> context.resources.getQuantityString(R.plurals.warranty_reminder_title, daysLeft.toInt(), merchant, daysLeft.toInt())
        }
    }

    companion object {
        private const val TAG = "WarrantyReminderWorker"
        private const val WORK_NAME = "warranty_reminders"
        private const val CHANNEL_ID = "warranty_reminders"

        /** A tag of its own, so the transaction id can't collide with another notification's id. */
        private const val NOTIFICATION_TAG = "warranty"

        /** Daily at 9 AM local. KEEP so re-enqueuing on every app start doesn't reset the schedule. */
        fun enqueuePeriodic(context: Context) {
            val now = ZonedDateTime.now()
            val request = PeriodicWorkRequestBuilder<WarrantyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, BillReminderWorker.nextRun(now)))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
