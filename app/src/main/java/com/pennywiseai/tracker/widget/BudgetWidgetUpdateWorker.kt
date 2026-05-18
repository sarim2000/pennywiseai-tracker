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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository.Companion.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import java.math.BigDecimal
import java.math.RoundingMode
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
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

            val summary = if (isUnifiedMode) {
                val raw = budgetGroupRepository.getGroupSpendingAllCurrencies(
                    today.year, today.monthValue
                ).first()

                // Reuse the same aggregator the Budgets screen uses so the widget
                // applies Refund (DEDUCT_SPENT) and Extra budget (ADD_TO_LIMIT)
                // identically. Refunds shrink categoryAmounts (floored at zero) and
                // are excluded from totalIncome; Extra budget bumps the displayed
                // category budget via categoryLimitBoosts and stays in income.
                val convertSplit: suspend (String, BigDecimal) -> BigDecimal =
                    { fromCurrency, amount ->
                        currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
                    }
                val (categoryAmounts, categoryLimitBoosts) = aggregateBudgetCategorySpending(
                    transactions = raw.allTransactions,
                    convertSplit = convertSplit,
                    convertIncome = { tx ->
                        currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                    }
                )

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
                    totalIncome += currencyConversionService.convertAmount(
                        tx.amount, tx.currency, displayCurrency
                    )
                }

                val groupSpendingList = raw.budgetsWithCategories.map { group ->
                    val isTrackingAll = group.categories.isEmpty()
                    val catSpending = group.categories.map { cat ->
                        val actual = categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                        val convertedBudget = currencyConversionService.convertAmount(cat.budgetAmount, baseCurrency, displayCurrency)
                        val effectiveBudget = convertedBudget +
                            (categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO)
                        BudgetCategorySpending(cat.categoryName, effectiveBudget, actual, 0f, BigDecimal.ZERO)
                    }
                    val convertedGroupLimit = currencyConversionService.convertAmount(
                        group.budget.limitAmount, baseCurrency, displayCurrency
                    )
                    // "Category Limits" are optional — the group-level limit
                    // is the source of truth when set (including when
                    // isTrackingAll), with the per-cat sum as a fallback for
                    // budgets that only define per-cat amounts.
                    val totalBudget = if (convertedGroupLimit > BigDecimal.ZERO) {
                        convertedGroupLimit
                    } else {
                        catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
                    }
                    val totalActual = if (isTrackingAll) {
                        categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                    } else {
                        catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
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
                        isTrackingAllExpenses = isTrackingAll
                    )
                }

                val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
                val totalLimitBudget = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
                val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
                val limitRemaining = totalLimitBudget - totalLimitSpent
                val pctUsed = if (totalLimitBudget > BigDecimal.ZERO) {
                    (totalLimitSpent.toFloat() / totalLimitBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailyAllowance = if (raw.daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
                    limitRemaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val netSavings = totalIncome - totalLimitSpent
                val savingsRate = if (totalIncome > BigDecimal.ZERO) {
                    (netSavings.toFloat() / totalIncome.toFloat() * 100f)
                } else 0f

                // Previous month savings for delta — same aggregator, same currency
                // converters, so the delta isn't skewed by stale logic on either side.
                val (prevCategoryAmounts, _) = aggregateBudgetCategorySpending(
                    transactions = raw.prevTransactions,
                    convertSplit = convertSplit,
                    convertIncome = { tx ->
                        currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                    }
                )

                val limitCategoryNames = raw.budgetsWithCategories
                    .filter { it.budget.groupType == BudgetGroupType.LIMIT }
                    .flatMap { it.categories }
                    .map { it.categoryName }
                    .toSet()

                val prevLimitSpent = limitCategoryNames.fold(BigDecimal.ZERO) { acc, catName ->
                    acc + (prevCategoryAmounts[catName] ?: BigDecimal.ZERO)
                }

                var prevIncome = BigDecimal.ZERO
                for (txWithSplits in raw.prevTransactions) {
                    val tx = txWithSplits.transaction
                    if (tx.transactionType != TransactionType.INCOME) continue
                    if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
                        tx.budgetCategory != null
                    ) continue
                    prevIncome += currencyConversionService.convertAmount(
                        tx.amount, tx.currency, displayCurrency
                    )
                }

                val prevSavings = prevIncome - prevLimitSpent
                val savingsDelta = netSavings - prevSavings

                BudgetWidgetData(
                    totalSpent = totalLimitSpent,
                    totalLimit = totalLimitBudget,
                    remaining = limitRemaining,
                    percentageUsed = pctUsed,
                    dailyAllowance = dailyAllowance,
                    totalIncome = totalIncome,
                    netSavings = netSavings,
                    savingsRate = savingsRate,
                    savingsDelta = savingsDelta,
                    currency = currency
                )
            } else {
                val spending = budgetGroupRepository.getGroupSpending(
                    today.year, today.monthValue, currency
                ).first()

                // Get previous month spending for delta calculation
                val prevYearMonth = java.time.YearMonth.of(today.year, today.monthValue).minusMonths(1)
                val prevSpending = budgetGroupRepository.getGroupSpending(
                    prevYearMonth.year, prevYearMonth.monthValue, currency
                ).first()

                val savingsDelta = spending.netSavings - prevSpending.netSavings

                val limitRemaining = spending.totalLimitBudget - spending.totalLimitSpent
                val pctUsed = if (spending.totalLimitBudget > java.math.BigDecimal.ZERO) {
                    (spending.totalLimitSpent.toFloat() / spending.totalLimitBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f

                BudgetWidgetData(
                    totalSpent = spending.totalLimitSpent,
                    totalLimit = spending.totalLimitBudget,
                    remaining = limitRemaining,
                    percentageUsed = pctUsed,
                    dailyAllowance = spending.dailyAllowance,
                    totalIncome = spending.totalIncome,
                    netSavings = spending.netSavings,
                    savingsRate = spending.savingsRate,
                    savingsDelta = savingsDelta,
                    currency = currency
                )
            }

            BudgetWidgetDataStore.update(applicationContext, summary)
            BudgetWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
