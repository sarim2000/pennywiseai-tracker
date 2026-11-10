package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.NotificationPreferences
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.latestReviewOffset
import com.pennywiseai.tracker.domain.review.percentChange
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.changeText
import com.pennywiseai.tracker.utils.withLatinDigits
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The opt-in weekly recap: last week's spending, compared with the week before.
 *
 * Checks every morning rather than once a week. OEM battery managers kill scheduled
 * work, and a single missed weekly run would lose the whole week. [NotificationPreferences.weeklySummaryLastWeek]
 * makes the check idempotent: the first run that gets through after a week ends
 * delivers that one week, explicitly dated, and a phone off for three weeks gets one
 * recap rather than a backlog.
 */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val loader: PeriodReviewLoader,
    private val userPreferencesRepository: NotificationPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Pin back to the morning each time, as DailySummaryWorker does for its evening.
        runCatching {
            WorkManager.getInstance(applicationContext).updateWork(
                PeriodicWorkRequestBuilder<WeeklySummaryWorker>(1, TimeUnit.DAYS)
                    .setId(id)
                    .setNextScheduleTimeOverride(nextRun(ZonedDateTime.now()).toInstant().toEpochMilli())
                    .build()
            )
        }

        if (!userPreferencesRepository.weeklySummaryEnabled.first()) return Result.success()
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (ZonedDateTime.now().hour < CHECK_HOUR && !force) return Result.success()

        val review = loader.observe(ReviewKind.WEEK, latestReviewOffset(ReviewKind.WEEK)).first()
        val week = review.range.start.toString()
        if (userPreferencesRepository.weeklySummaryLastWeek.first() == week && !force) return Result.success()

        // A quiet week stays quiet, and still counts as delivered. A currency only here
        // because it fell to nothing has nothing to say in a recap of what happened.
        val active = review.currencies.filter { it.hasActivity }
        if (active.isNotEmpty()) {
            val context = applicationContext.withLatinDigits()
            val totals = active.associate { it.currency to Money(it.spent, it.currency) }
            val title = context.getString(
                R.string.notif_weekly_title,
                CurrencyFormatter.formatByCurrency(totals, fallbackCurrency = active.first().currency, maskIfHidden = false)
            )
            val dates = DateRangeUtils.formatDateRange(review.range.start, review.range.end)
            val single = active.singleOrNull()
            // One currency only: "12% less" says nothing about a mixed total.
            val change = single?.let { currency ->
                percentChange(currency.spent, currency.previousSpent)?.let {
                    changeText(context, it, context.getString(R.string.review_target_week))
                }
            }
            val count = active.sumOf { it.transactionCount }
            val text = listOfNotNull(
                dates,
                change,
                count.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.notif_daily_count, it, it) },
            ).joinToString(" · ")
            notify(context, title, text, week)
        }
        userPreferencesRepository.setWeeklySummaryLastWeek(week)
        return Result.success()
    }

    private fun notify(context: Context, title: String, text: String, week: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_weekly_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_weekly_channel_description) }
        )
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_REVIEW, ReviewKind.WEEK.name)
            // A recap can sit in the shade for weeks; the tap must open the week it reports.
            .putExtra(MainActivity.EXTRA_REVIEW_WEEK_START, week)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                // Figures stay off the lock screen, like the other recaps (#734).
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        )
    }

    companion object {
        private const val WORK_NAME = "weekly_summary"
        private const val CHANNEL_ID = "weekly_summary"
        // Taken: the daily recap's -2, the transaction stack's header -3, and the transaction
        // alerts, whose ids are never negative. -4 is the conscience's in #1125.
        private const val NOTIFICATION_ID = -5
        /** Input flag: skip the morning gate and the delivered-week check (debug probe only). */
        const val KEY_FORCE = "force"
        private const val CHECK_HOUR = 10

        /** The next local 10 AM strictly after [now]. */
        internal fun nextRun(now: ZonedDateTime): ZonedDateTime {
            val today = now.toLocalDate().atTime(CHECK_HOUR, 0).atZone(now.zone)
            return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(CHECK_HOUR, 0).atZone(now.zone)
        }

        /** Schedules the check while [enabled] and cancels it otherwise. */
        fun sync(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = ZonedDateTime.now()
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun(now)))
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
