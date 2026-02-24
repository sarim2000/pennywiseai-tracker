package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.presentation.common.CurrencyGroupedTotals
import com.pennywiseai.tracker.presentation.common.CurrencyTotals
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.utils.CurrencyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionSplitDao: TransactionSplitDao,
    private val userPreferencesRepository: com.pennywiseai.tracker.data.preferences.UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()
    
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    // Multiple categories filter (for budget navigation)
    private val _categoriesFilter = MutableStateFlow<List<String>?>(null)
    val categoriesFilter: StateFlow<List<String>?> = _categoriesFilter.asStateFlow()

    private val _transactionTypeFilter = MutableStateFlow(TransactionTypeFilter.ALL)
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = _transactionTypeFilter.asStateFlow()
    
    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("INR") // Will be initialized from preferences
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    private val _isUnifiedMode = MutableStateFlow(false)
    val isUnifiedMode: StateFlow<Boolean> = _isUnifiedMode.asStateFlow()

    // Map of transactionId -> converted amount in display currency (for unified mode)
    private val _convertedAmounts = MutableStateFlow<Map<Long, BigDecimal>>(emptyMap())
    val convertedAmounts: StateFlow<Map<Long, BigDecimal>> = _convertedAmounts.asStateFlow()

    // Store custom date range as epoch days to survive process death
    // Stored as Pair<Long, Long> (startEpochDay, endEpochDay) in SavedStateHandle
    private val _customDateRangeEpochDays = savedStateHandle.getStateFlow<Pair<Long, Long>?>("customDateRange", null)

    // Expose as LocalDate pair for convenience
    val customDateRange: StateFlow<Pair<LocalDate, LocalDate>?> = _customDateRangeEpochDays
        .map { epochDays ->
            epochDays?.let { (startEpochDay, endEpochDay) ->
                LocalDate.ofEpochDay(startEpochDay) to LocalDate.ofEpochDay(endEpochDay)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()
    
    private val _currencyGroupedTotals = MutableStateFlow(CurrencyGroupedTotals())
    val currencyGroupedTotals: StateFlow<CurrencyGroupedTotals> = _currencyGroupedTotals.asStateFlow()

    // Available currencies for the selected time period
    val availableCurrencies: StateFlow<List<String>> = combine(selectedPeriod, customDateRange) { period, customRange ->
        period to customRange
    }.flatMapLatest { (period, customRange) ->
        if (period == TimePeriod.ALL) {
            transactionRepository.getAllCurrencies()
        } else if (period == TimePeriod.CUSTOM && customRange != null) {
            val (startDate, endDate) = customRange
            val startDateTime = startDate.atStartOfDay()
            val endDateTime = endDate.atTime(23, 59, 59)
            transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
        } else {
            val dateRange = getDateRangeForPeriod(period)
            if (dateRange != null) {
                val (startDate, endDate) = dateRange
                val startDateTime = startDate.atStartOfDay()
                val endDateTime = endDate.atTime(23, 59, 59)
                transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
            } else {
                transactionRepository.getAllCurrencies()
            }
        }
    }
        .map { currencies ->
            currencies.sortedWith { a, b ->
                when {
                    a == "INR" -> -1 // INR first
                    b == "INR" -> 1
                    else -> a.compareTo(b) // Alphabetical for others
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Computed property for current selected currency totals
    val filteredTotals: StateFlow<FilteredTotals> = combine(
        _currencyGroupedTotals,
        _selectedCurrency,
        _isUnifiedMode
    ) { groupedTotals, currency, isUnified ->
        Triple(groupedTotals, currency, isUnified)
    }.mapLatest { (groupedTotals, currency, isUnified) ->
        if (isUnified && groupedTotals.totalsByCurrency.size > 1) {
            // Aggregate all currencies converted to display currency
            var income = BigDecimal.ZERO
            var expenses = BigDecimal.ZERO
            var credit = BigDecimal.ZERO
            var transfer = BigDecimal.ZERO
            var investment = BigDecimal.ZERO
            var count = 0
            for ((cur, totals) in groupedTotals.totalsByCurrency) {
                if (cur == currency) {
                    income += totals.income
                    expenses += totals.expenses
                    credit += totals.credit
                    transfer += totals.transfer
                    investment += totals.investment
                } else {
                    income += currencyConversionService.convertAmount(totals.income, cur, currency)
                    expenses += currencyConversionService.convertAmount(totals.expenses, cur, currency)
                    credit += currencyConversionService.convertAmount(totals.credit, cur, currency)
                    transfer += currencyConversionService.convertAmount(totals.transfer, cur, currency)
                    investment += currencyConversionService.convertAmount(totals.investment, cur, currency)
                }
                count += totals.transactionCount
            }
            val netBalance = income - expenses - credit - transfer - investment
            FilteredTotals(income, expenses, credit, transfer, investment, netBalance, count)
        } else {
            val currencyTotals = groupedTotals.getTotalsForCurrency(currency)
            FilteredTotals(
                income = currencyTotals.income,
                expenses = currencyTotals.expenses,
                credit = currencyTotals.credit,
                transfer = currencyTotals.transfer,
                investment = currencyTotals.investment,
                netBalance = currencyTotals.netBalance,
                transactionCount = currencyTotals.transactionCount
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilteredTotals()
    )
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()
    
    // Track if initial filters have been applied to prevent resetting on back navigation
    private var hasAppliedInitialFilters = false

    // Track the navigation params that were initially applied, to detect actual navigation changes
    private var appliedNavigationParams: NavigationParams? = null

    // Track budget navigation params similarly
    private var appliedBudgetParams: BudgetParams? = null
    
    // Categories flow - will be used to map category names to colors
    val categories: StateFlow<Map<String, CategoryEntity>> = categoryRepository.getAllCategories()
        .map { categoryList ->
            categoryList.associateBy { it.name }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    // SMS scan period for info banner
    val smsScanMonths: StateFlow<Int> = userPreferencesRepository.smsScanMonths
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 3
        )

    private val smsScanAllTime: StateFlow<Boolean> = userPreferencesRepository.smsScanAllTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun isShowingLimitedData(): Boolean {
        if (smsScanAllTime.value) return false

        val currentPeriod = _selectedPeriod.value
        val scanMonthsValue = smsScanMonths.value

        return when (currentPeriod) {
            TimePeriod.ALL -> true
            TimePeriod.CURRENT_FY -> {
                // Check if FY start is before scan period
                val dateRange = getDateRangeForPeriod(TimePeriod.CURRENT_FY)
                if (dateRange != null) {
                    val (fyStart, _) = dateRange
                    val scanStart = LocalDate.now().minusMonths(scanMonthsValue.toLong())
                    fyStart.isBefore(scanStart)
                } else {
                    false
                }
            }
            TimePeriod.CUSTOM -> {
                // Check if custom range start is before scan period
                val customRange = customDateRange.value
                if (customRange != null) {
                    val (startDate, _) = customRange
                    val scanStart = LocalDate.now().minusMonths(scanMonthsValue.toLong())
                    startDate.isBefore(scanStart)
                } else {
                    false
                }
            }
            else -> false
        }
    }
    
    init {
        // Initialize selectedCurrency from baseCurrency preference
        viewModelScope.launch {
        }

        // Load unified mode preferences
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { unifiedMode, displayCurrency ->
                unifiedMode to displayCurrency
            }.collect { (unifiedMode, displayCurrency) ->
                _isUnifiedMode.value = unifiedMode
                if (unifiedMode) {
                    _selectedCurrency.value = displayCurrency
                } else {
                    _selectedCurrency.value = baseCurrency
                }
            }
        }

        // Manually combine all flows using transformLatest
        merge(
            searchQuery.debounce(300).map { "search" },
            selectedPeriod.map { "period" },
            categoryFilter.map { "category" },
            categoriesFilter.map { "categories" },
            transactionTypeFilter.map { "typeFilter" },
            selectedCurrency.map { "currency" },
            _isUnifiedMode.map { "unifiedMode" },
            sortOption.map { "sort" },
            customDateRange.map { "customDate" }
        )
            .transformLatest { trigger ->
                // Get current values from all StateFlows
                val query = searchQuery.value
                val period = selectedPeriod.value
                val category = categoryFilter.value
                val categories = categoriesFilter.value
                val typeFilter = transactionTypeFilter.value
                val sort = sortOption.value
                val isUnified = _isUnifiedMode.value

                // Get filtered transactions (without currency filter first)
                getFilteredTransactions(query, period, category, categories, typeFilter)
                    .collect { transactions ->
                        if (isUnified) {
                            // Show all transactions regardless of currency
                            emit(sortTransactions(transactions, sort))
                        } else {
                            // Calculate available currencies from ALL filtered transactions (before currency filtering)
                            val allAvailableCurrencies = CurrencyUtils.sortCurrencies(
                                transactions.map { it.currency }.distinct()
                            )

                            // Auto-select primary currency if current currency doesn't exist in available currencies
                            val currentCurrency = selectedCurrency.value
                            val finalCurrency =
                                if (allAvailableCurrencies.isNotEmpty() && !allAvailableCurrencies.contains(
                                        currentCurrency
                                    )
                                ) {
                                    // Auto-select: prefer baseCurrency from preferences, then first available
                                    val baseCurrency =
                                        userPreferencesRepository.baseCurrency.first()
                                    val newCurrency =
                                        if (allAvailableCurrencies.contains(baseCurrency)) {
                                            baseCurrency
                                        } else {
                                            allAvailableCurrencies.first()
                                        }
                                    _selectedCurrency.value = newCurrency
                                    newCurrency
                                } else {
                                    currentCurrency
                                }

                            // Now filter by the selected currency (which may have just been auto-selected)
                            val currencyFilteredTransactions = transactions.filter {
                                it.currency.equals(finalCurrency, ignoreCase = true)
                            }

                            emit(sortTransactions(currencyFilteredTransactions, sort))
                        }
                    }
            }
            .onEach { transactions ->
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    groupedTransactions = groupTransactionsByDate(transactions),
                    isLoading = false
                )
                // Calculate totals for filtered transactions
                _currencyGroupedTotals.value = calculateCurrencyGroupedTotals(transactions)

                // Auto-select primary currency if not already selected or if current currency no longer exists
                if (!_isUnifiedMode.value) {
                    val currentCurrency = selectedCurrency.value
                    if (!_currencyGroupedTotals.value.availableCurrencies.contains(currentCurrency) && _currencyGroupedTotals.value.hasAnyCurrency()) {
                        val baseCurrency = userPreferencesRepository.baseCurrency.first()
                        _selectedCurrency.value = _currencyGroupedTotals.value.getPrimaryCurrency(baseCurrency)
                    }
                    _convertedAmounts.value = emptyMap()
                } else {
                    // Build converted amounts map for transactions in foreign currencies
                    val displayCurrency = _selectedCurrency.value
                    val converted = mutableMapOf<Long, BigDecimal>()
                    for (tx in transactions) {
                        if (!tx.currency.equals(displayCurrency, ignoreCase = true)) {
                            converted[tx.id] = currencyConversionService.convertAmount(
                                tx.amount, tx.currency, displayCurrency
                            )
                        }
                    }
                    _convertedAmounts.value = converted
                }
            }
            .launchIn(viewModelScope)
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }
    
    fun setCategoryFilter(category: String) {
        println("DEBUG: Setting category filter to: '$category'")
        _categoryFilter.value = category
    }
    
    fun clearCategoryFilter() {
        _categoryFilter.value = null
    }
    
    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        _transactionTypeFilter.value = filter
    }
    
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    /**
     * Sets a custom date range filter and switches the period to CUSTOM.
     * Date range is persisted in SavedStateHandle to survive process death.
     *
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @throws IllegalArgumentException if startDate > endDate
     */
    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        require(startDate <= endDate) {
            "Start date ($startDate) must be before or equal to end date ($endDate)"
        }
        // Store as epoch days for process death survival
        savedStateHandle["customDateRange"] = startDate.toEpochDay() to endDate.toEpochDay()
        _selectedPeriod.value = TimePeriod.CUSTOM
    }

    /**
     * Clears the custom date range and resets to THIS_MONTH period.
     * Always safe to call - ensures we never have CUSTOM period with null dates.
     */
    fun clearCustomDateRange() {
        savedStateHandle["customDateRange"] = null
        // Always reset to a valid period to prevent CUSTOM with null dates
        if (_selectedPeriod.value == TimePeriod.CUSTOM) {
            _selectedPeriod.value = TimePeriod.THIS_MONTH
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            _deletedTransaction.value = transaction
            transactionRepository.deleteTransaction(transaction)
        }
    }
    
    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            viewModelScope.launch {
                transactionRepository.undoDeleteTransaction(transaction)
                _deletedTransaction.value = null
            }
        }
    }
    
    fun undoDeleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.undoDeleteTransaction(transaction)
        }
    }
    
    fun clearDeletedTransaction() {
        _deletedTransaction.value = null
    }

    fun resetFilters() {
        hasAppliedInitialFilters = false
        appliedNavigationParams = null
        appliedBudgetParams = null
        clearCategoryFilter()
        clearCategoriesFilter()
        updateSearchQuery("")
        clearCustomDateRange()
        selectPeriod(TimePeriod.THIS_MONTH)
        setTransactionTypeFilter(TransactionTypeFilter.ALL)
        setSortOption(SortOption.DATE_NEWEST)
        // Don't reset currency as it might be user preference
    }
    
    fun applyInitialFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?
    ) {
        if (!hasAppliedInitialFilters) {
            // Only apply filters once, when first navigating to the screen
            clearCategoryFilter()
            updateSearchQuery("")
            selectPeriod(TimePeriod.THIS_MONTH)
            setTransactionTypeFilter(TransactionTypeFilter.ALL)
            setSortOption(SortOption.DATE_NEWEST)

            category?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } else it
                setCategoryFilter(decoded)
            }

            merchant?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } else it
                updateSearchQuery(decoded)
            }

            period?.let { periodName ->
                val timePeriod = when (periodName) {
                    "THIS_MONTH" -> TimePeriod.THIS_MONTH
                    "LAST_MONTH" -> TimePeriod.LAST_MONTH
                    "CURRENT_FY" -> TimePeriod.CURRENT_FY
                    "ALL" -> TimePeriod.ALL
                    else -> null
                }
                timePeriod?.let { selectPeriod(it) }
            }

            // Only set currency if it's provided (from navigation)
            currency?.let { selectCurrency(it) }

            hasAppliedInitialFilters = true
        }
    }

    fun applyNavigationFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?
    ) {
        // Create current params to compare
        val currentParams = NavigationParams(category, merchant, period, currency)

        // Only apply navigation filters if:
        // 1. This is the first time (appliedNavigationParams is null)
        // 2. OR the navigation params have actually changed (new navigation, not returning from detail)
        if (appliedNavigationParams != null && appliedNavigationParams == currentParams) {
            // Same params, user is returning from detail screen - don't reset their filters
            return
        }

        // Store the current navigation params
        appliedNavigationParams = currentParams

        // Reset filters for new navigation
        clearCategoryFilter()
        updateSearchQuery("")
        selectPeriod(TimePeriod.THIS_MONTH)
        setTransactionTypeFilter(TransactionTypeFilter.ALL)
        setSortOption(SortOption.DATE_NEWEST)

        category?.let {
            val decoded = if (it.contains("+") || it.contains("%")) {
                java.net.URLDecoder.decode(it, "UTF-8")
            } else it
            setCategoryFilter(decoded)
        }

        merchant?.let {
            val decoded = if (it.contains("+") || it.contains("%")) {
                java.net.URLDecoder.decode(it, "UTF-8")
            } else it
            updateSearchQuery(decoded)
        }

        period?.let { periodName ->
            val timePeriod = when (periodName) {
                "THIS_MONTH" -> TimePeriod.THIS_MONTH
                "LAST_MONTH" -> TimePeriod.LAST_MONTH
                "CURRENT_FY" -> TimePeriod.CURRENT_FY
                "ALL" -> TimePeriod.ALL
                else -> null
            }
            timePeriod?.let { selectPeriod(it) }
        }

        // Only set currency if it's provided (from navigation)
        currency?.let { selectCurrency(it) }
    }

    /**
     * Apply filters for budget transactions navigation.
     * This sets a custom date range, categories filter, and transaction type.
     */
    fun applyBudgetFilters(
        startDateEpochDay: Long,
        endDateEpochDay: Long,
        currency: String?,
        categories: String?,  // Comma-separated
        transactionType: String?
    ) {
        // Create current params to compare
        val currentParams = BudgetParams(startDateEpochDay, endDateEpochDay, currency, categories, transactionType)

        // Only apply budget filters if:
        // 1. This is the first time (appliedBudgetParams is null)
        // 2. OR the budget params have actually changed (new navigation, not returning from detail)
        if (appliedBudgetParams != null && appliedBudgetParams == currentParams) {
            // Same params, user is returning from detail screen - don't reset their filters
            return
        }

        // Store the current budget params
        appliedBudgetParams = currentParams

        // Clear existing filters first
        clearCategoryFilter()
        updateSearchQuery("")
        setSortOption(SortOption.DATE_NEWEST)

        // Set custom date range
        setCustomDateRange(
            LocalDate.ofEpochDay(startDateEpochDay),
            LocalDate.ofEpochDay(endDateEpochDay)
        )

        // Set currency
        currency?.let { selectCurrency(it) }

        // Set transaction type filter
        transactionType?.let {
            try {
                val filter = TransactionTypeFilter.valueOf(it)
                setTransactionTypeFilter(filter)
            } catch (e: IllegalArgumentException) {
                // Ignore invalid transaction type
            }
        }

        // Set multiple categories filter
        categories?.let { cats ->
            val categoryList = cats.split(",").map { cat ->
                if (cat.contains("+") || cat.contains("%")) {
                    java.net.URLDecoder.decode(cat, "UTF-8")
                } else {
                    cat
                }
            }.filter { it.isNotBlank() }

            if (categoryList.isNotEmpty()) {
                _categoriesFilter.value = categoryList
            }
        }
    }

    /**
     * Clears the multiple categories filter.
     */
    fun clearCategoriesFilter() {
        _categoriesFilter.value = null
    }

    private fun getFilteredTransactions(
        searchQuery: String,
        period: TimePeriod,
        category: String?,
        categories: List<String>?,
        typeFilter: TransactionTypeFilter
    ): Flow<List<TransactionEntity>> {
        // Start with the base flow based on category filter
        val baseFlow = if (category != null) {
            println("DEBUG: Filtering by category: '$category'")
            transactionRepository.getTransactionsByCategory(category)
        } else {
            transactionRepository.getAllTransactions()
        }

        // Apply multiple categories filter (for budget navigation)
        // This needs to consider split transactions - a transaction should be included
        // if its main category OR any of its split categories match the budget's categories
        val categoriesFilteredFlow = if (categories != null && categories.isNotEmpty()) {
            baseFlow.flatMapLatest { transactions ->
                flow {
                    // Get all transaction IDs
                    val txIds = transactions.map { it.id }

                    // Batch fetch all splits for these transactions (efficient single query)
                    val allSplits = if (txIds.isNotEmpty()) {
                        transactionSplitDao.getSplitsForTransactions(txIds)
                    } else {
                        emptyList()
                    }

                    // Group splits by transaction ID for quick lookup
                    val splitsByTxId = allSplits.groupBy { it.transactionId }

                    // Filter transactions
                    val filtered = transactions.filter { tx ->
                        // Check main category first (fast path)
                        if (tx.category in categories) {
                            true
                        } else {
                            // Check if any split category matches
                            splitsByTxId[tx.id]?.any { it.category in categories } == true
                        }
                    }
                    emit(filtered)
                }
            }
        } else {
            baseFlow
        }
        
        // Apply period filter
        val periodFilteredFlow = when (period) {
            TimePeriod.ALL -> categoriesFilteredFlow
            TimePeriod.CUSTOM -> {
                val customRange = customDateRange.value
                // Guard against invalid state: CUSTOM period must have a date range
                // This should never happen due to clearCustomDateRange() logic, but be defensive
                if (customRange == null) {
                    android.util.Log.e("TransactionsViewModel",
                        "CUSTOM period selected but no date range set - falling back to THIS_MONTH")
                    // Auto-correct the invalid state
                    _selectedPeriod.value = TimePeriod.THIS_MONTH
                    val (startDate, endDate) = getDateRangeForPeriod(TimePeriod.THIS_MONTH)!!
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)
                    categoriesFilteredFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                } else {
                    val (startDate, endDate) = customRange
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)

                    categoriesFilteredFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                }
            }
            else -> {
                val dateRange = getDateRangeForPeriod(period)
                if (dateRange != null) {
                    val (startDate, endDate) = dateRange
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)

                    categoriesFilteredFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                } else {
                    categoriesFilteredFlow
                }
            }
        }
        
        // Apply transaction type filter
        val typeFilteredFlow = periodFilteredFlow.map { transactions ->
            when (typeFilter) {
                TransactionTypeFilter.ALL -> transactions
                TransactionTypeFilter.INCOME -> transactions.filter { it.transactionType == TransactionType.INCOME }
                TransactionTypeFilter.EXPENSE -> transactions.filter { it.transactionType == TransactionType.EXPENSE }
                TransactionTypeFilter.CREDIT -> transactions.filter { it.transactionType == TransactionType.CREDIT }
                TransactionTypeFilter.TRANSFER -> transactions.filter { it.transactionType == TransactionType.TRANSFER }
                TransactionTypeFilter.INVESTMENT -> transactions.filter { it.transactionType == TransactionType.INVESTMENT }
            }
        }
        
        // Apply search filter
        return if (searchQuery.isBlank()) {
            typeFilteredFlow
        } else {
            typeFilteredFlow.map { transactions ->
                transactions.filter { transaction ->
                    // Check merchant name and description
                    val matchesMerchant = transaction.merchantName.contains(searchQuery, ignoreCase = true)
                    val matchesDescription = transaction.description?.contains(searchQuery, ignoreCase = true) == true
                    
                    // Check SMS body (full text search)
                    val matchesSmsBody = transaction.smsBody?.contains(searchQuery, ignoreCase = true) == true
                    
                    // Check if search query matches amount
                    val matchesAmount = try {
                        // Remove commas and spaces from search query for number parsing
                        val cleanedQuery = searchQuery.replace(",", "").replace(" ", "").trim()
                        
                        // Check if it's a valid number and matches the amount
                        if (cleanedQuery.isNotEmpty() && cleanedQuery.all { it.isDigit() || it == '.' }) {
                            val amountString = transaction.amount.toPlainString()
                            // Support both exact and partial matches
                            amountString.contains(cleanedQuery) || 
                            // Also match formatted amount (e.g., "1,000" matches "1000")
                            amountString.replace(",", "").contains(cleanedQuery)
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    
                    matchesMerchant || matchesDescription || matchesSmsBody || matchesAmount
                }
            }
        }
    }
    
    private fun sortTransactions(transactions: List<TransactionEntity>, sortOption: SortOption): List<TransactionEntity> {
        return when (sortOption) {
            SortOption.DATE_NEWEST -> transactions.sortedByDescending { it.dateTime }
            SortOption.DATE_OLDEST -> transactions.sortedBy { it.dateTime }
            SortOption.AMOUNT_HIGHEST -> transactions.sortedByDescending { it.amount }
            SortOption.AMOUNT_LOWEST -> transactions.sortedBy { it.amount }
            SortOption.MERCHANT_AZ -> transactions.sortedBy { it.merchantName.lowercase() }
            SortOption.MERCHANT_ZA -> transactions.sortedByDescending { it.merchantName.lowercase() }
        }
    }
    
    private fun groupTransactionsByDate(
        transactions: List<TransactionEntity>
    ): Map<DateGroup, List<TransactionEntity>> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekStart = today.minusWeeks(1)
        
        return transactions.groupBy { transaction ->
            val transactionDate = transaction.dateTime.toLocalDate()
            when {
                transactionDate == today -> DateGroup.TODAY
                transactionDate == yesterday -> DateGroup.YESTERDAY
                transactionDate > weekStart -> DateGroup.THIS_WEEK
                else -> DateGroup.EARLIER
            }
        }
    }
    
    private fun calculateCurrencyGroupedTotals(transactions: List<TransactionEntity>): CurrencyGroupedTotals {
        // Group transactions by currency
        val transactionsByCurrency = transactions.groupBy { it.currency }

        val totalsByCurrency = transactionsByCurrency.mapValues { (currency, currencyTransactions) ->
            val income = currencyTransactions
                .filter { it.transactionType == TransactionType.INCOME }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            val expenses = currencyTransactions
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            val credit = currencyTransactions
                .filter { it.transactionType == TransactionType.CREDIT }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            val transfer = currencyTransactions
                .filter { it.transactionType == TransactionType.TRANSFER }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            val investment = currencyTransactions
                .filter { it.transactionType == TransactionType.INVESTMENT }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            CurrencyTotals(
                currency = currency,
                income = income,
                expenses = expenses,
                credit = credit,
                transfer = transfer,
                investment = investment,
                transactionCount = currencyTransactions.size
            )
        }

        // Note: availableCurrencies are now provided by the separate availableCurrencies StateFlow
        // We'll keep the old behavior for compatibility but the UI should use availableCurrencies property
        // Use standard currency sorting (INR first, then alphabetical)
        val filteredAvailableCurrencies = CurrencyUtils.sortCurrencies(
            totalsByCurrency.keys.toList()
        )

        return CurrencyGroupedTotals(
            totalsByCurrency = totalsByCurrency,
            availableCurrencies = filteredAvailableCurrencies,
            transactionCount = transactions.size
        )
    }
    
    fun getReportUrl(transaction: TransactionEntity): String {
        // If we have the original SMS body, create report URL
        val smsBody = transaction.smsBody ?: ""
        // Use the original SMS sender if available
        val sender = transaction.smsSender ?: ""
        
        // URL encode the parameters
        val encodedMessage = java.net.URLEncoder.encode(smsBody, "UTF-8")
        val encodedSender = java.net.URLEncoder.encode(sender, "UTF-8")
        
        // Encrypt device data for verification
        val encryptedDeviceData = com.pennywiseai.tracker.utils.DeviceEncryption.encryptDeviceData(context)
        val encodedDeviceData = if (encryptedDeviceData != null) {
            java.net.URLEncoder.encode(encryptedDeviceData, "UTF-8")
        } else {
            ""
        }
        
        // Create the report URL using hash fragment for privacy
        return "${Constants.Links.WEB_PARSER_URL}/#message=$encodedMessage&sender=$encodedSender&device=$encodedDeviceData&autoparse=true"
    }
    
}

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val groupedTransactions: Map<DateGroup, List<TransactionEntity>> = emptyMap(),
    val isLoading: Boolean = true
)

