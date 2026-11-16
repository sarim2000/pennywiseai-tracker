package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.QuickAddIntent
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.insights.InsightsLoader
import com.pennywiseai.tracker.domain.insights.InsightsSnapshot
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import com.pennywiseai.tracker.receiver.QuickCategoryPickerActivity
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import com.pennywiseai.tracker.utils.withLatinDigits
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val insightsLoader: InsightsLoader
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
        // recap the wrong day. (The debug-only DailySummaryProbeReceiver forces a run.)
        if (now.hour < SUMMARY_HOUR && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()

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
        // The Budgets screen's own figures, as the budget alerts use; a rate fetch
        // that fails mustn't cost the user the whole recap. Budgets span every
        // profile, so with one profile selected they'd sit beside spending they
        // don't match: leave them out then, as goal figures are (roadmap Decision 3).
        val budgetSummary = if (profileId != null) {
            null
        } else {
            runCatching { budgetSummaryProvider.summary(YearMonth.from(today), flagUncounted = true).first() }
                .onFailure {
                    // A stopped worker must stop, not carry on without budgets.
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Budget figures unavailable for the recap", it)
                }
                .getOrNull()
        }

        // Something needing attention is worth the tap: open Insights rather than Home.
        val opensInsights = opensInsights(
            runCatching { insightsLoader.observe(today).first() }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Insights unavailable for the recap", it)
                }
                .getOrNull()
        )

        // The recap is built outside any Activity, so it needs the Latin-digit context too (14b00ada).
        val context = applicationContext.withLatinDigits()
        val budgetLines = budgetSummary?.let { summary ->
            budgetSegments(
                budgetsLeft(summary, today),
                line = { budget ->
                    context.getString(
                        if (budget.left.signum() < 0) R.string.notif_daily_budget_over else R.string.notif_daily_budget_left,
                        CurrencyFormatter.formatCurrency(budget.left.abs(), summary.currency, maskIfHidden = false),
                        budget.name
                    )
                },
                incompleteNote = context.getString(R.string.notif_daily_budget_incomplete),
                more = { context.resources.getQuantityString(R.plurals.notif_daily_budget_more, it, it) }
            )
        }.orEmpty()
        val text = listOfNotNull(
            transactions.size.takeIf { it > 0 }
                ?.let { context.resources.getQuantityString(R.plurals.notif_daily_count, it, it) },
            *budgetLines.toTypedArray(),
            // Unsorted transfers count as spending until filed, so say why sorting matters.
            unsorted.size.takeIf { it > 0 }
                ?.let {
                    context.resources.getQuantityString(R.plurals.notif_daily_uncategorized, it, it) + " — " +
                        context.resources.getQuantityString(R.plurals.notif_daily_sort_why, it)
                }
        ).joinToString(" · ")
        val title = context.getString(
            R.string.notif_daily_title,
            CurrencyFormatter.formatByCurrency(spent, fallbackCurrency = userPreferencesRepository.baseCurrency.first(), maskIfHidden = false)
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .apply { if (opensInsights) putExtra(MainActivity.EXTRA_OPEN_INSIGHTS, true) },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            // Figures stay off the lock screen, like the transaction alerts (#734).
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        // The picker walks the unsorted ones, newest first; each pick teaches the
        // merchant, which often sorts the rest too. Its "Saved as" notes take each
        // transaction's own alert id, so the recap stays until the walk ends.
        if (unsorted.isNotEmpty()) {
            builder.addAction(
                0,
                context.getString(if (unsorted.size > 1) R.string.notif_daily_action_sort_all else R.string.notif_daily_action_sort),
                QuickCategoryPickerActivity.walkIntent(
                    context,
                    unsorted.sortedByDescending { it.dateTime }.map { it.id },
                    requestCode = NOTIFICATION_ID
                )
            )
        }
        // Cash never texts: the recap is when the day's cash spend gets remembered.
        builder.addAction(
            0,
            context.getString(R.string.notif_daily_action_cash),
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                QuickAddIntent.create(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        return Result.success()
    }

    /** One LIMIT budget's line in the recap: what's [left] (negative: over) of its current window. */
    internal data class BudgetLeft(val name: String, val left: BigDecimal, val incomplete: Boolean)

    companion object {
        private const val TAG = "DailySummaryWorker"
        private const val WORK_NAME = "daily_summary"
        private const val CHANNEL_ID = "daily_summary"
        // Transaction alerts use the transaction id; ids are never negative.
        internal const val NOTIFICATION_ID = -2
        /** Input flag: skip the 9 PM gate (debug probe only). */
        const val KEY_FORCE = "force"
        private const val SUMMARY_HOUR = 21
        private const val MAX_BUDGET_LINES = 2

        /** A tap opens Insights while at least one alert there hasn't been dismissed. */
        internal fun opensInsights(snapshot: InsightsSnapshot?): Boolean =
            snapshot?.active?.any { it.severity == InsightSeverity.ALERT } == true

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

        /**
         * Each LIMIT budget running today, in the summary's one currency: a
         * budget's figures already come resolved and converted per budget (one
         * entry each), so its allowance is never repeated per spending currency.
         * A budget whose window isn't today's (a one-time budget that ended) or
         * with no limit is left out. A limit set to zero with spending is over by
         * all of it, so it stays. Tightest first: the least left for its limit,
         * a zero limit before any other.
         */
        internal fun budgetsLeft(summary: BudgetOverallSummary, today: LocalDate): List<BudgetLeft> =
            summary.groups
                .filter {
                    it.group.budget.groupType == BudgetGroupType.LIMIT &&
                        (it.hasLimit || (it.hasZeroLimit() && it.remaining.signum() < 0)) &&
                        !today.isBefore(it.windowStart) && !today.isAfter(it.windowEnd)
                }
                // A limit rollover carried down to 0 is measured against the budget's own;
                // one the user set to 0 has nothing to measure against, so it leads.
                .sortedWith(
                    compareBy(nullsFirst()) {
                        (it.totalBudget.takeIf { t -> t.signum() > 0 } ?: it.baseBudget.takeIf { b -> b.signum() > 0 })
                            ?.let { limit -> it.remaining.divide(limit, MathContext.DECIMAL64) }
                    }
                )
                .map { BudgetLeft(it.group.budget.name, it.remaining, it.hasUnconvertedSpend) }

        /**
         * The user set this limit to zero: the stored limit and every category's
         * amount are zero. A zero [BudgetGroupSpending.totalBudget] alone isn't
         * enough, as the unified figures turn a limit with no rate into zero too.
         */
        private fun BudgetGroupSpending.hasZeroLimit(): Boolean =
            group.budget.limitAmount.signum() == 0 && group.categories.all { it.budgetAmount.signum() == 0 }

        /**
         * The recap's budget segments: only the tightest [MAX_BUDGET_LINES] budgets,
         * so it stays short, then one [incompleteNote] if any of those figures
         * leaves spending out (once, not after every line), then a count of the rest.
         */
        internal fun budgetSegments(
            budgets: List<BudgetLeft>,
            line: (BudgetLeft) -> String,
            incompleteNote: String,
            more: (Int) -> String
        ): List<String> {
            val shown = budgets.take(MAX_BUDGET_LINES)
            return shown.map(line) +
                listOfNotNull(
                    incompleteNote.takeIf { shown.any { it.incomplete } },
                    (budgets.size - shown.size).takeIf { it > 0 }?.let(more)
                )
        }

        /**
         * Schedules the recap while [enabled] (KEEP: an existing schedule stands)
         * and cancels it otherwise. Called on app start and from the Settings switch.
         */
        // ponytail: WorkManager, not an exact alarm: under Doze the recap can land a little after 9 PM.
        fun sync(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = ZonedDateTime.now()
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun(now)))
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
