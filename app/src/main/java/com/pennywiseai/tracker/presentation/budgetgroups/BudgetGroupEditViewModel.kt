package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CategoryBudgetItem(
    val categoryName: String,
    val amount: BigDecimal,
    val currentSpending: BigDecimal = BigDecimal.ZERO
)

data class BudgetGroupEditUiState(
    val groupId: Long? = null,
    val name: String = "",
    val overallAmount: String = "",
    val categories: List<CategoryBudgetItem> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val categorySpending: Map<String, BigDecimal> = emptyMap(),
    val currency: String = "INR",
    val availableCurrencies: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false
)

@HiltViewModel
class BudgetGroupEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transactionSplitDao: TransactionSplitDao
) : ViewModel() {

    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L

    private val _uiState = MutableStateFlow(BudgetGroupEditUiState())
    val uiState: StateFlow<BudgetGroupEditUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val currency = userPreferencesRepository.baseCurrency.first()
            val currencies = CurrencyFormatter.getSupportedCurrencies()

            val today = LocalDate.now()
            val yearMonth = YearMonth.from(today)
            val startDate = yearMonth.atDay(1).atStartOfDay()
            val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

            val transactions = transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency).first()
            val categorySpending = mutableMapOf<String, BigDecimal>()
            transactions.forEach { txWithSplits ->
                if (txWithSplits.transaction.transactionType != TransactionType.INCOME) {
                    txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                        val categoryName = category.ifEmpty { "Others" }
                        categorySpending[categoryName] = (categorySpending[categoryName] ?: BigDecimal.ZERO) + amount
                    }
                }
            }

            if (groupId > 0) {
                val groups = budgetGroupRepository.getActiveGroups().first()
                val group = groups.find { it.budget.id == groupId }
                if (group != null) {
                    val assignedCategories = group.categories.map {
                        CategoryBudgetItem(
                            categoryName = it.categoryName,
                            amount = it.budgetAmount,
                            currentSpending = categorySpending[it.categoryName] ?: BigDecimal.ZERO
                        )
                    }
                    _uiState.value = BudgetGroupEditUiState(
                        groupId = groupId,
                        name = group.budget.name,
                        overallAmount = if (group.budget.limitAmount.compareTo(BigDecimal.ZERO) == 0) "" else group.budget.limitAmount.toPlainString(),
                        categories = assignedCategories,
                        categorySpending = categorySpending,
                        currency = group.budget.currency,
                        availableCurrencies = currencies,
                        isLoading = false
                    )
                }
            } else {
                _uiState.value = BudgetGroupEditUiState(
                    currency = currency,
                    categorySpending = categorySpending,
                    availableCurrencies = currencies,
                    isLoading = false
                )
            }

            loadAvailableCategories()
        }
    }

    private fun loadAvailableCategories() {
        viewModelScope.launch {
            categoryRepository.getExpenseCategories().collect { expenseCategories ->
                val currentCategoryNames = _uiState.value.categories.map { it.categoryName }
                val available = expenseCategories
                    .map { it.name }
                    .filter { it !in currentCategoryNames }
                _uiState.value = _uiState.value.copy(availableCategories = available)
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateOverallAmount(amount: String) {
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(overallAmount = amount)
        }
    }

    fun updateCurrency(currency: String) {
        _uiState.value = _uiState.value.copy(currency = currency)
    }

    fun addCategory(categoryName: String) {
        val current = _uiState.value.categories.toMutableList()
        val spending = _uiState.value.categorySpending[categoryName] ?: BigDecimal.ZERO
        current.add(CategoryBudgetItem(categoryName, BigDecimal.ZERO, spending))
        _uiState.value = _uiState.value.copy(
            categories = current,
            availableCategories = _uiState.value.availableCategories - categoryName
        )
    }

    fun removeCategory(categoryName: String) {
        val current = _uiState.value.categories.toMutableList()
        current.removeAll { it.categoryName == categoryName }
        _uiState.value = _uiState.value.copy(
            categories = current,
            availableCategories = _uiState.value.availableCategories + categoryName
        )
    }

    fun updateCategoryAmount(categoryName: String, amount: BigDecimal) {
        val current = _uiState.value.categories.map {
            if (it.categoryName == categoryName) it.copy(amount = amount) else it
        }
        _uiState.value = _uiState.value.copy(categories = current)
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return
        val overallAmount = state.overallAmount.toBigDecimalOrNull() ?: return
        if (overallAmount <= BigDecimal.ZERO) return

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            val categories = state.categories.map { it.categoryName to it.amount }
            val defaultColor = "#1565C0"

            if (state.groupId != null && state.groupId > 0) {
                budgetGroupRepository.updateGroup(
                    budgetId = state.groupId,
                    name = state.name,
                    groupType = BudgetGroupType.LIMIT,
                    color = defaultColor,
                    categories = categories,
                    currency = state.currency,
                    limitAmount = overallAmount
                )
            } else {
                budgetGroupRepository.createGroup(
                    name = state.name,
                    groupType = BudgetGroupType.LIMIT,
                    color = defaultColor,
                    currency = state.currency,
                    categories = categories,
                    limitAmount = overallAmount
                )
            }

            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
            _uiState.value = _uiState.value.copy(isSaving = false, saveComplete = true)
        }
    }

    fun deleteGroup() {
        val id = _uiState.value.groupId ?: return
        viewModelScope.launch {
            budgetGroupRepository.deleteGroup(id)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
            _uiState.value = _uiState.value.copy(saveComplete = true)
        }
    }
}
