package com.pennywiseai.tracker.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.repository.BudgetRepository
import com.pennywiseai.tracker.data.repository.BudgetSpending
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Available currencies from transactions
    val availableCurrencies: StateFlow<List<String>> = transactionRepository.getAllCurrencies()
        .map { currencies ->
            if (currencies.isEmpty()) listOf("INR") else currencies
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("INR")
        )

    // All expense categories for budget creation
    val expenseCategories: StateFlow<List<CategoryEntity>> = categoryRepository.getExpenseCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadBudgets()
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            budgetRepository.getCurrentBudgets()
                .collectLatest { budgets ->
                    val budgetsWithSpending = budgets.map { budget ->
                        val spending = budgetRepository.getBudgetSpending(budget).first()
                        val dailyAllowance = budgetRepository.calculateDailyAllowance(budget, spending.totalSpent)
                        val daysRemaining = budgetRepository.getDaysRemaining(budget)
                        val categories = budgetRepository.getCategoryNamesForBudget(budget.id)

                        BudgetWithSpending(
                            budget = budget,
                            spending = spending,
                            dailyAllowance = dailyAllowance,
                            daysRemaining = daysRemaining,
                            categories = categories
                        )
                    }

                    _uiState.update {
                        it.copy(
                            budgets = budgetsWithSpending,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun createBudget(
        name: String,
        limitAmount: BigDecimal,
        periodType: BudgetPeriodType,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        includeAllCategories: Boolean,
        categories: List<String>,
        color: String
    ) {
        viewModelScope.launch {
            try {
                budgetRepository.createBudget(
                    name = name,
                    limitAmount = limitAmount,
                    periodType = periodType,
                    startDate = startDate,
                    endDate = endDate,
                    currency = currency,
                    includeAllCategories = includeAllCategories,
                    categories = categories,
                    color = color
                )
                _snackbarMessage.value = "Budget created successfully"
                loadBudgets()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error creating budget: ${e.message}"
            }
        }
    }

    fun updateBudget(
        budgetId: Long,
        name: String,
        limitAmount: BigDecimal,
        periodType: BudgetPeriodType,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        includeAllCategories: Boolean,
        categories: List<String>,
        color: String
    ) {
        viewModelScope.launch {
            try {
                budgetRepository.updateBudget(
                    budgetId = budgetId,
                    name = name,
                    limitAmount = limitAmount,
                    periodType = periodType,
                    startDate = startDate,
                    endDate = endDate,
                    currency = currency,
                    includeAllCategories = includeAllCategories,
                    categories = categories,
                    color = color
                )
                _snackbarMessage.value = "Budget updated successfully"
                loadBudgets()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error updating budget: ${e.message}"
            }
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            try {
                budgetRepository.deleteBudget(budgetId)
                _snackbarMessage.value = "Budget deleted"
                loadBudgets()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error deleting budget: ${e.message}"
            }
        }
    }

    fun loadBudgetForEdit(budgetId: Long) {
        viewModelScope.launch {
            val budget = budgetRepository.getBudgetById(budgetId)
            val categories = budget?.let { budgetRepository.getCategoryNamesForBudget(it.id) } ?: emptyList()

            _uiState.update {
                it.copy(
                    editingBudget = budget,
                    editingBudgetCategories = categories
                )
            }
        }
    }

    fun clearEditingBudget() {
        _uiState.update {
            it.copy(
                editingBudget = null,
                editingBudgetCategories = emptyList()
            )
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun refreshBudgets() {
        loadBudgets()
    }
}

data class BudgetUiState(
    val budgets: List<BudgetWithSpending> = emptyList(),
    val isLoading: Boolean = true,
    val editingBudget: BudgetEntity? = null,
    val editingBudgetCategories: List<String> = emptyList()
)

data class BudgetWithSpending(
    val budget: BudgetEntity,
    val spending: BudgetSpending,
    val dailyAllowance: BigDecimal,
    val daysRemaining: Int,
    val categories: List<String>
)
