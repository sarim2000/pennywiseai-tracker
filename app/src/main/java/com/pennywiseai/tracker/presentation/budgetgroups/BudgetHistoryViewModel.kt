package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.PastWindowSpending
import com.pennywiseai.tracker.data.repository.WindowBreakdown
import com.pennywiseai.tracker.data.repository.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.data.repository.windowsForMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject

data class BudgetHistoryUiState(
    val isLoading: Boolean = true,
    val budget: BudgetEntity? = null,
    val yearMonth: YearMonth = YearMonth.now(),
    val windowHistory: List<PastWindowSpending> = emptyList(),
    val displayedWindowStart: java.time.LocalDate = java.time.LocalDate.now(),
    val displayedWindowEnd: java.time.LocalDate = java.time.LocalDate.now(),
    val displayedCapDate: java.time.LocalDate = java.time.LocalDate.now(),
    val displayedIsLive: Boolean = false,
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val currency: String = "INR",
    val baseCurrency: String = "INR",
    val budgetAmount: BigDecimal = BigDecimal.ZERO,
    /**
     * The per-window category breakdown currently shown in the bottom
     * sheet. Null when the sheet is dismissed. Set by [loadBreakdown].
     */
    val breakdown: WindowBreakdown? = null,
    val isLoadingBreakdown: Boolean = false
)

/**
 * Read-only history for one budget at the selected (year, month). Powers
 * the Budget History screen — opened from the Budgets page 3-dots
 * "View this period history" item.
 *
 * For Weekly budgets the per-window list is the per-week sub-list (4–5
 * weeks). For Monthly / One-time, the list has one entry — the cycle
 * for Monthly, the literal range for One-time. Each entry carries a
 * [PastWindowSpending.capDate] so the screen can label it "Live" or
 * "Frozen as of …".
 */
@HiltViewModel
class BudgetHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : ViewModel() {

    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L
    private val year: Int = savedStateHandle.get<Int>("year") ?: -1
    private val month: Int = savedStateHandle.get<Int>("month") ?: -1

    private val _uiState = MutableStateFlow(
        BudgetHistoryUiState(
            yearMonth = if (year > 0 && month > 0) YearMonth.of(year, month) else YearMonth.now()
        )
    )
    val uiState: StateFlow<BudgetHistoryUiState> = _uiState.asStateFlow()

    // Unified mode converts every currency's spending, and last period's for the
    // carry, as the Budgets card does; otherwise it counts the history's own currency only.
    private var convert: (suspend (String, BigDecimal) -> BigDecimal?)? = null

    // The sheet's load in flight. Cancelled when the sheet closes or another load
    // replaces it, so a slow one finishing late can't reopen or overwrite the sheet.
    private var breakdownJob: Job? = null

    init {
        // Loaded again when a rate changes: unified mode converts at today's rates, and
        // the breakdown's missing-rate link comes back here once one is added.
        viewModelScope.launch {
            currencyConversionService.getAllRatesFlow().collectLatest { loadHistory() }
        }
    }

    private suspend fun loadHistory() {
        val yearMonth = _uiState.value.yearMonth
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        val baseCurrency = userPreferencesRepository.baseCurrency.first()
        val displayCurrency = baseCurrency
        convert = if (userPreferencesRepository.unifiedCurrencyMode.first()) {
            { fromCurrency, amount -> currencyConversionService.convertAmountOrNull(amount, fromCurrency, displayCurrency) }
        } else null
        val budget = budgetGroupRepository.getGroupById(groupId) ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        val history = budgetGroupRepository.getBudgetHistoryForMonth(
            budget = budget,
            year = yearMonth.year,
            month = yearMonth.monthValue,
            currency = displayCurrency,
            convert = convert
        )
        val totalSpent = history.fold(BigDecimal.ZERO) { acc, w -> acc + w.spent }
        val isCurrentMonth = yearMonth == YearMonth.now()
        val today = java.time.LocalDate.now()
        val displayedWindow = if (isCurrentMonth) {
            resolveBudgetWindow(budget, today, startDay)
        } else {
            history.lastOrNull()?.window
                ?: windowsForMonth(budget, yearMonth.year, yearMonth.monthValue, startDay)
                    .lastOrNull()
                ?: com.pennywiseai.tracker.data.repository.BudgetWindow(
                    yearMonth.atDay(1), yearMonth.atEndOfMonth(), yearMonth.lengthOfMonth()
                )
        }
        val monthEnd = yearMonth.atEndOfMonth()
        val displayedCap = if (isCurrentMonth &&
            !today.isBefore(displayedWindow.start) && !today.isAfter(displayedWindow.end)
        ) today else monthEnd
        val displayedIsLive = isCurrentMonth &&
            !today.isBefore(displayedWindow.start) && !today.isAfter(displayedWindow.end)

        val openBreakdown = _uiState.value.breakdown?.window
        _uiState.value = BudgetHistoryUiState(
            isLoading = false,
            budget = budget,
            yearMonth = yearMonth,
            windowHistory = history,
            displayedWindowStart = displayedWindow.start,
            displayedWindowEnd = displayedWindow.end,
            displayedCapDate = displayedCap,
            displayedIsLive = displayedIsLive,
            totalSpent = totalSpent,
            currency = displayCurrency,
            baseCurrency = baseCurrency,
            budgetAmount = historyBudgetTotal(budget, history)
        )
        openBreakdown?.let(::loadBreakdown)
    }

    /**
     * Load the per-category breakdown for [window] and show it in the
     * bottom sheet. Idempotent — calling it twice on the same window is
     * a no-op. Calling it on a different window replaces the existing
     * sheet content.
     */
    fun loadBreakdown(window: BudgetWindow) {
        val budget = _uiState.value.budget ?: return
        if (_uiState.value.breakdown?.window == window && !_uiState.value.isLoadingBreakdown) return
        _uiState.value = _uiState.value.copy(
            isLoadingBreakdown = true,
            breakdown = _uiState.value.breakdown?.takeIf { it.window == window }
        )
        breakdownJob?.cancel()
        breakdownJob = viewModelScope.launch {
            val currency = _uiState.value.currency
            val breakdown = budgetGroupRepository.getBudgetWindowBreakdown(
                budget = budget,
                window = window,
                currency = currency,
                convert = convert
            )
            ensureActive()
            _uiState.value = _uiState.value.copy(
                breakdown = breakdown,
                isLoadingBreakdown = false
            )
        }
    }

    /** Dismiss the bottom sheet without changing the loaded breakdown. */
    fun dismissBreakdown() {
        breakdownJob?.cancel()
        _uiState.value = _uiState.value.copy(breakdown = null, isLoadingBreakdown = false)
    }
}

/**
 * The month's budget on the Budget History header: the base limit for each window,
 * plus what the month's first window carried in from before the month. A later
 * window's carry is an earlier window's leftover (or overspend) of this same month,
 * already counted in that window's limit, so adding it again would count it twice.
 */
internal fun historyBudgetTotal(budget: BudgetEntity, history: List<PastWindowSpending>): BigDecimal {
    val base = if (budget.periodType == com.pennywiseai.tracker.data.database.entity.BudgetPeriodType.WEEKLY) {
        budget.limitAmount.multiply(BigDecimal(history.size))
    } else budget.limitAmount
    // Rollover: the carry is in the history's own currency.
    return base + (history.firstOrNull()?.carry?.amount?.amount ?: BigDecimal.ZERO)
}