data class FilterParams(
    val query: String,
    val period: TimePeriod,
    val category: String?,
    val typeFilter: TransactionTypeFilter
)

enum class DateGroup(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    EARLIER("Earlier")
}

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest First"),
    DATE_OLDEST("Oldest First"),
    AMOUNT_HIGHEST("Highest Amount"),
    AMOUNT_LOWEST("Lowest Amount"),
    MERCHANT_AZ("Merchant (A-Z)"),
    MERCHANT_ZA("Merchant (Z-A)")
}

data class FilteredTotals(
    val income: BigDecimal = BigDecimal.ZERO,
    val expenses: BigDecimal = BigDecimal.ZERO,
    val credit: BigDecimal = BigDecimal.ZERO,
    val transfer: BigDecimal = BigDecimal.ZERO,
    val investment: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0
)

/**
 * Tracks the navigation parameters that were applied.
 * Used to detect if navigation params have actually changed vs
 * just returning from a detail screen with the same params.
 */
private data class NavigationParams(
    val category: String?,
    val merchant: String?,
    val period: String?,
    val currency: String?
)

/**
 * Tracks the budget navigation parameters that were applied.
 * Used to detect if budget params have actually changed vs
 * just returning from a detail screen with the same params.
 */
private data class BudgetParams(
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val currency: String?,
    val categories: String?,
    val transactionType: String?
)
