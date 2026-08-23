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
import com.pennywiseai.tracker.domain.model.BudgetCycle
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            val (cycleStart, _) = BudgetCycle.currentCycle(now, startDay)
            val start = cycleStart.atStartOfDay()
            val end = LocalDateTime.now()

            val allTransactions = transactionRepository
                .getTransactionsBetweenDates(start, end)
                .first()

            // Loan disbursements/repayments are tracked in the Loans feature, not
            // spending — exclude them, matching Home/Analytics. Analytics-excluded
            // transactions are opted out of every spend figure by the user, so they
            // must not count toward the widget's spend total either.
            val nonLoan = allTransactions.filter { it.loanId == null && !it.excludedFromAnalytics }
            // Null = no rate for this pair; the spend folds skip it rather than
            // counting a face-value foreign amount (#670).
            suspend fun inTarget(tx: com.pennywiseai.tracker.data.database.entity.TransactionEntity): BigDecimal? =
                if (!tx.currency.equals(targetCurrency, ignoreCase = true)) {
                    currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, targetCurrency)
                } else {
                    tx.amount
                }

            val grossSpent = nonLoan
                .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT || it.transactionType == TransactionType.INVESTMENT }
                .fold(BigDecimal.ZERO) { acc, tx -> inTarget(tx)?.let { acc + it } ?: acc }

            // A "Refund" (INCOME + DEDUCT_SPENT) reverses a previous expense, so it
            // shrinks the spend total (floored at zero) — matching the Home card and
            // the Transactions page, which were already net of refunds.
            val refundTotal = nonLoan
                .filter { it.transactionType == TransactionType.INCOME && it.budgetImpactType == BudgetImpactType.DEDUCT_SPENT }
                .fold(BigDecimal.ZERO) { acc, tx -> inTarget(tx)?.let { acc + it } ?: acc }

            val totalSpent = (grossSpent - refundTotal).coerceAtLeast(BigDecimal.ZERO)

            val formatter = DateTimeFormatter.ofPattern("MMM d")

            val recentItems = allTransactions
                .take(MAX_ITEMS)
                .map { tx ->
                    // If a foreign txn has no rate, show it in its native currency
                    // rather than mislabeling a face value as targetCurrency (#670).
                    val converted = if (!tx.currency.equals(targetCurrency, ignoreCase = true)) {
                        currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, targetCurrency)
                    } else {
                        tx.amount
                    }
                    val amount = converted ?: tx.amount
                    val itemCurrency = if (converted != null) targetCurrency else tx.currency
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
                        currency = itemCurrency,
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
