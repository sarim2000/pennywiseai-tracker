package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.overlaps
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.utils.inAppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetGroupsUiState(
    val summary: BudgetOverallSummary? = null,
    /** The month [summary] was computed for; it trails [selectedMonth] while a new one loads. */
    val summaryMonth: YearMonth? = null,
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
    private val budgetSummaryProvider: BudgetSummaryProvider
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

    /**
     * The day the under-budget card is judged against. [refreshDate] moves it when the
     * screen resumes, so a ViewModel that lives across midnight doesn't keep yesterday's card.
     */
    private val today = MutableStateFlow(LocalDate.now())

    /**
     * Last month's spending limits all kept, during the first week of this one.
     * [UnderBudgetCardState.ready] stays false until that decision arrives, including "no card".
     */
    val underBudget: StateFlow<UnderBudgetCardState> = today.flatMapLatest { day ->
        val decided: Flow<MonthUnderBudget?> = if (day.dayOfMonth > UNDER_BUDGET_CARD_DAYS) {
            // Past the first week there's nothing to show: skip last month's query.
            flowOf(null)
        } else {
            combine(
                // Flagged: spending in another currency it can't count would hide an overspend.
                budgetSummaryProvider.summary(YearMonth.from(day).minusMonths(1), flagUncounted = true),
                userPreferencesRepository.budgetMonthCardsDismissed,
            ) { summary, dismissed -> monthUnderBudget(day, summary, dismissed) }
        }
        // The StateFlow starts not-ready, so a deep link waits for this decision instead of
        // scrolling before a late card shifts the rows. A new day replaces that value.
        decided.map { win -> UnderBudgetCardState(win = win, ready = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnderBudgetCardState())

    /** Re-reads today. The Budgets screen calls this from its resume path. */
    fun refreshDate() {
        val now = LocalDate.now()
        today.value = now
        viewModelScope.launch { userPreferencesRepository.pruneBudgetMonthDismissals(now, UNDER_BUDGET_CARD_DAYS) }
    }

    private val _celebrate = MutableStateFlow(false)

    /** True while the under-budget month is being celebrated, once per month and currency. */
    val celebrate: StateFlow<Boolean> = _celebrate.asStateFlow()

    fun onUnderBudgetShown(win: MonthUnderBudget) {
        viewModelScope.launch {
            if (userPreferencesRepository.celebrateOnce(win.key)) _celebrate.value = true
        }
    }

    fun onCelebrated() { _celebrate.value = false }

    fun dismissUnderBudget(win: MonthUnderBudget) {
        viewModelScope.launch {
            userPreferencesRepository.dismissBudgetMonthCard(win.key, today.value, UNDER_BUDGET_CARD_DAYS)
        }
    }

    init {
        // Default to today's calendar month. A user on startDay=25 viewing
        // the page on Jul 1 should land on the July view (the cycle started
        // Jun 25 and ends Jul 24, but the page label is the calendar month
        // today falls in). The cycle's start day still drives the per-budget
        // displayed window via resolveBudgetWindow, so Monthly cards on the
        // July view show the (Jun 25..Jul 24) cycle window — same data as
        // before, just labelled with today's month.
        _selectedYearMonth.value = YearMonth.now()
        viewModelScope.launch {
            userPreferencesRepository.budgetCycleStartDay.collect {
                // No-op: the default is today regardless of start day. Kept
                // as a hook for future logic (e.g. "snap to cycle" if the
                // user explicitly navigates back to the current cycle).
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
            selectedYearMonth.flatMapLatest { yearMonth ->
                budgetSummaryProvider.summary(yearMonth).map { yearMonth to it }
            }.collect { (yearMonth, summary) ->
                _uiState.value = _uiState.value.copy(
                    summary = summary,
                    summaryMonth = yearMonth,
                    hasGroups = summary.groups.isNotEmpty(),
                    isLoading = false
                )
            }
        }
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
        _selectedYearMonth.value = YearMonth.now()
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
            val res = context.inAppLanguage()
            budgetGroupRepository.createSmartDefaults(
                baseCurrency,
                spendingName = res.getString(R.string.budget_group_default_spending),
                fixedCostsName = res.getString(R.string.budget_group_default_fixed_costs),
                investmentsName = res.getString(R.string.budget_group_default_investments)
            )
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
}
