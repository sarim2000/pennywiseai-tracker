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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
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

                // Convert raw to summary with currency conversion
                val incomeTransactions = raw.allTransactions.filter {
                    it.transaction.transactionType == TransactionType.INCOME
                }
                var totalIncome = java.math.BigDecimal.ZERO
                for (tx in incomeTransactions) {
                    totalIncome += currencyConversionService.convertAmount(
                        tx.transaction.amount, tx.transaction.currency, displayCurrency
                    )
                }

                val categoryAmounts = mutableMapOf<String, java.math.BigDecimal>()
                raw.allTransactions.forEach { txWithSplits ->
                    if (txWithSplits.transaction.transactionType != TransactionType.INCOME) {
                        txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                            val converted = currencyConversionService.convertAmount(amount, txWithSplits.transaction.currency, displayCurrency)
                            val categoryName = category.ifEmpty { "Others" }
                            categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: java.math.BigDecimal.ZERO) + converted
                        }
                    }
                }

                val groupSpendingList = raw.budgetsWithCategories.map { group ->
                    val catSpending = group.categories.map { cat ->
                        val actual = categoryAmounts[cat.categoryName] ?: java.math.BigDecimal.ZERO
                        val convertedBudget = currencyConversionService.convertAmount(cat.budgetAmount, baseCurrency, displayCurrency)
                        BudgetCategorySpending(cat.categoryName, convertedBudget, actual, 0f, java.math.BigDecimal.ZERO)
                    }
                    val totalBudget = catSpending.fold(java.math.BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
                    val totalActual = catSpending.fold(java.math.BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
                    BudgetGroupSpending(group, catSpending, totalBudget, totalActual, totalBudget - totalActual, 0f, java.math.BigDecimal.ZERO, raw.daysRemaining, raw.daysElapsed)
                }

                val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
                val totalLimitBudget = limitGroups.fold(java.math.BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
                val totalLimitSpent = limitGroups.fold(java.math.BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
                val limitRemaining = totalLimitBudget - totalLimitSpent
                val pctUsed = if (totalLimitBudget > java.math.BigDecimal.ZERO) {
                    (totalLimitSpent.toFloat() / totalLimitBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailyAllowance = if (raw.daysRemaining > 0 && limitRemaining > java.math.BigDecimal.ZERO) {
                    limitRemaining.divide(java.math.BigDecimal(raw.daysRemaining), 0, java.math.RoundingMode.HALF_UP)
                } else java.math.BigDecimal.ZERO
                val netSavings = totalIncome - totalLimitSpent
                val savingsRate = if (totalIncome > java.math.BigDecimal.ZERO) {
                    (netSavings.toFloat() / totalIncome.toFloat() * 100f)
                } else 0f

                // Calculate previous month savings for delta
                val limitCategoryNames = raw.budgetsWithCategories
                    .filter { it.budget.groupType == BudgetGroupType.LIMIT }
                    .flatMap { it.categories }
                    .map { it.categoryName }
                    .toSet()

                val prevCategoryAmounts = mutableMapOf<String, java.math.BigDecimal>()
                raw.prevTransactions.forEach { txWithSplits ->
                    if (txWithSplits.transaction.transactionType != TransactionType.INCOME) {
                        txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                            val converted = currencyConversionService.convertAmount(
                                amount, txWithSplits.transaction.currency, displayCurrency
                            )
                            val categoryName = category.ifEmpty { "Others" }
                            prevCategoryAmounts[categoryName] =
                                (prevCategoryAmounts[categoryName] ?: java.math.BigDecimal.ZERO) + converted
                        }
                    }
                }

                val prevLimitSpent = limitCategoryNames.fold(java.math.BigDecimal.ZERO) { acc, catName ->
                    acc + (prevCategoryAmounts[catName] ?: java.math.BigDecimal.ZERO)
                }

                var prevIncome = java.math.BigDecimal.ZERO
                raw.prevTransactions
                    .filter { it.transaction.transactionType == TransactionType.INCOME }
                    .forEach { tx ->
                        prevIncome += currencyConversionService.convertAmount(
                            tx.transaction.amount, tx.transaction.currency, displayCurrency
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
