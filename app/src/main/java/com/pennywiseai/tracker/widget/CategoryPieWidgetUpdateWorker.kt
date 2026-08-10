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
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Prepares the category pie widget's snapshot (#665): this cycle's EXPENSE
 * transactions grouped by category, top slices plus an aggregated "Other".
 *
 * Scope mirrors the analytics pie: split transactions contribute their split
 * amounts to each split's category (via [TransactionWithSplits.getAmountByCategory]),
 * hidden accounts are excluded, and the selected profile is respected.
 *
 * Currency: a pie mixing currencies is meaningless, so either everything is
 * converted into the display currency (unified mode, same as the other
 * widgets) or the cycle's dominant spend currency is shown and the rest are
 * left out — the label always says which currency the pie is in. In unified
 * mode a transaction whose exchange rate is unavailable is left out rather
 * than counted at its raw foreign face value, which would corrupt the total
 * and every percentage.
 */
@HiltWorker
class CategoryPieWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
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

            val expenses = transactionRepository
                .getTransactionsWithSplitsFiltered(cycleStart, now)
                .first()
                // Same exclusions as Home/Analytics/other widgets: loan-linked
                // rows are the Loans feature's, excluded rows are the user's call.
                .filter {
                    it.transaction.transactionType == TransactionType.EXPENSE &&
                        it.transaction.loanId == null &&
                        !it.transaction.excludedFromAnalytics
                }

            // Scope to the selected profile and drop hidden accounts, mirroring
            // Home and Analytics — the widget must agree with the in-app pie.
            val hidden = applicationContext
                .getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
                .getStringSet("hidden_accounts", emptySet()) ?: emptySet()
            val selectedProfileId = userPreferencesRepository.selectedProfileId.first()
            val profileKeys =
                buildProfileAccountKeys(accountBalanceRepository.getAllLatestBalances().first())
            val keptIds = filterTransactionsByProfile(
                expenses.map { it.transaction },
                selectedProfileId,
                profileKeys
            ).mapTo(HashSet()) { it.id }
            val transactions = expenses.filter { tw ->
                tw.transaction.id in keptIds &&
                    "${tw.transaction.bankName}_${tw.transaction.accountNumber}" !in hidden
            }

            // Resolve the pie's single currency, then aggregate per category.
            // Split transactions distribute their amount across their splits'
            // categories, exactly like the analytics breakdown.
            val currency: String
            val byCategory = mutableMapOf<String, BigDecimal>()
            if (isUnifiedMode) {
                currency = displayCurrency
                transactions.forEach { tw ->
                    val from = tw.transaction.currency
                    val rate = if (from.equals(currency, ignoreCase = true)) {
                        BigDecimal.ONE
                    } else {
                        // No rate → no honest way to place this transaction in
                        // the pie; skip it instead of counting the raw foreign
                        // amount as [currency].
                        currencyConversionService.getExchangeRate(from, currency)
                            ?: return@forEach
                    }
                    tw.getAmountByCategory().forEach { (cat, amount) ->
                        val converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                        byCategory.merge(cat.ifBlank { "Others" }, converted, BigDecimal::add)
                    }
                }
            } else {
                currency = transactions
                    .groupBy { it.transaction.currency.uppercase() }
                    .maxByOrNull { (_, txs) -> txs.sumOf { it.transaction.amount } }
                    ?.key ?: displayCurrency
                transactions
                    .filter { it.transaction.currency.equals(currency, ignoreCase = true) }
                    .forEach { tw ->
                        tw.getAmountByCategory().forEach { (cat, amount) ->
                            byCategory.merge(cat.ifBlank { "Others" }, amount, BigDecimal::add)
                        }
                    }
            }

            val ranked = byCategory.entries.sortedByDescending { it.value }
            val total = ranked.fold(BigDecimal.ZERO) { acc, e -> acc + e.value }
            val colorOverrides = categoryRepository.getAllCategories().first()
                .associate { it.name to it.color }

            val top = ranked.take(MAX_SLICES)
            val otherTotal = ranked.drop(MAX_SLICES)
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
