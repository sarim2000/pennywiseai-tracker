package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetCarry
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.repository.budgetPercentUsed
import com.pennywiseai.tracker.data.repository.budgetSpend
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.spending.budgetPace
import com.pennywiseai.tracker.presentation.home.heroBudget
import com.pennywiseai.tracker.presentation.home.homeBudgetSummary
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@HiltWorker
class BudgetWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "budget_widget_update"
        private const val WORK_NAME_PERIODIC = "budget_widget_update_periodic"

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<BudgetWidgetUpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<BudgetWidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val isUnifiedMode = userPreferencesRepository.unifiedCurrencyMode.first()
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val displayCurrency = userPreferencesRepository.displayCurrency.first()
            val currency = if (isUnifiedMode) displayCurrency else baseCurrency

            val today = java.time.LocalDate.now()
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            // Use today's calendar month, not the cycle's start month —
            // same fix as the home viewmodel. For a user on startDay=25
            // viewing today=Jul 1, cycleStartYm was June, which made
            // isCurrentMonth=false in the repo and the widget treated
            // the current cycle as historical (daysRemaining=0, no
            // daily allowance). The per-budget current window is
            // resolved by resolveBudgetWindow inside the repo
            // regardless of which calendar month the page is on.
            val todayYm = java.time.YearMonth.of(today.year, today.monthValue)
            val prevCycle = BudgetCycle.previousCycle(
                BudgetCycle.currentCycle(today, startDay), startDay
            )
            // Use the end date of the previous cycle to derive the display month.
            // Using prevCycle.first is wrong for non-default start days: e.g.
            // startDay=25 → prevCycle = May 25–Jun 24, first = May, but
            // getGroupSpending(May) resolves to Apr 25–May 24 (two cycles ago).
            // prevCycle.second = Jun 24 → June → getGroupSpending(June) correctly
            // resolves to May 25–Jun 24.
            val prevCycleStartYm = java.time.YearMonth.from(prevCycle.second)

            val summary = if (isUnifiedMode) {
                val raw = budgetGroupRepository.getGroupSpendingAllCurrencies(
                    todayYm.year, todayYm.monthValue
                ).first()

                // Reuse the same aggregator the Budgets screen uses so the widget
                // applies Refund (DEDUCT_SPENT) and Extra budget (ADD_TO_LIMIT)
                // identically. Refunds shrink categoryAmounts (floored at zero) and
                // are excluded from totalIncome; Extra budget bumps the displayed
                // category budget via categoryLimitBoosts and stays in income.
                // Null = no rate for this pair; the aggregator leaves the row out
                // rather than counting a face-value foreign amount (#670).
                val convertSplit: suspend (String, BigDecimal) -> BigDecimal? =
                    { fromCurrency, amount ->
                        currencyConversionService.convertAmountOrNull(amount, fromCurrency, displayCurrency)
                    }

                var totalIncome = BigDecimal.ZERO
                for (txWithSplits in raw.allTransactions) {
                    val tx = txWithSplits.transaction
                    if (tx.transactionType != TransactionType.INCOME) continue
                    // Only exclude a Refund from income when it's actually being
                    // deducted from a category by aggregateBudgetCategorySpending
                    // (i.e. budgetCategory is set). An "orphaned" DEDUCT_SPENT
                    // with no category isn't subtracted from spend, so dropping it
                    // from income too would understate netSavings.
                    if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
                        tx.budgetCategory != null
                    ) continue
                    // Skip unconvertible income so it doesn't inflate netSavings
                    // at face value (#670).
                    totalIncome += currencyConversionService.convertAmountOrNull(
                        tx.amount, tx.currency, displayCurrency
                    ) ?: continue
                }

                // Each budget's carry, worked out once for the figures and the owl's pace.
                val carries = raw.budgetsWithCategories.associate { group ->
                    group.budget.id to raw.carryFor(group, displayCurrency, baseCurrency, convertSplit)
                }
                val convertLimit: suspend (BigDecimal) -> BigDecimal =
                    { amount -> currencyConversionService.convertAmount(amount, baseCurrency, displayCurrency) }
                val groupSpendingList = unifiedGroupSpending(raw, carries, convertSplit, convertLimit, ownWindows = false)

                val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
                val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }

                // Previous month savings for delta — same aggregator, same currency
                // converters, so the delta isn't skewed by stale logic on either side.
                val (prevCategoryAmounts, _, prevTypeAmounts) = aggregateBudgetCategorySpending(
                    transactions = raw.prevCycleTransactions,
                    convertSplit = convertSplit,
                    convertIncome = { tx ->
                        currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, displayCurrency)
                    }
                )

                // Split LIMIT-group buckets into category vs type buckets so the
                // previous-month spend picks up type-bucket amounts too (they live
                // in prevTypeAmounts, not prevCategoryAmounts).
                val limitBuckets = raw.budgetsWithCategories
                    .filter { it.budget.groupType == BudgetGroupType.LIMIT }
                    .flatMap { it.categories }
                val limitCategoryNames = limitBuckets.filter { it.matchType == null }
                    .map { it.categoryName }.toSet()
                val limitMatchTypes = limitBuckets.mapNotNull { it.matchType }.toSet()

                val prevLimitSpent = limitCategoryNames.fold(BigDecimal.ZERO) { acc, catName ->
                    acc + (prevCategoryAmounts[catName] ?: BigDecimal.ZERO)
                } + limitMatchTypes.fold(BigDecimal.ZERO) { acc, t ->
                    acc + (prevTypeAmounts[t] ?: BigDecimal.ZERO)
                }

                var prevIncome = BigDecimal.ZERO
                for (txWithSplits in raw.prevCycleTransactions) {
                    val tx = txWithSplits.transaction
                    if (tx.transactionType != TransactionType.INCOME) continue
                    if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
                        tx.budgetCategory != null
                    ) continue
                    prevIncome += currencyConversionService.convertAmountOrNull(
                        tx.amount, tx.currency, displayCurrency
                    ) ?: continue
                }

                // The budget figures are Home's, so the widget shows what Home's hero does.
                widgetBudgetFigures(
                    homeBudgetSummary(raw, displayCurrency, baseCurrency) { amount, from, to ->
                        currencyConversionService.convertAmountOrNull(amount, from, to)
                    },
                    today,
                    pageDaysRemaining = raw.daysRemaining,
                    income = totalIncome,
                    incomeCurrency = displayCurrency,
                    pageLimitSpent = totalLimitSpent,
                    prevSavings = prevIncome - prevLimitSpent
                ).copy(
                    currency = currency,
                    // The owl paces each budget on its own window's spending, as its Budgets card does.
                    overPace = widgetOverPace(
                        today,
                        unifiedGroupSpending(raw, carries, convertSplit, convertLimit, ownWindows = true)
                    )
                )
            } else {
                val spending = budgetGroupRepository.getGroupSpending(
                    todayYm.year, todayYm.monthValue, currency
                ).first()

                // Get previous month spending for delta calculation
                val prevSpending = budgetGroupRepository.getGroupSpending(
                    prevCycleStartYm.year, prevCycleStartYm.monthValue, currency
                ).first()

                // The same summary Home reads in this mode.
                widgetBudgetFigures(
                    spending,
                    today,
                    pageDaysRemaining = spending.daysRemaining,
                    income = spending.totalIncome,
                    incomeCurrency = currency,
                    pageLimitSpent = spending.totalLimitSpent,
                    prevSavings = prevSpending.netSavings
                ).copy(
                    currency = currency,
                    overPace = widgetOverPace(today, spending.groups)
                )
            }

            BudgetWidgetDataStore.update(applicationContext, summary)
            BudgetWidget().updateAll(applicationContext)
            // The summary widget leads with what's left of this budget.
            SpendingSummaryWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * The widget's figures. When Home's hero leads with a budget (an all-spending limit
 * running [today]), the widget shows that budget alone: it already covers what a
 * category budget spends, so adding the two counts that spending twice (#1420). The
 * limit, spend and per-day figure are that budget's, and Saved / Overspent is [income]
 * less its spend, with no arrow: [prevSavings] is last period's income less every
 * limit's spend, not this budget's. Its spend only stands in when the summary is in
 * [incomeCurrency]; otherwise savings fall back as below.
 *
 * With no such budget, the page's limits are added up, as before: the per-day figure is
 * what's left over [pageDaysRemaining] (the page window's days left, not one budget's),
 * and Saved / Overspent is [income] less [pageLimitSpent], its arrow against [prevSavings].
 */
internal fun widgetBudgetFigures(
    summary: BudgetOverallSummary,
    today: LocalDate,
    pageDaysRemaining: Int,
    income: BigDecimal,
    incomeCurrency: String,
    pageLimitSpent: BigDecimal,
    prevSavings: BigDecimal
): BudgetWidgetData {
    val hero = summary.heroBudget(today)?.let { h -> summary.groups.first { it.group.budget.id == h.budgetId } }
    val limit = hero?.totalBudget ?: summary.totalLimitBudget
    val spent = hero?.totalActual ?: summary.totalLimitSpent
    val remaining = limit - spent
    val usage = widgetLimitUsage(limit, spent, hero?.let { listOf(it) } ?: summary.groups)
    val dailyAllowance = hero?.dailyAllowance ?: if (pageDaysRemaining > 0 && remaining > BigDecimal.ZERO) {
        remaining.divide(BigDecimal(pageDaysRemaining), 0, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO
    val savingsHero = hero?.takeIf { summary.currency == incomeCurrency }
    val netSavings = income - (savingsHero?.totalActual ?: pageLimitSpent)
    return BudgetWidgetData(
        totalSpent = spent,
        totalLimit = limit,
        hasLimit = usage.hasLimit,
        remaining = remaining,
        percentageUsed = usage.percentageUsed,
        dailyAllowance = dailyAllowance,
        totalIncome = income,
        netSavings = netSavings,
        savingsRate = if (income > BigDecimal.ZERO) netSavings.toFloat() / income.toFloat() * 100f else 0f,
        savingsDelta = if (savingsHero != null) null else netSavings - prevSavings,
        currency = summary.currency
    )
}

internal data class WidgetLimitUsage(val percentageUsed: Float, val hasLimit: Boolean)

/**
 * How much of the page's spending limits ([budget] with every carry in it, [spent]:
 * the totals the widget shows) is used. Rollover can carry every limit down to 0:
 * that's used up, not "no budget". [groups] give the carries; a budget left out of
 * the totals (a one-time budget that already ended) has no window and carries nothing.
 */
internal fun widgetLimitUsage(budget: BigDecimal, spent: BigDecimal, groups: List<BudgetGroupSpending>): WidgetLimitUsage {
    val carry = groups.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        .fold(BigDecimal.ZERO) { acc, g -> acc + g.carry }
    return WidgetLimitUsage(budgetPercentUsed(spent, budget, carry), (budget - carry).signum() > 0)
}

/**
 * Whether any limit running [today] is a day's budget past an even spend over its own
 * window, as its Budgets card paces it. One budget over pace isn't hidden by another
 * under it. Null when no limit is running.
 */
internal fun widgetOverPace(today: LocalDate, groups: List<BudgetGroupSpending>): Boolean? {
    val paces = groups.mapNotNull { g ->
        if (g.group.budget.groupType != BudgetGroupType.LIMIT || today !in g.windowStart..g.windowEnd) return@mapNotNull null
        budgetPace(g.totalActual, g.totalBudget, ChronoUnit.DAYS.between(g.windowStart, today).toInt() + 1, g.windowDays)
    }
    return if (paces.isEmpty()) null else paces.any { it.daysOver > 0 }
}

/**
 * Unified mode: each budget's figures in the display currency, refunds and Extra budget
 * applied by the same aggregator as the Budgets screen. A rollover budget counts its own
 * current window, as on Budgets: its spending and its Extra budget, so the carry isn't
 * set against a month of a weekly budget's figures. [ownWindows] counts every budget
 * that way, for the owl's pace; without it the others count every transaction on the
 * page, as the widget's savings figure does. [carries] holds every budget's
 * carry; [convertLimit] puts a stored (base-currency) limit into the display currency.
 */
internal suspend fun unifiedGroupSpending(
    raw: BudgetGroupSpendingRaw,
    carries: Map<Long, BudgetCarry>,
    convertSplit: suspend (String, BigDecimal) -> BigDecimal?,
    convertLimit: suspend (BigDecimal) -> BigDecimal,
    ownWindows: Boolean
): List<BudgetGroupSpending> {
    suspend fun aggregate(transactions: List<TransactionWithSplits>) = aggregateBudgetCategorySpending(
        transactions = transactions,
        convertSplit = convertSplit,
        convertIncome = { tx -> convertSplit(tx.currency, tx.amount) }
    )
    val page = if (ownWindows) null else aggregate(raw.allTransactions)
    return raw.budgetsWithCategories.map { group ->
        val isTrackingAll = group.categories.isEmpty()
        val window = raw.currentWindows[group.budget.id]
        val windowTransactions = raw.currentWindowTransactions(group.budget.id).orEmpty()
        val counted = if (page == null) windowTransactions else raw.allTransactions
        val rolloverSpend = if (group.budget.rolloverEnabled) {
            budgetSpend(group, windowTransactions, raw.parentOf, convertSplit)
        } else null
        val (categoryAmounts, categoryLimitBoosts, typeAmounts) = page ?: aggregate(windowTransactions)
        val catSpending = group.categories.map { cat ->
            val actual = if (cat.matchType != null) {
                typeAmounts[cat.matchType] ?: BigDecimal.ZERO
            } else {
                categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
            }
            val boost = if (cat.matchType != null || rolloverSpend != null) BigDecimal.ZERO
            else categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO
            BudgetCategorySpending(cat.categoryName, convertLimit(cat.budgetAmount) + boost, actual, 0f, BigDecimal.ZERO)
        }
        val convertedGroupLimit = convertLimit(group.budget.limitAmount)
        // "Category Limits" are optional — the group-level limit is the source of truth
        // when set (including when isTrackingAll), with the per-cat sum as a fallback for
        // budgets that only define per-cat amounts. Rollover: the same carry the Budgets
        // screen and Home add.
        val carry = carries.getValue(group.budget.id)
        val totalBudget = carry.appliedTo(
            if (convertedGroupLimit > BigDecimal.ZERO) {
                convertedGroupLimit
            } else {
                catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount } + (rolloverSpend?.boost ?: BigDecimal.ZERO)
            }
        )
        val totalActual = when {
            rolloverSpend != null -> rolloverSpend.amount
            // As on the Budgets screen and Home: investments left out.
            isTrackingAll -> budgetSpend(group, counted, raw.parentOf, convertSplit).amount
            else -> catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
        }
        BudgetGroupSpending(
            group,
            if (isTrackingAll) emptyList() else catSpending,
            totalBudget,
            totalActual,
            totalBudget - totalActual,
            0f,
            BigDecimal.ZERO,
            raw.daysRemaining,
            raw.daysElapsed,
            isTrackingAllExpenses = isTrackingAll,
            // The budget's own period, for the owl's pace: not the page's window.
            windowStart = window?.start ?: raw.today,
            windowEnd = window?.end ?: raw.today,
            windowDays = window?.days ?: 0,
            carry = carry.amount.amount,
            carryGap = carry.gap
        )
    }
}
