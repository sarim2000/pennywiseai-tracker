package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import com.pennywiseai.tracker.receiver.BillReminderActionReceiver
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The morning heads-up for a bill due tomorrow: one notification per active
 * expense subscription, with "Mark paid" and "Skip this time" actions so the
 * schedule stays right without opening the app. A bill the bank reports paying
 * (a mandate, or one an SMS charge already paid) gets the heads-up without
 * them: its SMS marks it paid, and a manual payment on top would count it twice. Users silence it from the system's
 * notification settings (channel "Bill reminders").
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Pin the next run back to 9 AM local, as DailySummaryWorker does for 9 PM.
        runCatching {
            WorkManager.getInstance(applicationContext).updateWork(
                PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
                    .setId(id)
                    .setNextScheduleTimeOverride(nextRun(ZonedDateTime.now()).toInstant().toEpochMilli())
                    .build()
            )
        }

        val context = applicationContext
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
        // Recurring income added by hand is stored as EXPENSE today; its category
        // still says income, so it isn't reminded (or booked) as a bill.
        val due = dueTomorrow(subscriptionRepository.getActiveSubscriptions().first(), LocalDate.now())
            .filterNot { sub -> sub.category?.let { categoryDao.getCategoryByName(it)?.isIncome } == true }
        if (due.isEmpty()) return Result.success()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.bill_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.bill_reminder_channel_description) }
        )
        due.forEach { sub ->
            val notificationId = notificationId(sub.id)
            val dueDate = sub.nextPaymentDate ?: return@forEach
            // Paid by a bank debit or a transfer the bank reports: its SMS records
            // the payment, so a manual one would count it twice.
            val paidBySms = sub.umn != null ||
                transactionDao.getBankChargedRecurringMerchants(sub.currency).any { RecurringCharge.sameMerchant(it, sub.merchantName) }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.bill_reminder_title, sub.merchantName))
                .setContentText(
                    context.getString(
                        R.string.bill_reminder_text,
                        CurrencyFormatter.formatCurrency(sub.amount, sub.currency),
                        cycleLabel(context, sub.billingCycle)
                    )
                )
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        notificationId,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                // Figures stay off the lock screen, like the other alerts (#734).
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            if (!paidBySms) {
                builder
                    .addAction(
                        0,
                        context.getString(R.string.bill_reminder_action_paid),
                        BillReminderActionReceiver.actionIntent(context, BillReminderActionReceiver.ACTION_PAID, sub.id, dueDate, notificationId)
                    )
                    .addAction(
                        0,
                        context.getString(R.string.bill_reminder_action_skip),
                        BillReminderActionReceiver.actionIntent(context, BillReminderActionReceiver.ACTION_SKIP, sub.id, dueDate, notificationId)
                    )
            }
            notificationManager.notify(notificationId, builder.build())
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "bill_reminders"
        private const val CHANNEL_ID = "bill_reminders"
        private const val REMINDER_HOUR = 9

        /**
         * One id per subscription so a re-run replaces rather than stacks. Negative
         * and below the fixed ids (-2, -3): transaction alerts use the transaction id.
         */
        fun notificationId(subscriptionId: Long): Int = (-1000L - subscriptionId).toInt()

        /** Active expense subscriptions whose next payment falls on the day after [today]. */
        internal fun dueTomorrow(subscriptions: List<SubscriptionEntity>, today: LocalDate): List<SubscriptionEntity> {
            val tomorrow = today.plusDays(1)
            return subscriptions.filter {
                it.state == SubscriptionState.ACTIVE &&
                    it.direction == SubscriptionDirection.EXPENSE &&
                    it.nextPaymentDate == tomorrow
            }
        }

        /** The next local 9 AM strictly after [now]; zone-aware, so DST shifts keep it at 9 AM. */
        internal fun nextRun(now: ZonedDateTime): ZonedDateTime {
            val today = now.toLocalDate().atTime(REMINDER_HOUR, 0).atZone(now.zone)
            return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(REMINDER_HOUR, 0).atZone(now.zone)
        }

        /** Billing cycles are stored as English keys; only the label is translated. */
        private fun cycleLabel(context: Context, cycle: String): String = when (cycle) {
            "Monthly" -> context.getString(R.string.add_sub_cycle_monthly)
            "Quarterly" -> context.getString(R.string.add_sub_cycle_quarterly)
            "Semi-Annual" -> context.getString(R.string.add_sub_cycle_semi_annual)
            "Annual" -> context.getString(R.string.add_sub_cycle_annual)
            "Weekly" -> context.getString(R.string.add_sub_cycle_weekly)
            else -> cycle
        }

        /** Daily at 9 AM local. KEEP so re-enqueuing on every app start doesn't reset the schedule. */
        // ponytail: WorkManager, not an exact alarm: under Doze the reminder can land a little after 9 AM.
        fun enqueuePeriodic(context: Context) {
            val now = ZonedDateTime.now()
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun(now)))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
