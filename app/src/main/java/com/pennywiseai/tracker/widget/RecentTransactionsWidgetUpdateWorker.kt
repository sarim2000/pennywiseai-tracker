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
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@HiltWorker
class RecentTransactionsWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "recent_transactions_widget_update"
        private const val WORK_NAME_PERIODIC = "recent_transactions_widget_update_periodic"
        private const val MAX_ITEMS = 10

        fun resolveTargetCurrency(isUnified: Boolean, displayCurrency: String, baseCurrency: String): String {
            return if (isUnified) displayCurrency else baseCurrency
        }

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecentTransactionsWidgetUpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecentTransactionsWidgetUpdateWorker>(
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
            val displayCurrency = userPreferencesRepository.displayCurrency.first()
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val targetCurrency = resolveTargetCurrency(isUnifiedMode, displayCurrency, baseCurrency)

            val now = LocalDate.now()
            val start = now.withDayOfMonth(1).atStartOfDay()
            val end = LocalDateTime.now()

            val allTransactions = transactionRepository
                .getTransactionsBetweenDates(start, end)
                .first()

            // Loan disbursements/repayments are tracked in the Loans feature, not
            // spending — exclude them, matching Home/Analytics.
            val nonLoan = allTransactions.filter { it.loanId == null }
            suspend fun inTarget(tx: com.pennywiseai.tracker.data.database.entity.TransactionEntity): BigDecimal =
                if (!tx.currency.equals(targetCurrency, ignoreCase = true)) {
                    currencyConversionService.convertAmount(tx.amount, tx.currency, targetCurrency)
                } else {
                    tx.amount
                }

            val grossSpent = nonLoan
                .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT || it.transactionType == TransactionType.INVESTMENT }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + inTarget(tx) }

            // A "Refund" (INCOME + DEDUCT_SPENT) reverses a previous expense, so it
            // shrinks the spend total (floored at zero) — matching the Home card and
            // the Transactions page, which were already net of refunds.
            val refundTotal = nonLoan
                .filter { it.transactionType == TransactionType.INCOME && it.budgetImpactType == BudgetImpactType.DEDUCT_SPENT }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + inTarget(tx) }

            val totalSpent = (grossSpent - refundTotal).coerceAtLeast(BigDecimal.ZERO)

            val formatter = DateTimeFormatter.ofPattern("MMM d")

            val recentItems = allTransactions
                .take(MAX_ITEMS)
                .map { tx ->
                    val amount = if (!tx.currency.equals(targetCurrency, ignoreCase = true)) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, targetCurrency)
                    } else {
                        tx.amount
                    }
                    val title = tx.merchantName.takeIf { it.isNotBlank() }
                        ?: tx.description?.takeIf { it.isNotBlank() }
                        ?: "Transaction"
                    val dateText = tx.dateTime.toLocalDate().format(formatter)
                    val subtitle = tx.category
                        .takeIf { it.isNotBlank() }
                        ?.let { "$it • $dateText" }
                        ?: dateText

                    RecentTransactionItem(
                        title = title,
                        subtitle = subtitle,
                        amount = amount,
                        currency = targetCurrency,
                        transactionType = tx.transactionType
                    )
                }

            val data = RecentTransactionsWidgetData(
                totalSpent = totalSpent,
                currency = targetCurrency,
                transactions = recentItems
            )

            RecentTransactionsWidgetDataStore.update(applicationContext, data)
            RecentTransactionsWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
