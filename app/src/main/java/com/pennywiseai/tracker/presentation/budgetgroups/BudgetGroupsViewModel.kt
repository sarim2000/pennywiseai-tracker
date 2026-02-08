package com.pennywiseai.tracker.presentation.budgetgroups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.MonthlyBudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetGroupsUiState(
    val summary: BudgetOverallSummary? = null,
    val isLoading: Boolean = true,
    val hasGroups: Boolean = false,
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val currency: String = "INR",
    val baseCurrency: String = "INR",
    val isUnifiedMode: Boolean = false,
    val needsMigration: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetGroupsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetGroupsUiState())
    val uiState: StateFlow<BudgetGroupsUiState> = _uiState.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    init {
        checkMigration()
        loadBudgetData()
    }

    private fun checkMigration() {
        viewModelScope.launch {
            val hasMigrated = userPreferencesRepository.hasMigratedToBudgetGroups.first()
            if (!hasMigrated) {
                val hasGroups = budgetGroupRepository.hasAnyGroups()
                if (!hasGroups) {
                    val oldLimit = userPreferencesRepository.monthlyBudgetLimit.first()
                    val oldCategoryLimits = monthlyBudgetRepository.getCategoryLimits().first()
                    if (oldLimit != null || oldCategoryLimits.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(needsMigration = true)
                    }
                }
                userPreferencesRepository.setHasMigratedToBudgetGroups(true)
            }
        }
    }

    private fun loadBudgetData() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                _selectedYearMonth
            ) { baseCurrency, unifiedMode, displayCurrency, yearMonth ->
                data class Params(val baseCurrency: String, val unifiedMode: Boolean, val displayCurrency: String, val yearMonth: YearMonth)
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
            combine(
                userPreferencesRepository.baseCurrency,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                _selectedYearMonth
            ) { baseCurrency, unifiedMode, displayCurrency, yearMonth ->
                data class SpendingParams(val displayCurrency: String, val baseCurrency: String, val unifiedMode: Boolean, val yearMonth: YearMonth)
                SpendingParams(if (unifiedMode) displayCurrency else baseCurrency, baseCurrency, unifiedMode, yearMonth)
            }.flatMapLatest { params ->
                if (params.unifiedMode) {
                    budgetGroupRepository.getGroupSpendingAllCurrencies(params.yearMonth.year, params.yearMonth.monthValue)
                        .map { raw -> convertRawToSummary(raw, params.displayCurrency, params.baseCurrency) }
                } else {
                    budgetGroupRepository.getGroupSpending(params.yearMonth.year, params.yearMonth.monthValue, params.displayCurrency)
                }
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(
                    summary = summary,
                    hasGroups = summary.groups.isNotEmpty(),
                    isLoading = false
                )
            }
        }
    }

    private suspend fun convertRawToSummary(
        raw: BudgetGroupSpendingRaw,
        displayCurrency: String,
        baseCurrency: String
    ): BudgetOverallSummary {
        val incomeTransactions = raw.allTransactions.filter {
            it.transaction.transactionType == TransactionType.INCOME
        }
        var totalIncome = BigDecimal.ZERO
        for (tx in incomeTransactions) {
            totalIncome = totalIncome.add(
                currencyConversionService.convertAmount(tx.transaction.amount, tx.transaction.currency, displayCurrency)
            )
        }

        // Build category amounts from all non-income transactions, converting currencies
        val categoryAmounts = mutableMapOf<String, BigDecimal>()
        raw.allTransactions.forEach { txWithSplits ->
            if (txWithSplits.transaction.transactionType != TransactionType.INCOME) {
                val fromCurrency = txWithSplits.transaction.currency
                txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                    val converted = currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
                    val categoryName = category.ifEmpty { "Others" }
                    categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                }
            }
        }

        val groupSpendingList = raw.budgetsWithCategories.map { group ->
            val catSpending = group.categories.map { cat ->
                val actual = categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                val convertedBudget = currencyConversionService.convertAmount(cat.budgetAmount, baseCurrency, displayCurrency)
                val pctUsed = if (convertedBudget > BigDecimal.ZERO) {
                    (actual.toFloat() / convertedBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailySpend = if (raw.daysElapsed > 0 && actual > BigDecimal.ZERO) {
                    actual.divide(BigDecimal(raw.daysElapsed), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                BudgetCategorySpending(
                    categoryName = cat.categoryName,
                    budgetAmount = convertedBudget,
                    actualAmount = actual,
                    percentageUsed = pctUsed,
                    dailySpend = dailySpend
                )
            }
            val totalBudget = catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
            val totalActual = catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
            val remaining = totalBudget - totalActual
            val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
            } else 0f
            val dailyAllowance = if (raw.daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                remaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            BudgetGroupSpending(
                group = group,
                categorySpending = catSpending,
                totalBudget = totalBudget,
                totalActual = totalActual,
                remaining = remaining,
                percentageUsed = pctUsed,
                dailyAllowance = dailyAllowance,
                daysRemaining = raw.daysRemaining,
                daysElapsed = raw.daysElapsed
            )
        }

        val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        val targetGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
        val expectedGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }

        val totalLimitBudget = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalTargetGoal = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalTargetActual = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalExpectedBudget = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalExpectedActual = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }

        val netSavings = totalIncome - totalLimitSpent
        val savingsRate = if (totalIncome > BigDecimal.ZERO) {
            (netSavings.toFloat() / totalIncome.toFloat() * 100f)
        } else 0f

        val limitRemaining = totalLimitBudget - totalLimitSpent
        val dailyAllowance = if (raw.daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
            limitRemaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return BudgetOverallSummary(
            groups = groupSpendingList,
            totalIncome = totalIncome,
            totalLimitBudget = totalLimitBudget,
            totalLimitSpent = totalLimitSpent,
            totalTargetGoal = totalTargetGoal,
            totalTargetActual = totalTargetActual,
            totalExpectedBudget = totalExpectedBudget,
            totalExpectedActual = totalExpectedActual,
            netSavings = netSavings,
            savingsRate = savingsRate,
            dailyAllowance = dailyAllowance,
            daysRemaining = raw.daysRemaining,
            currency = displayCurrency
        )
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

    fun deleteGroup(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.deleteGroup(budgetId)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun runSmartDefaults() {
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            budgetGroupRepository.createSmartDefaults(baseCurrency)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun migrateOldBudget() {
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val oldLimit = userPreferencesRepository.monthlyBudgetLimit.first()
            val oldCategoryLimits = monthlyBudgetRepository.getCategoryLimits().first()
            budgetGroupRepository.migrateFromOldBudget(oldLimit, oldCategoryLimits, baseCurrency)
            _uiState.value = _uiState.value.copy(needsMigration = false)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun moveGroupUp(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.moveGroupUp(budgetId)
        }
    }

    fun moveGroupDown(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.moveGroupDown(budgetId)
        }
    }
}
