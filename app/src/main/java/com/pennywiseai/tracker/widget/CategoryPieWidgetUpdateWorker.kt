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
import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import com.pennywiseai.tracker.domain.spending.analyticsRowsFor
import com.pennywiseai.tracker.domain.spending.defaultTypeFilter
import com.pennywiseai.tracker.domain.spending.netOf
import com.pennywiseai.tracker.domain.spending.refundNetting
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.inAppLanguage
import com.pennywiseai.tracker.utils.sumByCurrency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Prepares the category pie widget's snapshot (#665): this cycle's EXPENSE
 * transactions grouped by category, top slices plus an aggregated "Other".
 *
 * Scope mirrors the analytics pie: split transactions contribute their split
 * amounts to each split's category (via [TransactionWithSplits.getAmountByCategory]),
 * refunds come off their category and the total ([pieSpend]), hidden accounts are
 * excluded, and the selected profile is respected.
 *
 * Currency: a pie mixing currencies is meaningless, so either everything is
 * converted into the display currency (unified mode, same as the other
 * widgets) or one currency ([pieCurrency]) is shown and the rest are left
 * out — the total in the hole says which currency the pie is in. In unified
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

            // Every row of the cycle, scoped to the selected profile with hidden
            // accounts dropped, mirroring Home and Analytics. All types are kept:
            // refunds (INCOME) come off spending in [pieSpend].
            val hidden = applicationContext
                .getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
                .getStringSet("hidden_accounts", emptySet()) ?: emptySet()
            val selectedProfileId = userPreferencesRepository.selectedProfileId.first()
            val profileKeys =
                buildProfileAccountKeys(accountBalanceRepository.getAllLatestBalances().first())
            val cycleRows = transactionRepository
                .getTransactionsWithSplitsFiltered(cycleStart, now)
                .first()
            val keptIds = filterTransactionsByProfile(
                cycleRows.map { it.transaction },
                selectedProfileId,
                profileKeys
            ).mapTo(HashSet()) { it.id }
            val rows = cycleRows.filter { tw ->
                tw.transaction.id in keptIds &&
                    "${tw.transaction.bankName}_${tw.transaction.accountNumber}" !in hidden
            }

            // Card purchases count when the user opted in (same switch Home uses),
            // so a split credit purchase lands in the pie like it does in-app (#750).
            val creditAsExpense = userPreferencesRepository.countCreditCardAsExpense.first()

            // Resolve the pie's single currency, then aggregate per category.
            val currency: String
            val amountIn: suspend (TransactionEntity, BigDecimal) -> BigDecimal?
            if (isUnifiedMode) {
                currency = displayCurrency
                amountIn = { tx, amount ->
                    val rate = if (tx.currency.equals(currency, ignoreCase = true)) {
                        BigDecimal.ONE
                    } else {
                        // No rate → no honest way to place this amount in the pie;
                        // skip it instead of counting the raw foreign amount as [currency].
                        currencyConversionService.getExchangeRate(tx.currency, currency)
                    }
                    rate?.let { amount.multiply(it).setScale(MinorUnits.digits(currency), RoundingMode.HALF_UP) }
                }
            } else {
                val baseCurrency = userPreferencesRepository.baseCurrency.first()
                currency = pieCurrency(
                    analyticsRowsFor(rows, defaultTypeFilter(creditAsExpense))
                        .sumByCurrency({ it.transaction.currency.uppercase() }, { it.transaction.amount }),
                    baseCurrency
                ) { currencyConversionService.convertAmountOrNull(it.amount, it.currency, baseCurrency) }
                amountIn = { tx, amount -> amount.takeIf { tx.currency.equals(currency, ignoreCase = true) } }
            }
            val spend = pieSpend(rows, creditAsExpense, amountIn)
            val byCategory = spend.byCategory.toMutableMap()

            // The "Others" category folds into the aggregated "Other" slice so
            // the pie never shows two grey catch-alls.
            val othersCategory = byCategory.remove("Others") ?: BigDecimal.ZERO
            val ranked = byCategory.entries.sortedByDescending { it.value }
            // Home's "Spent": net of refunds. The slices share out what they add up
            // to, which differs from [total] only when a refund outgrows its category
            // or its currency's spend.
            val total = spend.total
            val sliceTotal = ranked.fold(othersCategory) { acc, e -> acc + e.value }
            val categories = categoryRepository.getAllCategories().first()
            val colorOverrides = categories.associate { it.name to it.color }

            val top = ranked.take(MAX_SLICES)
            val otherTotal = ranked.drop(MAX_SLICES)
                .fold(othersCategory) { acc, e -> acc + e.value }

            fun percentOf(amount: BigDecimal): Float =
                if (sliceTotal.signum() == 0) 0f
                else (amount.toFloat() / sliceTotal.toFloat()) * 100f

            val slices = buildList {
                top.forEach { (name, amount) ->
                    add(
                        CategoryPieSlice(
                            name = CategoryMapping.label(applicationContext.inAppLanguage(), name, categories),
                            colorArgb = CategoryMapping.colorFor(name, colorOverrides[name])
                                .toArgb().toLong(),
                            percent = percentOf(amount)
                        )
                    )
                }
                if (otherTotal.signum() > 0) {
                    add(
                        CategoryPieSlice(
                            name = applicationContext.inAppLanguage().getString(R.string.widget_pie_other),
                            colorArgb = 0xFF9E9E9EL,
                            percent = percentOf(otherTotal)
                        )
                    )
                }
            }

            CategoryPieWidgetDataStore.update(
                applicationContext,
                CategoryPieWidgetData(
                    cycleStart = cycleStart.toString(),
                    currency = currency,
                    total = total.toPlainString(),
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

/** [byCategory]: what each category's slice is worth; [total]: the figure in the hole. */
internal class PieSpend(val byCategory: Map<String, BigDecimal>, val total: BigDecimal)

/**
 * This cycle's spend by category, in the pie's currency, defined as Home's "Spent"
 * and the Analytics spend view: expense rows (plus card spend when [creditAsExpense]),
 * minus refunds (INCOME + DEDUCT_SPENT). A refund comes off its budget category
 * (floored at zero), as in Analytics (#704), and off [PieSpend.total] in full up to
 * its own currency's spend, as on Home.
 *
 * [rows] is the scoped cycle, every type. [amountIn] gives an amount of a row in the
 * pie's currency, or null to leave it out (another currency, or no rate).
 */
internal suspend fun pieSpend(
    rows: List<TransactionWithSplits>,
    creditAsExpense: Boolean,
    amountIn: suspend (TransactionEntity, BigDecimal) -> BigDecimal?
): PieSpend {
    val filter = defaultTypeFilter(creditAsExpense)
    val byCategory = mutableMapOf<String, BigDecimal>()
    val grossByCurrency = mutableMapOf<String, BigDecimal>()
    for (tw in analyticsRowsFor(rows, filter)) {
        for ((category, amount) in tw.getAmountByCategory()) {
            val counted = amountIn(tw.transaction, amount) ?: continue
            byCategory.merge(category.ifBlank { "Others" }, counted, BigDecimal::add)
            grossByCurrency.merge(tw.transaction.currency.uppercase(), counted, BigDecimal::add)
        }
    }
    // Home floors spend at zero per original currency before converting
    // (breakdownByCurrency), so a USD refund with no USD spend this cycle
    // takes nothing off the JOD total. Net each currency on its own, then add.
    val refundsByCurrency = rows.groupBy { it.transaction.currency.uppercase() }.mapValues { (_, group) ->
        refundNetting(group, filter, categoryFilter = null, parentOf = emptyMap()) { amountIn(it, it.amount) }
    }
    val refundByCategory = mutableMapOf<String, BigDecimal>()
    refundsByCurrency.values.forEach { refunds ->
        refunds.byCategory.forEach { (category, refund) -> refundByCategory.merge(category, refund, BigDecimal::add) }
    }
    refundByCategory.forEach { (category, refund) ->
        byCategory[category]?.let { byCategory[category] = netOf(it, refund) }
    }
    val total = (grossByCurrency.keys + refundsByCurrency.keys).fold(BigDecimal.ZERO) { acc, currency ->
        acc + netOf(grossByCurrency[currency] ?: BigDecimal.ZERO, refundsByCurrency[currency]?.total)
    }
    return PieSpend(byCategory.filterValues { it.signum() > 0 }, total)
}

/**
 * The pie's one currency outside unified mode: the user's own if they spent in
 * it, as the in-app pie opens on it, so ₹5,000 can't outrank JD 40 on face value.
 * Otherwise the largest spend by value in the base currency ([toBase]); one with
 * no rate ranks last, and if none has a rate the largest face value wins.
 */
internal suspend fun pieCurrency(
    spendByCurrency: Map<String, Money>,
    baseCurrency: String,
    toBase: suspend (Money) -> BigDecimal?
): String {
    val base = baseCurrency.uppercase()
    if ((spendByCurrency[base]?.amount?.signum() ?: 0) > 0) return base
    // One currency has nothing to rank against: no rate lookup, possibly network.
    spendByCurrency.keys.singleOrNull()?.let { return it }
    return spendByCurrency.values
        .map { it to (toBase(it) ?: BigDecimal.ZERO) }
        .maxWithOrNull(compareBy({ it.second }, { it.first.amount }))
        ?.first?.currency ?: base
}
