package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetBucketInput
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
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
    val currentSpending: BigDecimal = BigDecimal.ZERO,
    /** Non-null → this row is a transaction-type bucket (e.g. "INVESTMENT"). */
    val matchType: String? = null
)

/** A trackable transaction-type bucket offered in the add-bucket menu. */
data class TypeBucketOption(
    val typeName: String,
    val displayName: String
)

data class BudgetGroupEditUiState(
    val groupId: Long? = null,
    val name: String = "",
    val overallAmount: String = "",
    val categories: List<CategoryBudgetItem> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableTypeBuckets: List<TypeBucketOption> = emptyList(),
    val categorySpending: Map<String, BigDecimal> = emptyMap(),
    val typeSpending: Map<String, BigDecimal> = emptyMap(),
    val currency: String = "INR",
    val availableCurrencies: List<String> = emptyList(),
    /** The budget's start date. Editable via the date picker. */
    val startDate: LocalDate = LocalDate.now(),
    /** Read-only end date, auto-derived from [startDate] + [periodType]. */
    val endDate: LocalDate = LocalDate.now(),
    /** Weekly / Monthly / Custom. Drives how [endDate] is derived. */
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
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
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val unifiedMode = userPreferencesRepository.unifiedCurrencyMode.first()
            val displayCurrency = userPreferencesRepository.displayCurrency.first()
            val currency = if (unifiedMode) displayCurrency else baseCurrency
            val currencies = CurrencyFormatter.getSupportedCurrencies()

            val today = LocalDate.now()
            val yearMonth = YearMonth.from(today)
            val startDate = yearMonth.atDay(1).atStartOfDay()
            val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

            // Default the new-budget start date to the user's current budget
            // cycle start (e.g. Sep 25 if today is Oct 5 and startDay=25) so
            // a fresh budget lands in the same window the home screen shows.
            // Editing an existing budget overrides this below.
            val cycleStartDay = userPreferencesRepository.getBudgetCycleStartDay()
            val defaultStart = BudgetCycle.currentCycleStartYearMonth(today, cycleStartDay).atDay(1)
            val defaultEnd = BudgetRepository.endDateFor(defaultStart, BudgetPeriodType.MONTHLY)

            val transactions = transactionSplitDao.getTransactionsWithSplitsFiltered(startDate, endDate, currency).first()
            // Mirror the main budget screen's routing: type-bucket-eligible
            // outflows (INVESTMENT) are summed per type, everything else per
            // category. Keeps the edit screen's "current spending" hint accurate
            // for a type bucket whose transactions aren't all tagged "Investments".
            val categorySpending = mutableMapOf<String, BigDecimal>()
            val typeSpending = mutableMapOf<String, BigDecimal>()
            transactions.forEach { txWithSplits ->
                val type = txWithSplits.transaction.transactionType
                if (type == TransactionType.INCOME || type == TransactionType.TRANSFER) return@forEach
                if (type in BudgetGroupRepository.BUDGET_TYPE_BUCKETS) {
                    typeSpending[type.name] = (typeSpending[type.name] ?: BigDecimal.ZERO) +
                        txWithSplits.transaction.amount
                } else {
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
                            currentSpending = if (it.matchType != null) {
                                typeSpending[it.matchType] ?: BigDecimal.ZERO
                            } else {
                                categorySpending[it.categoryName] ?: BigDecimal.ZERO
                            },
                            matchType = it.matchType
                        )
                    }
                    _uiState.value = BudgetGroupEditUiState(
                        groupId = groupId,
                        name = group.budget.name,
                        overallAmount = if (group.budget.limitAmount.compareTo(BigDecimal.ZERO) == 0) "" else group.budget.limitAmount.toPlainString(),
                        categories = assignedCategories,
                        categorySpending = categorySpending,
                        typeSpending = typeSpending,
                        currency = group.budget.currency,
                        availableCurrencies = currencies,
                        // Editing: trust the budget's own persisted dates and
                        // period type. The end date is just persisted metadata
                        // — re-derive it from start + periodType so the
                        // read-only label matches the live calc, even if an
                        // older row's end_date is slightly off (e.g. a budget
                        // created before this feature was added).
                        startDate = group.budget.startDate,
                        endDate = BudgetRepository.endDateFor(group.budget.startDate, group.budget.periodType),
                        periodType = group.budget.periodType,
                        isLoading = false
                    )
                }
            } else {
                _uiState.value = BudgetGroupEditUiState(
                    name = "Monthly Budget",
                    currency = currency,
                    categorySpending = categorySpending,
                    typeSpending = typeSpending,
                    availableCurrencies = currencies,
                    // Creating: seed the start to the current cycle start so
                    // the budget lands in the same window the home screen
                    // shows, with the end auto-derived.
                    startDate = defaultStart,
                    endDate = defaultEnd,
                    periodType = BudgetPeriodType.MONTHLY,
                    isLoading = false
                )
            }

            loadAvailableCategories()
        }
    }

    private fun loadAvailableCategories() {
        viewModelScope.launch {
            categoryRepository.getExpenseCategories().collect { expenseCategories ->
                val current = _uiState.value.categories
                val currentNames = current.map { it.categoryName }
                // A type bucket's display label (e.g. "Investments") is offered as a
                // type bucket, not a category — so hide those category names to avoid
                // the (budget_id, category_name) unique-index collision.
                val typeLabels = ALL_TYPE_BUCKETS.map { it.displayName }
                val available = expenseCategories
                    .map { it.name }
                    .filter { it !in currentNames && it !in typeLabels }
                _uiState.value = _uiState.value.copy(
                    availableCategories = available,
                    availableTypeBuckets = ALL_TYPE_BUCKETS.filter { opt ->
                        current.none { it.matchType == opt.typeName }
                    }
                )
            }
        }
    }

    fun addTypeBucket(option: TypeBucketOption) {
        val current = _uiState.value.categories.toMutableList()
        current.add(
            CategoryBudgetItem(
                categoryName = option.displayName,
                amount = BigDecimal.ZERO,
                currentSpending = _uiState.value.typeSpending[option.typeName] ?: BigDecimal.ZERO,
                matchType = option.typeName
            )
        )
        _uiState.value = _uiState.value.copy(
            categories = current,
            availableTypeBuckets = _uiState.value.availableTypeBuckets - option
        )
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
        val removed = current.firstOrNull { it.categoryName == categoryName }
        current.removeAll { it.categoryName == categoryName }
        val restoredType = removed?.matchType?.let { mt -> ALL_TYPE_BUCKETS.firstOrNull { it.typeName == mt } }
        _uiState.value = _uiState.value.copy(
            categories = current,
            // Restore a removed category to the picker; a removed type bucket goes
            // back to the type-bucket options instead.
            availableCategories = if (removed?.matchType == null) {
                _uiState.value.availableCategories + categoryName
            } else _uiState.value.availableCategories,
            availableTypeBuckets = if (restoredType != null) {
                _uiState.value.availableTypeBuckets + restoredType
            } else _uiState.value.availableTypeBuckets
        )
    }

    fun updateCategoryAmount(categoryName: String, amount: BigDecimal) {
        val current = _uiState.value.categories.map {
            if (it.categoryName == categoryName) it.copy(amount = amount) else it
        }
        _uiState.value = _uiState.value.copy(categories = current)
    }

    /**
     * Updates the budget's start date and re-derives the end date for the
     * currently-selected period type. The end date is read-only, so this is
     * the only place that mutates either — the screen just renders the
     * recomputed value.
     */
    fun updateStartDate(date: LocalDate) {
        val period = _uiState.value.periodType
        _uiState.value = _uiState.value.copy(
            startDate = date,
            endDate = BudgetRepository.endDateFor(date, period)
        )
    }

    /**
     * Switches the period type (Weekly / Monthly / Custom) and re-derives the
     * end date from the currently-set start date. Switching from a 1-month
     * window to a 1-week window is intentional — the user explicitly chose
     * the cadence and the start anchor they want.
     */
    fun updatePeriodType(period: BudgetPeriodType) {
        val start = _uiState.value.startDate
        _uiState.value = _uiState.value.copy(
            periodType = period,
            endDate = BudgetRepository.endDateFor(start, period)
        )
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return
        val overallAmount = state.overallAmount.toBigDecimalOrNull() ?: return
        if (overallAmount <= BigDecimal.ZERO) return

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            val buckets = state.categories.map {
                BudgetBucketInput(name = it.categoryName, amount = it.amount, matchType = it.matchType)
            }
            val defaultColor = "#1565C0"

            if (state.groupId != null && state.groupId > 0) {
                budgetGroupRepository.updateGroup(
                    budgetId = state.groupId,
                    name = state.name,
                    groupType = BudgetGroupType.LIMIT,
                    color = defaultColor,
                    buckets = buckets,
                    currency = state.currency,
                    limitAmount = overallAmount,
                    // Pass the per-budget dates through; the repo recomputes
                    // endDate from the budget's existing periodType.
                    customStartDate = state.startDate,
                    periodType = state.periodType
                )
            } else {
                budgetGroupRepository.createGroup(
                    name = state.name,
                    groupType = BudgetGroupType.LIMIT,
                    color = defaultColor,
                    buckets = buckets,
                    currency = state.currency,
                    limitAmount = overallAmount,
                    customStartDate = state.startDate,
                    periodType = state.periodType
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

    companion object {
        /** Transaction-type buckets a budget can track (display order). */
        private val ALL_TYPE_BUCKETS = listOf(
            TypeBucketOption(TransactionType.INVESTMENT.name, "Investments")
        )
    }
}
