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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.MonthlyBudgetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class BudgetWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
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

            val spending = if (isUnifiedMode) {
                // Get all-currencies spending and convert to display currency
                val raw = monthlyBudgetRepository.getMonthSpendingAllCurrencies(
                    java.time.LocalDate.now().year,
                    java.time.LocalDate.now().monthValue
                ).first()

                // Convert budget limit from base currency to display currency
                val convertedTotalLimit = currencyConversionService.convertAmount(
                    raw.totalLimit, baseCurrency, displayCurrency
                )

                // Aggregate expenses and income across currencies
                var totalSpent = java.math.BigDecimal.ZERO
                var totalIncome = java.math.BigDecimal.ZERO

                for (tx in raw.allTransactions) {
                    val converted = currencyConversionService.convertAmount(
                        tx.transaction.amount, tx.transaction.currency, displayCurrency
                    )
                    when (tx.transaction.transactionType) {
                        TransactionType.EXPENSE, TransactionType.CREDIT -> totalSpent += converted
                        TransactionType.INCOME -> totalIncome += converted
                        else -> totalSpent += converted
                    }
                }

                val remaining = convertedTotalLimit - totalSpent
                val percentageUsed = if (convertedTotalLimit > java.math.BigDecimal.ZERO) {
                    (totalSpent.toFloat() / convertedTotalLimit.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailyAllowance = if (raw.daysRemaining > 0 && remaining > java.math.BigDecimal.ZERO) {
                    remaining.divide(java.math.BigDecimal(raw.daysRemaining), 0, java.math.RoundingMode.HALF_UP)
                } else java.math.BigDecimal.ZERO
                val netSavings = totalIncome - totalSpent
                val savingsRate = if (totalIncome > java.math.BigDecimal.ZERO) {
                    (netSavings.toFloat() / totalIncome.toFloat() * 100f)
                } else 0f

                com.pennywiseai.tracker.data.repository.MonthlyBudgetSpending(
                    totalLimit = convertedTotalLimit,
                    totalSpent = totalSpent,
                    remaining = remaining,
                    percentageUsed = percentageUsed,
                    categorySpending = emptyList(),
                    daysRemaining = raw.daysRemaining,
                    dailyAllowance = dailyAllowance,
                    totalIncome = totalIncome,
                    netSavings = netSavings,
                    savingsRate = savingsRate,
                    savingsDelta = null
                )
            } else {
                monthlyBudgetRepository.getCurrentMonthSpending(currency).first()
            }

            val widgetData = BudgetWidgetData(
                totalSpent = spending.totalSpent,
                totalLimit = spending.totalLimit,
                remaining = spending.remaining,
                percentageUsed = spending.percentageUsed,
                dailyAllowance = spending.dailyAllowance,
                totalIncome = spending.totalIncome,
                netSavings = spending.netSavings,
                savingsRate = spending.savingsRate,
                savingsDelta = spending.savingsDelta,
                currency = currency
            )

            BudgetWidgetDataStore.update(applicationContext, widgetData)
            BudgetWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
