package com.pennywiseai.tracker.presentation.monthlybudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.CategoryBudgetLimitEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.CategorySpendingInfo
import com.pennywiseai.tracker.data.repository.MonthlyBudgetRepository
import com.pennywiseai.tracker.data.repository.MonthlyBudgetSpending
import com.pennywiseai.tracker.data.repository.MonthlyBudgetSpendingRaw
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
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
    val currency: String = "INR",
    val baseCurrency: String = "INR",
    val isUnifiedMode: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyBudgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
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
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                monthlyBudgetRepository.monthlyBudgetLimit,
                monthlyBudgetRepository.getCategoryLimits()
            ) { baseCurrency, unifiedMode, displayCurrency, limit, categoryLimits ->
                val currency = if (unifiedMode) displayCurrency else baseCurrency
                data class BudgetMetadata(
                    val currency: String,
                    val baseCurrency: String,
                    val unifiedMode: Boolean,
                    val limit: BigDecimal?,
                    val categoryLimits: List<CategoryBudgetLimitEntity>
                )
                BudgetMetadata(currency, baseCurrency, unifiedMode, limit, categoryLimits)
            }.combine(_selectedYearMonth) { metadata, yearMonth ->
                metadata to yearMonth
            }.collect { (metadata, yearMonth) ->
                _uiState.value = _uiState.value.copy(
                    monthlyLimit = metadata.limit,
                    categoryLimits = metadata.categoryLimits,
                    currency = metadata.currency,
                    baseCurrency = metadata.baseCurrency,
                    isUnifiedMode = metadata.unifiedMode,
                    selectedYear = yearMonth.year,
                    selectedMonth = yearMonth.monthValue
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
                    monthlyBudgetRepository.getMonthSpendingAllCurrencies(params.yearMonth.year, params.yearMonth.monthValue)
                        .mapToConvertedSpending(params.displayCurrency, params.baseCurrency)
                } else {
                    monthlyBudgetRepository.getMonthSpending(params.yearMonth.year, params.yearMonth.monthValue, params.displayCurrency)
                }
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

    /**
     * Convert raw all-currencies spending to MonthlyBudgetSpending
     * by converting each transaction's amount to the display currency.
     */
    private fun kotlinx.coroutines.flow.Flow<MonthlyBudgetSpendingRaw>.mapToConvertedSpending(
        displayCurrency: String,
        baseCurrency: String
    ): kotlinx.coroutines.flow.Flow<MonthlyBudgetSpending> = this.map { raw ->
        // Convert budget limits from base currency to display currency
        val convertedTotalLimit = currencyConversionService.convertAmount(raw.totalLimit, baseCurrency, displayCurrency)
        val categoryLimitsMap = raw.categoryLimits.associate { it.categoryName to
            currencyConversionService.convertAmount(it.limitAmount, baseCurrency, displayCurrency)
        }

        val expenseTransactions = raw.allTransactions.filter {
            it.transaction.transactionType == TransactionType.EXPENSE ||
                it.transaction.transactionType == TransactionType.CREDIT
        }
        val incomeTransactions = raw.allTransactions.filter {
            it.transaction.transactionType == TransactionType.INCOME
        }

        // Convert income amounts
        var totalIncome = BigDecimal.ZERO
        for (tx in incomeTransactions) {
            val converted = currencyConversionService.convertAmount(
                tx.transaction.amount, tx.transaction.currency, displayCurrency
            )
            totalIncome = totalIncome.add(converted)
        }

        // Convert expense amounts and group by category
        val categoryAmounts = mutableMapOf<String, BigDecimal>()
        for (txWithSplits in expenseTransactions) {
            val fromCurrency = txWithSplits.transaction.currency
            txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                val converted = currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
                val categoryName = category.ifEmpty { "Others" }
                categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
            }
        }

        val totalSpent = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val remaining = convertedTotalLimit - totalSpent
        val percentageUsed = if (convertedTotalLimit > BigDecimal.ZERO) {
            (totalSpent.toFloat() / convertedTotalLimit.toFloat() * 100f).coerceAtLeast(0f)
        } else 0f

        val dailyAllowance = if (raw.daysRemaining > 0 && remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val allCategories = (categoryLimitsMap.keys + categoryAmounts.keys).distinct()
        val categorySpending = allCategories.map { categoryName ->
            val spent = categoryAmounts[categoryName] ?: BigDecimal.ZERO
            val catLimit = categoryLimitsMap[categoryName]
            val catPercentage = if (catLimit != null && catLimit > BigDecimal.ZERO) {
                (spent.toFloat() / catLimit.toFloat() * 100f).coerceAtLeast(0f)
            } else null
            val catDailySpend = if (raw.daysElapsed > 0 && spent > BigDecimal.ZERO) {
                spent.divide(BigDecimal(raw.daysElapsed), 0, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            val catRemaining = if (catLimit != null) catLimit - spent else null
            val catDailyAllowance = if (catLimit != null && raw.daysRemaining > 0 && catRemaining != null && catRemaining > BigDecimal.ZERO) {
                catRemaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
            } else null
            CategorySpendingInfo(
                categoryName = categoryName,
                spent = spent,
                limit = catLimit,
                percentageUsed = catPercentage,
                dailySpend = catDailySpend,
                dailyAllowance = catDailyAllowance
            )
        }.sortedWith(compareByDescending<CategorySpendingInfo> { it.limit != null }.thenByDescending { it.spent })

        val netSavings = totalIncome - totalSpent
        val savingsRate = if (totalIncome > BigDecimal.ZERO) {
            (netSavings.toFloat() / totalIncome.toFloat() * 100f)
        } else 0f

        // Convert previous month transactions
        var prevIncome = BigDecimal.ZERO
        for (tx in raw.prevTransactions.filter { it.transaction.transactionType == TransactionType.INCOME }) {
            prevIncome = prevIncome.add(
                currencyConversionService.convertAmount(tx.transaction.amount, tx.transaction.currency, displayCurrency)
            )
        }
        var prevExpenses = BigDecimal.ZERO
        for (tx in raw.prevTransactions.filter {
            it.transaction.transactionType == TransactionType.EXPENSE || it.transaction.transactionType == TransactionType.CREDIT
        }) {
            prevExpenses = prevExpenses.add(
                currencyConversionService.convertAmount(tx.transaction.amount, tx.transaction.currency, displayCurrency)
            )
        }
        val prevSavings = prevIncome - prevExpenses
        val savingsDelta = if (prevIncome > BigDecimal.ZERO || totalIncome > BigDecimal.ZERO) {
            netSavings - prevSavings
        } else null

        MonthlyBudgetSpending(
            totalLimit = convertedTotalLimit,
            totalSpent = totalSpent,
            remaining = remaining,
            percentageUsed = percentageUsed,
            categorySpending = categorySpending,
            daysRemaining = raw.daysRemaining,
            dailyAllowance = dailyAllowance,
            totalIncome = totalIncome,
            netSavings = netSavings,
            savingsRate = savingsRate,
            savingsDelta = savingsDelta
        )
    }

    fun setMonthlyLimit(amount: BigDecimal) {
        viewModelScope.launch {
            monthlyBudgetRepository.setMonthlyBudgetLimit(amount)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun setCategoryLimit(categoryName: String, amount: BigDecimal) {
        viewModelScope.launch {
            monthlyBudgetRepository.setCategoryLimit(categoryName, amount)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun removeCategoryLimit(categoryName: String) {
        viewModelScope.launch {
            monthlyBudgetRepository.removeCategoryLimit(categoryName)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    fun removeBudget() {
        viewModelScope.launch {
            monthlyBudgetRepository.setMonthlyBudgetLimit(null)
            com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
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
