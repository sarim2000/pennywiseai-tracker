package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository.Companion.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class BudgetGroupsUiState(
    val summary: BudgetOverallSummary? = null,
    val isLoading: Boolean = true,
    val hasGroups: Boolean = false,
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val currency: String = "INR",
    val baseCurrency: String = "INR",
    val isUnifiedMode: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetGroupsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetGroupsUiState())
    val uiState: StateFlow<BudgetGroupsUiState> = _uiState.asStateFlow()

    // Defaults to the user's current budget-cycle start (e.g. Sep 2026 on
    // Oct 5 with startDay=25) so opening the screen lands on the cycle that
    // contains today, not the calendar month. Once the user navigates
    // forwards/backwards via the month picker, we stop overriding.
    private val _selectedYearMonth = MutableStateFlow<YearMonth?>(null)
    private val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = YearMonth.now()
        )

    init {
        // Seed the default to the cycle's start month, then react to changes
        // to the start day so the picker snaps back to the new current cycle
        // when the user hasn't navigated yet.
        viewModelScope.launch {
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            _selectedYearMonth.value = BudgetCycle.currentCycleStartYearMonth(LocalDate.now(), startDay)
        }
        viewModelScope.launch {
            userPreferencesRepository.budgetCycleStartDay.collect { startDay ->
                if (_selectedYearMonth.value == null) {
                    _selectedYearMonth.value =
                        BudgetCycle.currentCycleStartYearMonth(LocalDate.now(), startDay)
                }
            }
        }
        loadBudgetData()
    }

    private fun loadBudgetData() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                selectedYearMonth
            ) { baseCurrency, unifiedMode, displayCurrency, yearMonth ->
                data class Params(val baseCurrency: String, val unifiedMode: Boolean, val displayCurrency: String, val yearMonth: YearMonth)
                Params(baseCurrency, unifiedMode, displayCurrency, yearMonth)
            }.collect { params ->
                val currency = if (params.unifiedMode) params.displayCurrency else params.baseCurrency
                _uiState.value = _uiState.value.copy(
                    currency = currency,
                    baseCurrency = params.baseCurrency,
                    isUnifiedMode = params.unifiedMode,
                    selectedYear = params.yearMonth.year,
                    selectedMonth = params.yearMonth.monthValue
                )
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                selectedYearMonth
            ) { baseCurrency, unifiedMode, displayCurrency, yearMonth ->
                data class SpendingParams(val displayCurrency: String, val baseCurrency: String, val unifiedMode: Boolean, val yearMonth: YearMonth)
                SpendingParams(if (unifiedMode) displayCurrency else baseCurrency, baseCurrency, unifiedMode, yearMonth)
            }.flatMapLatest { params ->
                if (params.unifiedMode) {
                    budgetGroupRepository.getGroupSpendingAllCurrencies(params.yearMonth.year, params.yearMonth.monthValue)
                        .map { raw -> convertRawToSummary(raw, params.displayCurrency, params.baseCurrency) }
                } else {
                    budgetGroupRepository.getGroupSpending(params.yearMonth.year, params.yearMonth.monthValue, params.displayCurrency)
                }
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(
                    summary = summary,
                    hasGroups = summary.groups.isNotEmpty(),
                    isLoading = false
                )
            }
        }
    }

    private suspend fun convertRawToSummary(
        raw: BudgetGroupSpendingRaw,
        displayCurrency: String,
        baseCurrency: String
    ): BudgetOverallSummary {
        // Exclude a Refund from totalIncome only when it's also being subtracted
        // from a category by aggregateBudgetCategorySpending (categorised refund);
        // orphaned DEDUCT_SPENT income stays in the total so netSavings doesn't
        // understate. Matches the same guard used in BudgetGroupRepository and the
        // widget.
        var totalIncome = BigDecimal.ZERO
        for (txWithSplits in raw.allTransactions) {
            val tx = txWithSplits.transaction
            if (tx.transactionType != TransactionType.INCOME) continue
            if (tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT &&
                tx.budgetCategory != null
            ) continue
            totalIncome = totalIncome.add(
                currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
            )
        }

        // Unified-currency: route through the same aggregator used by the
        // single-currency path so Refund (DEDUCT_SPENT) and Extra budget
        // (ADD_TO_LIMIT) take effect here too. The converters project each
        // amount into the display currency before aggregation.
        val (categoryAmounts, categoryLimitBoosts, typeAmounts) = aggregateBudgetCategorySpending(
            transactions = raw.allTransactions,
            convertSplit = { fromCurrency, amount ->
                currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
            },
            convertIncome = { tx ->
                currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
            }
        )

        // Pre-compute converted category amounts per transaction for pace chart.
        // The pace chart is calibrated against the actual budget cycle (e.g.
        // Sep 25..Oct 24 = 30 days) — not the calendar month — so the daily
        // budget line reflects the cycle the user picked, not a 31-day October.
        val yearMonth = selectedYearMonth.value
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        val (cycleStart, cycleEnd) = BudgetCycle.currentCycle(yearMonth.atDay(1), startDay)
        val daysInCycle = ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt() + 1
        val daysInMonth = daysInCycle
        val effectiveDays = raw.daysElapsed.coerceAtMost(daysInCycle)

        data class ConvertedTxDay(val day: Int, val categoryAmounts: Map<String, Double>)
        val convertedTxDays = raw.allTransactions.mapNotNull { txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType == TransactionType.INCOME ||
                tx.transactionType == TransactionType.TRANSFER ||
                tx.transactionType == TransactionType.INVESTMENT ||
                tx.loanId != null
            ) return@mapNotNull null
            val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
            val catAmounts = txWithSplits.getAmountByCategory().mapValues { (_, amount) ->
                currencyConversionService.convertAmount(amount, tx.currency, displayCurrency).toDouble()
            }.mapKeys { (cat, _) -> cat.ifEmpty { "Others" } }
            ConvertedTxDay(day, catAmounts)
        }

        // Type-bucket transactions (e.g. INVESTMENT) for the pace chart, keyed by
        // transaction type. Routed separately from categories so a type bucket's
        // "Actual" line reflects its type, not any category.
        data class ConvertedTypeDay(val day: Int, val typeName: String, val amount: Double)
        val convertedTypeDays = raw.allTransactions.mapNotNull { txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType !in BudgetGroupRepository.BUDGET_TYPE_BUCKETS || tx.loanId != null) {
                return@mapNotNull null
            }
            val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
            val amt = currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency).toDouble()
            ConvertedTypeDay(day, tx.transactionType.name, amt)
        }

        // Pre-convert Refund (DEDUCT_SPENT) income amounts on the day they occurred
        // so the pace chart subtracts them in lockstep with the displayed "actual".
        data class ConvertedRefundDay(val day: Int, val category: String, val amount: Double)
        val convertedRefundDays = raw.allTransactions.mapNotNull { txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType != TransactionType.INCOME) return@mapNotNull null
            if (tx.budgetImpactType != BudgetImpactType.DEDUCT_SPENT) return@mapNotNull null
            val category = tx.budgetCategory ?: return@mapNotNull null
            val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
            val amount = currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency).toDouble()
            ConvertedRefundDay(day, category, amount)
        }

        fun buildGroupPaceUnified(
            categoryNames: Set<String>?,
            matchTypes: Set<String>,
            groupBudget: BigDecimal
        ): Pair<List<Double>, List<Double>> {
            if (effectiveDays < 1) return emptyList<Double>() to emptyList()
            val dailyAmounts = DoubleArray(daysInMonth)
            convertedTxDays.forEach { txDay ->
                if (categoryNames == null) {
                    dailyAmounts[txDay.day - 1] += txDay.categoryAmounts.values.sum()
                } else {
                    txDay.categoryAmounts.forEach { (cat, amount) ->
                        if (cat in categoryNames) dailyAmounts[txDay.day - 1] += amount
                    }
                }
            }
            convertedTypeDays.forEach { typeDay ->
                if (typeDay.typeName in matchTypes) dailyAmounts[typeDay.day - 1] += typeDay.amount
            }
            convertedRefundDays.forEach { refundDay ->
                if (categoryNames == null || refundDay.category in categoryNames) {
                    dailyAmounts[refundDay.day - 1] -= refundDay.amount
                }
            }
            // Carry the running total forward unclamped so a refund dated before
            // the first expense still nets out; only the emitted value is clamped
            // so the endpoint matches aggregateBudgetCategorySpending's floor.
            val cumulative = mutableListOf<Double>()
            var runningNet = 0.0
            for (i in 0 until effectiveDays) {
                runningNet += dailyAmounts[i]
                cumulative.add(runningNet.coerceAtLeast(0.0))
            }
            val pace = if (groupBudget > BigDecimal.ZERO) {
                val dp = groupBudget.toDouble() / daysInMonth
                (1..effectiveDays).map { it * dp }
            } else emptyList()
            return cumulative to pace
        }

        val groupSpendingList = raw.budgetsWithCategories.map { group ->
            val isTrackingAll = group.categories.isEmpty()
            val catSpending = group.categories.map { cat ->
                val actual = if (cat.matchType != null) {
                    typeAmounts[cat.matchType] ?: BigDecimal.ZERO
                } else {
                    categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                }
                val convertedBudget = currencyConversionService.convertAmount(cat.budgetAmount, baseCurrency, displayCurrency)
                val boost = if (cat.matchType != null) BigDecimal.ZERO
                    else categoryLimitBoosts[cat.categoryName] ?: BigDecimal.ZERO
                val effectiveBudget = convertedBudget + boost
                val pctUsed = if (effectiveBudget > BigDecimal.ZERO) {
                    (actual.toFloat() / effectiveBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailySpend = if (raw.daysElapsed > 0 && actual > BigDecimal.ZERO) {
                    actual.divide(BigDecimal(raw.daysElapsed), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                BudgetCategorySpending(
                    categoryName = cat.categoryName,
                    budgetAmount = effectiveBudget,
                    actualAmount = actual,
                    percentageUsed = pctUsed,
                    dailySpend = dailySpend
                )
            }
            val convertedGroupLimit = currencyConversionService.convertAmount(
                group.budget.limitAmount, baseCurrency, displayCurrency
            )
            // "Category Limits" are optional — the group-level limit is the
            // source of truth when set (including when isTrackingAll), with
            // the per-cat sum as a fallback for budgets that only define
            // per-cat amounts.
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
            val remaining = totalBudget - totalActual
            val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
            } else 0f
            val dailyAllowance = if (raw.daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                remaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val catNames = if (isTrackingAll) null
                else group.categories.filter { it.matchType == null }.map { it.categoryName }.toSet()
            val matchTypes = group.categories.mapNotNull { it.matchType }.toSet()
            val (cumSpending, budgetPace) = buildGroupPaceUnified(catNames, matchTypes, totalBudget)

            BudgetGroupSpending(
                group = group,
                categorySpending = if (isTrackingAll) emptyList() else catSpending,
                totalBudget = totalBudget,
                totalActual = totalActual,
                remaining = remaining,
                percentageUsed = pctUsed,
                dailyAllowance = dailyAllowance,
                daysRemaining = raw.daysRemaining,
                daysElapsed = raw.daysElapsed,
                isTrackingAllExpenses = isTrackingAll,
                dailyCumulativeSpending = cumSpending,
                dailyBudgetPace = budgetPace
            )
        }

        val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        val targetGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
        val expectedGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }

        val totalLimitBudget = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalTargetGoal = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalTargetActual = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalExpectedBudget = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalExpectedActual = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }

        val netSavings = totalIncome - totalLimitSpent
        val savingsRate = if (totalIncome > BigDecimal.ZERO) {
            (netSavings.toFloat() / totalIncome.toFloat() * 100f)
        } else 0f

        val limitRemaining = totalLimitBudget - totalLimitSpent
        val dailyAllowance = if (raw.daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
            limitRemaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return BudgetOverallSummary(
            groups = groupSpendingList,
            totalIncome = totalIncome,
            totalLimitBudget = totalLimitBudget,
            totalLimitSpent = totalLimitSpent,
            totalTargetGoal = totalTargetGoal,
            totalTargetActual = totalTargetActual,
            totalExpectedBudget = totalExpectedBudget,
            totalExpectedActual = totalExpectedActual,
            netSavings = netSavings,
            savingsRate = savingsRate,
            dailyAllowance = dailyAllowance,
            daysRemaining = raw.daysRemaining,
            currency = displayCurrency
        )
    }

    fun selectPreviousMonth() {
        _selectedYearMonth.value = selectedYearMonth.value.minusMonths(1)
    }

    fun selectNextMonth() {
        val next = selectedYearMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _selectedYearMonth.value = next
        }
    }

    fun selectCurrentMonth() {
        // Jump to the user's current budget cycle, not the calendar month.
        // For startDay=25 on Oct 5 this lands on the Sep 25..Oct 24 cycle.
        viewModelScope.launch {
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            _selectedYearMonth.value =
                BudgetCycle.currentCycleStartYearMonth(LocalDate.now(), startDay)
        }
    }

    fun deleteGroup(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.deleteGroup(budgetId)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }
    
    fun moveGroupUp(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.moveGroupUp(budgetId)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }
    
    fun moveGroupDown(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.moveGroupDown(budgetId)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun runSmartDefaults() {
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            budgetGroupRepository.createSmartDefaults(baseCurrency)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

}
