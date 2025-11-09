package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val userPreferencesRepository: com.pennywiseai.tracker.data.preferences.UserPreferencesRepository,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()
    
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()
    
    private val _transactionTypeFilter = MutableStateFlow(TransactionTypeFilter.ALL)
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = _transactionTypeFilter.asStateFlow()
    
    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("INR") // Default to INR
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

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
        _selectedCurrency
    ) { groupedTotals, currency ->
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilteredTotals()
    )
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()
    
    // Track if initial filters have been applied to prevent resetting on back navigation
    private var hasAppliedInitialFilters = false
    
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
    
    fun isShowingLimitedData(): Boolean {
        val currentPeriod = _selectedPeriod.value
        val scanMonthsValue = smsScanMonths.value

        return when (currentPeriod) {
            TimePeriod.ALL -> true  // Always show for "All Time"
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
        // Manually combine all flows using transformLatest
        merge(
            searchQuery.debounce(300).map { "search" },
            selectedPeriod.map { "period" },
            categoryFilter.map { "category" },
            transactionTypeFilter.map { "typeFilter" },
            selectedCurrency.map { "currency" },
            sortOption.map { "sort" },
            customDateRange.map { "customDate" }
        )
            .transformLatest { trigger ->
                // Get current values from all StateFlows
                val query = searchQuery.value
                val period = selectedPeriod.value
                val category = categoryFilter.value
                val typeFilter = transactionTypeFilter.value
                val currency = selectedCurrency.value
                val sort = sortOption.value

                // Get filtered transactions
                getFilteredTransactions(query, period, category, typeFilter)
                    .collect { transactions ->
                        // Filter by currency
                        val currencyFilteredTransactions = transactions.filter {
                            it.currency.equals(currency, ignoreCase = true)
                        }
                        emit(sortTransactions(currencyFilteredTransactions, sort))
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
                val currentCurrency = selectedCurrency.value
                if (!_currencyGroupedTotals.value.availableCurrencies.contains(currentCurrency) && _currencyGroupedTotals.value.hasAnyCurrency()) {
                    _selectedCurrency.value = _currencyGroupedTotals.value.getPrimaryCurrency()
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
        clearCategoryFilter()
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
        // This function can be called multiple times for navigation updates
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
    
    private fun getFilteredTransactions(
        searchQuery: String,
        period: TimePeriod,
        category: String?,
        typeFilter: TransactionTypeFilter
    ): Flow<List<TransactionEntity>> {
        // Start with the base flow based on category filter
        val baseFlow = if (category != null) {
            println("DEBUG: Filtering by category: '$category'")
            transactionRepository.getTransactionsByCategory(category)
        } else {
            transactionRepository.getAllTransactions()
        }
        
        // Apply period filter
        val periodFilteredFlow = when (period) {
            TimePeriod.ALL -> baseFlow
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
                    baseFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                } else {
                    val (startDate, endDate) = customRange
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)

                    baseFlow.map { transactions ->
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

                    baseFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                } else {
                    baseFlow
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
