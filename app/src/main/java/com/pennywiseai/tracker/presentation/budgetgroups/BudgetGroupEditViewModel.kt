package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import javax.inject.Inject

data class CategoryBudgetItem(
    val categoryName: String,
    val amount: BigDecimal
)

data class BudgetGroupEditUiState(
    val groupId: Long? = null,
    val name: String = "",
    val type: BudgetGroupType = BudgetGroupType.LIMIT,
    val color: String = "#1565C0",
    val categories: List<CategoryBudgetItem> = emptyList(),
    val availableCategories: List<String> = emptyList(),
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
    private val userPreferencesRepository: UserPreferencesRepository
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

            if (groupId > 0) {
                // Edit existing group
                val groups = budgetGroupRepository.getActiveGroups().first()
                val group = groups.find { it.budget.id == groupId }
                if (group != null) {
                    val assignedCategories = group.categories.map {
                        CategoryBudgetItem(it.categoryName, it.budgetAmount)
                    }
                    _uiState.value = BudgetGroupEditUiState(
                        groupId = groupId,
                        name = group.budget.name,
                        type = group.budget.groupType,
                        color = group.budget.color,
                        categories = assignedCategories,
                        currency = group.budget.currency,
                        availableCurrencies = currencies,
                        isLoading = false
                    )
                }
            } else {
                _uiState.value = BudgetGroupEditUiState(
                    currency = currency,
                    availableCurrencies = currencies,
                    isLoading = false
                )
            }

            // Load available (unassigned) categories
            combine(
                categoryRepository.getExpenseCategories(),
                budgetGroupRepository.getAssignedCategories()
            ) { allCategories, assignedNames ->
                val currentGroupCats = _uiState.value.categories.map { it.categoryName }.toSet()
                allCategories
                    .map { it.name }
                    .filter { it !in assignedNames || it in currentGroupCats }
                    .filter { it !in _uiState.value.categories.map { c -> c.categoryName } }
            }.collect { available ->
                _uiState.value = _uiState.value.copy(availableCategories = available)
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateType(type: BudgetGroupType) {
        _uiState.value = _uiState.value.copy(type = type)
    }

    fun updateColor(color: String) {
        _uiState.value = _uiState.value.copy(color = color)
    }

    fun updateCurrency(currency: String) {
        _uiState.value = _uiState.value.copy(currency = currency)
    }

    fun addCategory(categoryName: String) {
        val current = _uiState.value.categories.toMutableList()
        current.add(CategoryBudgetItem(categoryName, BigDecimal.ZERO))
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

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            val categories = state.categories.map { it.categoryName to it.amount }

            if (state.groupId != null && state.groupId > 0) {
                budgetGroupRepository.updateGroup(
                    budgetId = state.groupId,
                    name = state.name,
                    groupType = state.type,
                    color = state.color,
                    categories = categories,
                    currency = state.currency
                )
            } else {
                budgetGroupRepository.createGroup(
                    name = state.name,
                    groupType = state.type,
                    color = state.color,
                    currency = state.currency,
                    categories = categories
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
