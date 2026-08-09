package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
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
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Prepares the category pie widget's snapshot (#665): this cycle's EXPENSE
 * transactions grouped by category, top slices plus an aggregated "Other".
 *
 * Currency: a pie mixing currencies is meaningless, so either everything is
 * converted into the display currency (unified mode, same as the other
 * widgets) or the cycle's dominant spend currency is shown and the rest are
 * left out — the label always says which currency the pie is in.
 */
@HiltWorker
class CategoryPieWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "category_pie_widget_update"
        private const val WORK_NAME_PERIODIC = "category_pie_widget_update_periodic"
        private const val MAX_SLICES = 5

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<CategoryPieWidgetUpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<CategoryPieWidgetUpdateWorker>(
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

            val now = LocalDate.now()
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            val (cycleStart, _) = BudgetCycle.currentCycle(now, startDay)

            val transactions = transactionRepository
                .getTransactionsBetweenDates(cycleStart.atStartOfDay(), LocalDateTime.now())
                .first()
                // Same exclusions as Home/Analytics/other widgets: loan-linked
                // rows are the Loans feature's, excluded rows are the user's call.
                .filter {
                    it.transactionType == TransactionType.EXPENSE &&
                        it.loanId == null &&
                        !it.excludedFromAnalytics
                }

            // Resolve the pie's single currency and each transaction's amount in it.
            val currency: String
            val amounts: List<Pair<String, BigDecimal>> // category -> amount in [currency]
            if (isUnifiedMode) {
                currency = displayCurrency
                amounts = transactions.map { tx ->
                    val amount = if (tx.currency.equals(currency, ignoreCase = true)) {
                        tx.amount
                    } else {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, currency)
                    }
                    tx.category to amount
                }
            } else {
                val byCurrency = transactions.groupBy { it.currency.uppercase() }
                currency = byCurrency.maxByOrNull { (_, txs) ->
                    txs.sumOf { it.amount }
                }?.key ?: displayCurrency
                amounts = transactions
                    .filter { it.currency.equals(currency, ignoreCase = true) }
                    .map { it.category to it.amount }
            }

            val total = amounts.fold(BigDecimal.ZERO) { acc, (_, a) -> acc + a }
            val colorOverrides = categoryRepository.getAllCategories().first()
                .associate { it.name to it.color }

            val byCategory = amounts
                .groupBy { (cat, _) -> cat.ifBlank { "Others" } }
                .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { acc, (_, a) -> acc + a } }
                .entries
                .sortedByDescending { it.value }

            val top = byCategory.take(MAX_SLICES)
            val otherTotal = byCategory.drop(MAX_SLICES)
                .fold(BigDecimal.ZERO) { acc, e -> acc + e.value }

            fun percentOf(amount: BigDecimal): Float =
                if (total.signum() == 0) 0f
                else (amount.toFloat() / total.toFloat()) * 100f

            val slices = buildList {
                top.forEach { (name, amount) ->
                    add(
                        CategoryPieSlice(
                            name = name,
                            amountFormatted = CurrencyFormatter.formatCurrency(amount, currency),
                            colorArgb = CategoryMapping.colorFor(name, colorOverrides[name])
                                .toArgb().toLong(),
                            percent = percentOf(amount)
                        )
                    )
                }
                if (otherTotal.signum() > 0) {
                    add(
                        CategoryPieSlice(
                            name = "Other",
                            amountFormatted = CurrencyFormatter.formatCurrency(otherTotal, currency),
                            colorArgb = 0xFF9E9E9EL,
                            percent = percentOf(otherTotal)
                        )
                    )
                }
            }

            CategoryPieWidgetDataStore.update(
                applicationContext,
                CategoryPieWidgetData(
                    monthLabel = cycleStart.format(
                        DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
                    ).uppercase() + " · " + currency,
                    currency = currency,
                    totalFormatted = CurrencyFormatter.formatCurrency(total, currency),
                    slices = slices
                )
            )
            CategoryPieWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("CategoryPieWidget", "Widget update failed", e)
            Result.retry()
        }
    }
}
