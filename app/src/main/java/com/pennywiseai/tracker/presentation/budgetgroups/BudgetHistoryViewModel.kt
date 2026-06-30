package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.PastWindowSpending
import com.pennywiseai.tracker.data.repository.aggregateBudgetCategorySpending
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.data.repository.windowsForMonth
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val budgetAmount: BigDecimal = BigDecimal.ZERO
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
    private val userPreferencesRepository: UserPreferencesRepository
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

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val yearMonth = _uiState.value.yearMonth
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val displayCurrency = baseCurrency
            val budget = budgetGroupRepository.getGroupById(groupId) ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            val history = budgetGroupRepository.getBudgetHistoryForMonth(
                budget = budget,
                year = yearMonth.year,
                month = yearMonth.monthValue,
                currency = displayCurrency
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
                budgetAmount = budget.limitAmount
            )
        }
    }
}
