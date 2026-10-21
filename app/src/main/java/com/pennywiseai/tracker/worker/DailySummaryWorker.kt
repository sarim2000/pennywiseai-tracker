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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * The evening recap: what today cost and how many transactions still need a
 * category, so a user who never opened the app still sees where the day went.
 * A day with no transactions stays quiet.
 */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!userPreferencesRepository.dailySummaryEnabled.first()) return Result.success()

        // Same rows as the widgets: the selected profile, no ignored accounts.
        val today = LocalDate.now()
        val ignored = ignoredAccountsStore.keys()
        val transactions = filterTransactionsByProfile(
            transactionRepository.getTransactionsBetweenDates(today, today).first()
                .filterNot { IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountNumber) },
            userPreferencesRepository.selectedProfileId.first(),
            buildProfileAccountKeys(accountBalanceRepository.getAllLatestBalances().first())
        ).forTotals()
        if (transactions.isEmpty()) return Result.success()

        // Same spend definition as Home and the widgets: card spend only when
        // opted in (#705), net of refunds, floored at zero — per currency.
        val creditAsExpense = userPreferencesRepository.countCreditCardAsExpense.first()
        val spent = transactions
            .mapNotNull { tx ->
                when {
                    tx.transactionType == TransactionType.EXPENSE ||
                        (creditAsExpense && tx.transactionType == TransactionType.CREDIT) -> tx to tx.amount
                    tx.transactionType == TransactionType.INCOME &&
                        tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT -> tx to tx.amount.negate()
                    else -> null
                }
            }
            .sumByCurrency({ it.first.currency }, { it.second })
            .mapValues { (currency, total) -> if (total.amount.signum() < 0) Money.zero(currency) else total }
        val unsorted = transactions.filter { it.category == BuiltinCategoryNames.current("Others") }

        val context = applicationContext
        val text = listOfNotNull(
            context.resources.getQuantityString(R.plurals.notif_daily_count, transactions.size, transactions.size),
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

        // ponytail: a 24h periodic job drifts under Doze; switch to an exact alarm if 9 PM sharp ever matters.
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            val tonight = now.toLocalDate().atTime(SUMMARY_HOUR, 0)
            val next = if (tonight.isAfter(now)) tonight else tonight.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
