package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.PastWindowSpending
import com.pennywiseai.tracker.data.repository.WindowSpending
import com.pennywiseai.tracker.data.repository.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.repository.overlaps
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.data.repository.windowsForMonth
import com.pennywiseai.tracker.domain.model.BudgetCycle
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
    val isUnifiedMode: Boolean = false,
    /**
     * True when the list is filtered to budgets whose window overlaps
     * the selected year-month. False shows every active budget. Toggled
     * by the Overlap/All chip in the UI.
     */
    val filterByWindowOverlap: Boolean = true
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
                SpendingParams(
                    displayCurrency = if (unifiedMode) displayCurrency else baseCurrency,
                    baseCurrency = baseCurrency,
                    unifiedMode = unifiedMode,
                    yearMonth = yearMonth
                )
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

    /**
     * Build a per-budget [BudgetGroupSpending] in the unified-currency path
     * from the repo's [BudgetGroupSpendingRaw]. The repo gives us a per-
     * (budget, window) spend map; the viewmodel applies currency
     * conversion to the displayed window's transactions and the
     * `previousWindows` (per-week sub-list) entries.
     */
    private suspend fun convertRawToSummary(
        raw: BudgetGroupSpendingRaw,
        displayCurrency: String,
        baseCurrency: String
    ): BudgetOverallSummary {
        val today = raw.today
        val yearMonth = YearMonth.of(today.year, today.monthValue) // for the "this is the current month" branch below
        val isCurrentMonth = raw.budgetsWithCategories.firstOrNull()?.let {
            val w = resolveBudgetWindow(it.budget, today, raw.globalStartDay)
            today >= w.start && today <= w.end
        } ?: false

        // Index the per-window spend by (budgetId, windowStart) for quick
        // lookup in the per-budget build.
        val windowSpendByBudget: Map<Long, List<WindowSpending>> =
            raw.windowedSpend.groupBy { it.budgetId }

        // Build per-budget summaries. For each budget:
        //  - pick the displayed window (resolveBudgetWindow for current
        //    month; most recent window in the list for historical months);
        //  - build the previousWindows list (Weekly only, historical only)
        //    with the per-window spend;
        //  - run the per-window transactions through the same aggregation
        //    used by the single-currency path, but with currency conversion.
        val groupSpendings = raw.budgetsWithCategories.map { group ->
            buildUnifiedGroupSpending(
                group = group,
                today = today,
                isCurrentMonth = isCurrentMonth,
                windowedSpend = windowSpendByBudget[group.budget.id].orEmpty(),
                displayCurrency = displayCurrency,
                baseCurrency = baseCurrency
            )
        }

        val totalIncome = raw.windowedSpend.flatMap { ws ->
            // We don't have raw transactions per window in the unified path
            // (the repo returned the spend totals). The home/widget use
            // this for the page-level "Income" stat; for the Budgets page
            // it's not used in the card so we approximate using the same
            // filter on the prev cycle transactions if the page is on the
            // current month.
            if (isCurrentMonth) raw.prevCycleTransactions else emptyList()
        }.fold(BigDecimal.ZERO) { acc, txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType == TransactionType.INCOME &&
                !(tx.budgetImpactType == BudgetImpactType.DEDUCT_SPENT && tx.budgetCategory != null)
            ) acc + currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
            else acc
        }
        // Note: totalIncome here uses the *previous* cycle's transactions
        // (since `windowedSpend` is keyed by window and doesn't carry the
        // raw income list). The page-level income figure is informational
        // only; the per-card spending is correct.

        val limitGroups = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        val targetGroups = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
        val expectedGroups = groupSpendings.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }

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
        val daysRemaining = groupSpendings.firstOrNull()?.daysRemaining ?: 0
        val dailyAllowance = if (daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
            limitRemaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val pageWindow = groupSpendings.firstOrNull()?.let {
            com.pennywiseai.tracker.data.repository.BudgetWindow(
                start = it.windowStart,
                end = it.windowEnd,
                days = it.windowDays
            )
        } ?: com.pennywiseai.tracker.data.repository.BudgetWindow(
            start = LocalDate.now(),
            end = LocalDate.now(),
            days = 1
        )

        return BudgetOverallSummary(
            groups = groupSpendings,
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
            daysRemaining = daysRemaining,
            currency = displayCurrency,
            pageWindow = pageWindow
        )
    }

    /**
     * Build one [BudgetGroupSpending] in the unified-currency path. We
     * don't have raw transactions for each window — the repo returned
     * the per-window totals — so we synthesise a "single-currency-equivalent"
     * by treating [windowedSpend] as the source of truth for each window's
     * spend. The displayed window's [WindowSpending.spent] is the
     * `totalActual` for the budget; the previousWindows list carries the
     * per-week breakdown.
     */
    private suspend fun buildUnifiedGroupSpending(
        group: BudgetWithCategories,
        today: LocalDate,
        isCurrentMonth: Boolean,
        windowedSpend: List<WindowSpending>,
        displayCurrency: String,
        baseCurrency: String
    ): BudgetGroupSpending {
        val budget = group.budget
        // Determine the displayed window.
        val displayedWindow = if (isCurrentMonth) {
            resolveBudgetWindow(budget, today, 1)
        } else {
            windowedSpend.lastOrNull()?.window
                ?: com.pennywiseai.tracker.data.repository.BudgetWindow(
                    start = LocalDate.now(), end = LocalDate.now(), days = 1
                )
        }
        val spentInDisplay = windowedSpend.firstOrNull { it.window == displayedWindow }?.spent
            ?: BigDecimal.ZERO

        // previousWindows: Weekly historical only.
        val previous = if (budget.periodType == BudgetPeriodType.WEEKLY && !isCurrentMonth) {
            windowedSpend.dropLast(1).map {
                PastWindowSpending(window = it.window, spent = it.spent)
            }
        } else emptyList()

        val daysElapsed: Int
        val daysRemaining: Int
        if (isCurrentMonth) {
            daysElapsed = (ChronoUnit.DAYS.between(displayedWindow.start, today).toInt() + 1)
                .coerceIn(1, displayedWindow.days)
            daysRemaining = (ChronoUnit.DAYS.between(today, displayedWindow.end).toInt() + 1)
                .coerceIn(0, displayedWindow.days)
        } else {
            daysElapsed = displayedWindow.days
            daysRemaining = 0
        }

        val convertedTotalActual = currencyConversionService.convertAmount(
            spentInDisplay, baseCurrency, displayCurrency
        )
        val convertedTotalBudget = currencyConversionService.convertAmount(
            group.budget.limitAmount, baseCurrency, displayCurrency
        )
        val remaining = convertedTotalBudget - convertedTotalActual
        val pctUsed = if (convertedTotalBudget > BigDecimal.ZERO) {
            (convertedTotalActual.toFloat() / convertedTotalBudget.toFloat() * 100f).coerceAtLeast(0f)
        } else 0f
        val dailyAllowance = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        // The per-category breakdown isn't computable from per-window
        // totals alone (the unified path doesn't return raw transactions
        // for each window). The single-currency path still shows it; the
        // unified path shows an empty list for the inline category list.
        return BudgetGroupSpending(
            group = group,
            categorySpending = emptyList(),
            totalBudget = convertedTotalBudget,
            totalActual = convertedTotalActual,
            remaining = remaining,
            percentageUsed = pctUsed,
            dailyAllowance = dailyAllowance,
            daysRemaining = daysRemaining,
            daysElapsed = daysElapsed,
            isTrackingAllExpenses = group.categories.isEmpty(),
            dailyCumulativeSpending = emptyList(),
            dailyBudgetPace = emptyList(),
            windowStart = displayedWindow.start,
            windowEnd = displayedWindow.end,
            windowDays = displayedWindow.days,
            // The unified path only renders the current month, so the
            // displayed window is always live — cap is today.
            displayedCapDate = today,
            displayedIsLive = isCurrentMonth,
            periodType = budget.periodType,
            previousWindows = previous
        )
    }

    fun toggleFilter() {
        _uiState.value = _uiState.value.copy(
            filterByWindowOverlap = !_uiState.value.filterByWindowOverlap
        )
    }

    fun setFilterByWindowOverlap(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(filterByWindowOverlap = enabled)
    }

    fun selectPreviousMonth() {
        _selectedYearMonth.value = selectedYearMonth.value.minusMonths(1)
    }

    fun selectNextMonth() {
        // Don't navigate past the current month — there's no spend data
        // for a future cycle. The chevron button in the UI is disabled
        // when the user is on the current month; this guard is defensive.
        val today = LocalDate.now()
        val current = YearMonth.of(today.year, today.monthValue)
        val next = selectedYearMonth.value.plusMonths(1)
        if (!next.isAfter(current)) {
            _selectedYearMonth.value = next
        }
    }

    fun selectCurrentMonth() {
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

    /**
     * Filter the [BudgetOverallSummary.groups] by the Overlap toggle.
     * When [filterByWindowOverlap] is true, a budget is shown only if any
     * of its resolved windows (against the selected year-month) intersect
     * that month. Weekly always shows (its window is the current week);
     * One-time Nov 5–Dec 4 shows for November and December; Monthly
     * Sep 25–Oct 24 shows for September and October.
     */
    fun visibleGroups(summary: BudgetOverallSummary?, filterByWindowOverlap: Boolean): List<BudgetGroupSpending> {
        if (summary == null) return emptyList()
        if (!filterByWindowOverlap) return summary.groups
        val ym = YearMonth.of(_uiState.value.selectedYear, _uiState.value.selectedMonth)
        val monthStart = ym.atDay(1)
        val monthEnd = ym.atEndOfMonth()
        val monthWindow = com.pennywiseai.tracker.data.repository.BudgetWindow(
            start = monthStart, end = monthEnd, days = monthEnd.dayOfMonth
        )
        return summary.groups.filter { g ->
            val candidate = g.periodType.let { _ ->
                // Use the displayed window + previousWindows to find any
                // overlap with the month. Weekly historical has multiple
                // windows in `previousWindows`; current view has just
                // the displayed window.
                val allWindows = buildList {
                    add(com.pennywiseai.tracker.data.repository.BudgetWindow(
                        start = g.windowStart, end = g.windowEnd, days = g.windowDays
                    ))
                    g.previousWindows.forEach { add(it.window) }
                }
                allWindows.any { it.overlaps(monthWindow) }
            }
            candidate
        }
    }

    // Unused — kept for backwards compat with code that referenced it.
    @Suppress("unused")
    private data class Params(
        val baseCurrency: String,
        val unifiedMode: Boolean,
        val displayCurrency: String,
        val yearMonth: YearMonth
    )

    @Suppress("unused")
    private data class SpendingParams(
        val displayCurrency: String,
        val baseCurrency: String,
        val unifiedMode: Boolean,
        val yearMonth: YearMonth
    )
}
