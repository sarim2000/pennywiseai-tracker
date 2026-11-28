package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.BudgetWindow
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val isUnifiedMode: Boolean = false
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

    private val selectedYearMonth = MutableStateFlow(YearMonth.now())

    /**
     * The day the under-budget card is judged against. [refreshDate] moves it on resume and
     * at midnight, so a ViewModel that lives across midnight doesn't keep yesterday's card.
     */
    private val today = MutableStateFlow(LocalDate.now())

    /** The budget cycle whose card can show today, or null; it follows the cycle start day. */
    private val cardCycle: Flow<BudgetWindow?> =
        combine(today, userPreferencesRepository.budgetCycleStartDay) { day, startDay -> underBudgetCycle(day, startDay) }
            .distinctUntilChanged()

    /**
     * The cycle that just ended kept every monthly spending limit, during the week after it.
     * [UnderBudgetCardState.ready] stays false until that decision arrives, including "no card".
     */
    val underBudget: StateFlow<UnderBudgetCardState> = cardCycle.flatMapLatest { cycle ->
        val decided: Flow<MonthUnderBudget?> = if (cycle == null) {
            // No cycle ended in the last week: skip the query.
            flowOf(null)
        } else {
            combine(
                // Read as of the cycle's last day, so each budget's window is the cycle that just
                // ended. Flagged: spending in another currency it can't count would hide an overspend.
                budgetSummaryProvider.summary(YearMonth.from(cycle.end), flagUncounted = true, today = cycle.end),
                userPreferencesRepository.budgetMonthCardsDismissed,
            ) { summary, dismissed -> monthUnderBudget(cycle, summary, dismissed) }
        }
        // The StateFlow starts not-ready, so a deep link waits for this decision instead of
        // scrolling before a late card shifts the rows. A new cycle replaces that value.
        decided.map { win -> UnderBudgetCardState(win = win, ready = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnderBudgetCardState())

    /** Called on resume and at midnight; a new day recounts, the same day does nothing. */
    fun refreshDate() {
        val now = LocalDate.now()
        selectedYearMonth.value = followToday(selectedYearMonth.value, today.value, now)
        today.value = now
        viewModelScope.launch {
            val cycle = underBudgetCycle(now, userPreferencesRepository.budgetCycleStartDay.first())
            userPreferencesRepository.pruneBudgetMonthDismissals(cycle?.let { YearMonth.from(it.end) })
        }
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
            userPreferencesRepository.dismissBudgetMonthCard(win.key, win.month)
        }
    }

    init {
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
                    isUnifiedMode = params.unifiedMode,
                    selectedYear = params.yearMonth.year,
                    selectedMonth = params.yearMonth.monthValue
                )
            }
        }

        viewModelScope.launch {
            combine(selectedYearMonth, today) { yearMonth, _ -> yearMonth }.flatMapLatest { yearMonth ->
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

    fun selectPreviousMonth() {
        selectedYearMonth.value = selectedYearMonth.value.minusMonths(1)
    }

    fun selectNextMonth() {
        // Don't navigate past the current month — there's no spend data
        // for a future cycle. The chevron button in the UI is disabled
        // when the user is on the current month; this guard is defensive.
        val today = LocalDate.now()
        val current = YearMonth.of(today.year, today.monthValue)
        val next = selectedYearMonth.value.plusMonths(1)
        if (!next.isAfter(current)) {
            selectedYearMonth.value = next
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

    private data class Params(
        val baseCurrency: String,
        val unifiedMode: Boolean,
        val displayCurrency: String,
        val yearMonth: YearMonth
    )
}
