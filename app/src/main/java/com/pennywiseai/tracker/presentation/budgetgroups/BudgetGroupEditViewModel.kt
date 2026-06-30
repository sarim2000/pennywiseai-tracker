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
    /**
     * The cadence this budget runs on.
     *
     *  - WEEKLY  — recurs every week starting on [weekStartDay] (1=Mon..7=Sun).
     *              [startDate] / [endDate] are read-only and show the *current*
     *              week window.
     *  - MONTHLY — recurs every month starting on [monthStartDay] (1..31).
     *              [startDate] / [endDate] are read-only and show the *current*
     *              cycle window.
     *  - CUSTOM  — one-time. [startDate] / [endDate] are user-picked literals
     *              and don't roll.
     */
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    /** WEEKLY only. 1=Mon..7=Sun. */
    val weekStartDay: Int = 1,
    /** MONTHLY only. 1..31, clamped to the month's length at resolve time. */
    val monthStartDay: Int = 1,
    /** For CUSTOM: the literal start date. For other period types: read-only cache of the current window's start. */
    val startDate: LocalDate = LocalDate.now(),
    /** For CUSTOM: the literal end date. For other period types: read-only cache of the current window's end. */
    val endDate: LocalDate = LocalDate.now(),
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

            // The "spending hint" panel on the right of the budget amount
            // ("Spent: $X") is computed from the calendar month regardless
            // of cadence — it's a quick glance, not the authoritative spend
            // figure. The authoritative spend figure is what the home card
            // shows, which honours the budget's own period window.
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
                    val b = group.budget
                    _uiState.value = BudgetGroupEditUiState(
                        groupId = groupId,
                        name = b.name,
                        overallAmount = if (b.limitAmount.compareTo(BigDecimal.ZERO) == 0) "" else b.limitAmount.toPlainString(),
                        categories = assignedCategories,
                        categorySpending = categorySpending,
                        typeSpending = typeSpending,
                        currency = b.currency,
                        availableCurrencies = currencies,
                        // Editing: trust the budget's own persisted period
                        // type + anchor + dates. We re-derive the displayed
                        // [startDate, endDate] window for WEEKLY / MONTHLY
                        // from the anchor + today so the read-only label
                        // always shows the *current* window — even for a
                        // row created weeks/months ago.
                        periodType = b.periodType,
                        weekStartDay = b.weekStartDay?.coerceIn(1, 7) ?: 1,
                        monthStartDay = b.monthStartDay ?: userPreferencesRepository.getBudgetCycleStartDay(),
                        startDate = currentWindowStart(b, today, userPreferencesRepository.getBudgetCycleStartDay()),
                        endDate = currentWindowEnd(b, today, userPreferencesRepository.getBudgetCycleStartDay()),
                        isLoading = false
                    )
                }
            } else {
                val cycleStartDay = userPreferencesRepository.getBudgetCycleStartDay()
                val initialMonthStart = cycleStartDay
                val (initialStart, initialEnd) = defaultMonthlyWindow(today, initialMonthStart)
                _uiState.value = BudgetGroupEditUiState(
                    name = "Monthly Budget",
                    currency = currency,
                    categorySpending = categorySpending,
                    typeSpending = typeSpending,
                    availableCurrencies = currencies,
                    // Creating: default to Monthly anchored to the user's
                    // global budget cycle start day. The user can flip the
                    // chip to Weekly or One-time and the form rebuilds.
                    periodType = BudgetPeriodType.MONTHLY,
                    weekStartDay = 1,
                    monthStartDay = initialMonthStart,
                    startDate = initialStart,
                    endDate = initialEnd,
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
     * Switches the period type. The form below the chip row rebuilds to
     * match — Weekly shows a day-of-week dropdown, Monthly shows a
     * day-of-month stepper, One-time shows two date pickers.
     */
    fun updatePeriodType(period: BudgetPeriodType) {
        val today = LocalDate.now()
        val current = _uiState.value
        val (newStart, newEnd) = when (period) {
            BudgetPeriodType.WEEKLY -> {
                val s = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(current.weekStartDay)))
                s to s.plusDays(6)
            }
            BudgetPeriodType.MONTHLY -> BudgetCycle.currentCycle(today, current.monthStartDay)
            BudgetPeriodType.CUSTOM -> {
                // Switching into One-time: keep the currently-displayed window
                // as the user's starting point. They can edit both dates freely.
                current.startDate to current.endDate
            }
        }
        _uiState.value = current.copy(
            periodType = period,
            startDate = newStart,
            endDate = newEnd
        )
    }

    /** WEEKLY: which day-of-week the week starts on (1=Mon..7=Sun). */
    fun updateWeekStartDay(day: Int) {
        val safe = day.coerceIn(1, 7)
        val today = LocalDate.now()
        val start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(safe)))
        _uiState.value = _uiState.value.copy(
            weekStartDay = safe,
            startDate = start,
            endDate = start.plusDays(6)
        )
    }

    /** MONTHLY: which day-of-month the cycle starts on (1..31). */
    fun updateMonthStartDay(day: Int) {
        val safe = BudgetCycle.clampStartDay(day)
        val today = LocalDate.now()
        val (s, e) = BudgetCycle.currentCycle(today, safe)
        _uiState.value = _uiState.value.copy(
            monthStartDay = safe,
            startDate = s,
            endDate = e
        )
    }

    /**
     * One-time only: update the literal start date. If the user picks a
     * date on or after the current end, we push the end one day further
     * so the window stays well-formed.
     */
    fun updateStartDate(date: LocalDate) {
        val current = _uiState.value
        if (current.periodType != BudgetPeriodType.CUSTOM) return
        val newEnd = if (!date.isBefore(current.endDate)) date.plusDays(1) else current.endDate
        _uiState.value = current.copy(startDate = date, endDate = newEnd)
    }

    /**
     * One-time only: update the literal end date. The end must be strictly
     * after the start; if the user picks a date on or before the start we
     * snap it to start+1 day.
     */
    fun updateEndDate(date: LocalDate) {
        val current = _uiState.value
        if (current.periodType != BudgetPeriodType.CUSTOM) return
        val safe = if (!date.isAfter(current.startDate)) current.startDate.plusDays(1) else date
        _uiState.value = current.copy(endDate = safe)
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
                    periodType = state.periodType,
                    weekStartDay = state.weekStartDay.takeIf { state.periodType == BudgetPeriodType.WEEKLY },
                    monthStartDay = state.monthStartDay.takeIf { state.periodType == BudgetPeriodType.MONTHLY },
                    startDate = state.startDate.takeIf { state.periodType == BudgetPeriodType.CUSTOM },
                    endDate = state.endDate.takeIf { state.periodType == BudgetPeriodType.CUSTOM }
                )
            } else {
                budgetGroupRepository.createGroup(
                    name = state.name,
                    groupType = BudgetGroupType.LIMIT,
                    color = defaultColor,
                    buckets = buckets,
                    currency = state.currency,
                    limitAmount = overallAmount,
                    periodType = state.periodType,
                    weekStartDay = state.weekStartDay.takeIf { state.periodType == BudgetPeriodType.WEEKLY },
                    monthStartDay = state.monthStartDay.takeIf { state.periodType == BudgetPeriodType.MONTHLY },
                    startDate = state.startDate.takeIf { state.periodType == BudgetPeriodType.CUSTOM },
                    endDate = state.endDate.takeIf { state.periodType == BudgetPeriodType.CUSTOM }
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

        /**
         * Default Monthly window for a brand-new budget. Anchored to
         * [monthStartDay] (the user's global budget cycle start day pref).
         */
        fun defaultMonthlyWindow(today: LocalDate, monthStartDay: Int): Pair<LocalDate, LocalDate> =
            BudgetCycle.currentCycle(today, BudgetCycle.clampStartDay(monthStartDay))

        /**
         * Resolve the *current* window's start for an existing budget,
         * honouring its period type and anchor. Used by the edit screen to
         * keep the read-only "Ends on …" label accurate even for rows
         * created weeks/months ago (whose persisted [startDate, endDate]
         * cache is stale).
         */
        fun currentWindowStart(
            budget: com.pennywiseai.tracker.data.database.entity.BudgetEntity,
            today: LocalDate,
            globalStartDay: Int
        ): LocalDate = when (budget.periodType) {
            BudgetPeriodType.WEEKLY -> today.with(
                java.time.temporal.TemporalAdjusters.previousOrSame(
                    java.time.DayOfWeek.of(budget.weekStartDay?.coerceIn(1, 7) ?: 1)
                )
            )
            BudgetPeriodType.MONTHLY -> BudgetCycle.currentCycle(
                today,
                budget.monthStartDay?.let { BudgetCycle.clampStartDay(it) }
                    ?: BudgetCycle.clampStartDay(globalStartDay)
            ).first
            BudgetPeriodType.CUSTOM -> budget.startDate
        }

        /** Mirror of [currentWindowStart] returning the end. */
        fun currentWindowEnd(
            budget: com.pennywiseai.tracker.data.database.entity.BudgetEntity,
            today: LocalDate,
            globalStartDay: Int
        ): LocalDate = when (budget.periodType) {
            BudgetPeriodType.WEEKLY -> currentWindowStart(budget, today, globalStartDay).plusDays(6)
            BudgetPeriodType.MONTHLY -> BudgetCycle.currentCycle(
                today,
                budget.monthStartDay?.let { BudgetCycle.clampStartDay(it) }
                    ?: BudgetCycle.clampStartDay(globalStartDay)
            ).second
            BudgetPeriodType.CUSTOM -> budget.endDate
        }
    }
}
