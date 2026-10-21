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
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.forTotals
import com.pennywiseai.tracker.utils.sumByCurrency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The evening recap: what today cost and how many transactions still need a
 * category, so a user who never opened the app still sees where the day went.
 * A day with nothing spent or received stays quiet.
 */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val loanRepository: LoanRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // WorkManager counts a periodic interval from when the last run ended, so
        // Doze delays, a DST change or a new time zone would walk the recap later
        // every day. Pin the next run back to 9 PM local each time.
        runCatching {
            WorkManager.getInstance(applicationContext).updateWork(
                PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                    .setId(id)
                    .setNextScheduleTimeOverride(nextRun(ZonedDateTime.now()).toInstant().toEpochMilli())
                    .build()
            )
        }

        if (!userPreferencesRepository.dailySummaryEnabled.first()) return Result.success()
        val now = ZonedDateTime.now()
        // A run the phone slept through (off at 9 PM, on next morning) would
        // recap the wrong day.
        if (now.hour < SUMMARY_HOUR) return Result.success()

        // Same rows as Home and the widgets: the selected profile, no ignored accounts.
        val today = now.toLocalDate()
        val ignored = ignoredAccountsStore.keys()
        val profileId = userPreferencesRepository.selectedProfileId.first()
        val profileKeys = buildProfileAccountKeys(accountBalanceRepository.getAllLatestBalances().first())
        fun counted(rows: List<TransactionEntity>) = filterTransactionsByProfile(
            rows.filterNot { IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountNumber) },
            profileId,
            profileKeys
        )
        val transactions = counted(transactionRepository.getTransactionsBetweenDates(today, today).first()).forTotals()
        // A lent loan forgiven today is spent today, as on Home (#938).
        val loss = loanRepository.settlementLossByCurrency(
            loanRepository.getLentLoansSettledInPeriod(today.atStartOfDay(), today.atTime(LocalTime.MAX)).first()
        ) { counted(listOf(it)).isNotEmpty() }
        val spent = daySpent(transactions, userPreferencesRepository.countCreditCardAsExpense.first(), loss)
            ?: return Result.success()
        val unsorted = transactions.filter { it.category == BuiltinCategoryNames.current("Others") }

        val context = applicationContext
        val text = listOfNotNull(
            transactions.size.takeIf { it > 0 }
                ?.let { context.resources.getQuantityString(R.plurals.notif_daily_count, it, it) },
            unsorted.size.takeIf { it > 0 }
                ?.let { context.resources.getQuantityString(R.plurals.notif_daily_uncategorized, it, it) }
        ).joinToString(" · ")
        val title = context.getString(
            R.string.notif_daily_title,
            CurrencyFormatter.formatByCurrency(spent, fallbackCurrency = userPreferencesRepository.baseCurrency.first())
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_daily_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_daily_channel_description) }
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            // Figures stay off the lock screen, like the transaction alerts (#734).
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        // The newest unsorted one opens in the category picker; picking it
        // teaches the merchant, which often sorts the rest too.
        unsorted.maxByOrNull { it.dateTime }?.let { latest ->
            builder.addAction(
                0,
                context.getString(R.string.notif_daily_action_sort),
                SmsBroadcastReceiver.pickerIntent(context, latest.id, NOTIFICATION_ID, requestCode = NOTIFICATION_ID)
            )
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_summary"
        private const val CHANNEL_ID = "daily_summary"
        // Transaction alerts use the transaction id; ids are never negative.
        private const val NOTIFICATION_ID = -2
        private const val SUMMARY_HOUR = 21

        /** The next local 9 PM strictly after [now]; zone-aware, so DST shifts keep it at 9 PM. */
        internal fun nextRun(now: ZonedDateTime): ZonedDateTime {
            val tonight = now.toLocalDate().atTime(SUMMARY_HOUR, 0).atZone(now.zone)
            return if (tonight.isAfter(now)) tonight else now.toLocalDate().plusDays(1).atTime(SUMMARY_HOUR, 0).atZone(now.zone)
        }

        /**
         * Today's "Spent" per currency, by Home's definition: expenses, card spend
         * only when opted in (#705), net of refunds and floored at zero, plus
         * loans forgiven today. Null for a day with nothing to report.
         */
        internal fun daySpent(
            rows: List<TransactionEntity>,
            creditAsExpense: Boolean,
            settlementLoss: Map<String, BigDecimal>
        ): Map<String, Money>? {
            if (rows.isEmpty() && settlementLoss.isEmpty()) return null
            val net = rows
                .mapNotNull { tx ->
                    when {
                        tx.transactionType == TransactionType.EXPENSE ||
                            (creditAsExpense && tx.transactionType == TransactionType.CREDIT) -> Money(tx.amount, tx.currency)
                        tx.transactionType == TransactionType.INCOME &&
                            tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT -> Money(tx.amount.negate(), tx.currency)
                        else -> null
                    }
                }
                .sumByCurrency({ it.currency }, { it.amount })
                .values.map { if (it.amount.signum() < 0) Money.zero(it.currency) else it }
            return (net + settlementLoss.map { (currency, amount) -> Money(amount, currency) })
                .sumByCurrency({ it.currency }, { it.amount })
        }

        // ponytail: WorkManager, not an exact alarm: under Doze the recap can land a little after 9 PM.
        fun schedule(context: Context) {
            val now = ZonedDateTime.now()
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun(now)))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
