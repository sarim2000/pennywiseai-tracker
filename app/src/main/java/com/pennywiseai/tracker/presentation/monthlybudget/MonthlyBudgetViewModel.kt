package com.pennywiseai.tracker.presentation.monthlybudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CategoryBudgetLimitEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.CategorySpendingInfo
import com.pennywiseai.tracker.data.repository.MonthlyBudgetRepository
import com.pennywiseai.tracker.data.repository.MonthlyBudgetSpending
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthlyBudgetUiState(
    val monthlyLimit: BigDecimal? = null,
    val spending: MonthlyBudgetSpending? = null,
    val categoryLimits: List<CategoryBudgetLimitEntity> = emptyList(),
    val unallocatedBudget: BigDecimal = BigDecimal.ZERO,
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val isLoading: Boolean = true,
    val currency: String = "INR"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyBudgetViewModel @Inject constructor(
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyBudgetUiState())
    val uiState: StateFlow<MonthlyBudgetUiState> = _uiState.asStateFlow()

    val expenseCategories: StateFlow<List<CategoryEntity>> = categoryRepository.getExpenseCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    init {
        loadBudgetData()
    }

    private fun loadBudgetData() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                monthlyBudgetRepository.monthlyBudgetLimit,
                monthlyBudgetRepository.getCategoryLimits(),
                _selectedYearMonth
            ) { currency, limit, categoryLimits, yearMonth ->
                Triple(currency, Triple(limit, categoryLimits, yearMonth), null)
            }.collect { (currency, data, _) ->
                val (limit, categoryLimits, yearMonth) = data
                _uiState.value = _uiState.value.copy(
                    monthlyLimit = limit,
                    categoryLimits = categoryLimits,
                    currency = currency,
                    selectedYear = yearMonth.year,
                    selectedMonth = yearMonth.monthValue
                )
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                _selectedYearMonth
            ) { currency, yearMonth ->
                currency to yearMonth
            }.flatMapLatest { (currency, yearMonth) ->
                monthlyBudgetRepository.getMonthSpending(yearMonth.year, yearMonth.monthValue, currency)
            }.collect { spending ->
                val limit = _uiState.value.monthlyLimit ?: BigDecimal.ZERO
                val allocatedTotal = _uiState.value.categoryLimits.fold(BigDecimal.ZERO) { acc, cat ->
                    acc + cat.limitAmount
                }
                _uiState.value = _uiState.value.copy(
                    spending = spending,
                    unallocatedBudget = limit - allocatedTotal,
                    isLoading = false
                )
            }
        }
    }

    fun setMonthlyLimit(amount: BigDecimal) {
        viewModelScope.launch {
            monthlyBudgetRepository.setMonthlyBudgetLimit(amount)
        }
    }

    fun setCategoryLimit(categoryName: String, amount: BigDecimal) {
        viewModelScope.launch {
            monthlyBudgetRepository.setCategoryLimit(categoryName, amount)
        }
    }

    fun removeCategoryLimit(categoryName: String) {
        viewModelScope.launch {
            monthlyBudgetRepository.removeCategoryLimit(categoryName)
        }
    }

    fun removeBudget() {
        viewModelScope.launch {
            monthlyBudgetRepository.setMonthlyBudgetLimit(null)
        }
    }

    fun selectPreviousMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
    }

    fun selectNextMonth() {
        val next = _selectedYearMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _selectedYearMonth.value = next
        }
    }

    fun selectCurrentMonth() {
        _selectedYearMonth.value = YearMonth.now()
    }
}
