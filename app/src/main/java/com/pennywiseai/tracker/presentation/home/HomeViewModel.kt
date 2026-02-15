package com.pennywiseai.tracker.presentation.home

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.manager.InAppUpdateManager
import com.pennywiseai.tracker.data.manager.InAppReviewManager
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val llmRepository: LlmRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val inAppUpdateManager: InAppUpdateManager,
    private val inAppReviewManager: InAppReviewManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()

    // SMS scanning work progress tracking
    private val _smsScanWorkInfo = MutableStateFlow<WorkInfo?>(null)
    val smsScanWorkInfo: StateFlow<WorkInfo?> = _smsScanWorkInfo.asStateFlow()

    // Store currency breakdown maps for quick access when switching currencies
    private var currentMonthBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> = emptyMap()
    private var lastMonthBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> = emptyMap()
    
    init {
        loadBaseCurrency()
        loadUnifiedModePreferences()
        loadHomeData()
    }

    private fun loadBaseCurrency() {
        // Initialize selectedCurrency from baseCurrency preference
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            _uiState.value = _uiState.value.copy(selectedCurrency = baseCurrency)
        }

        // Listen to baseCurrency changes and update selectedCurrency
        userPreferencesRepository.baseCurrency
            .onEach { baseCurrency ->
                val currentSelected = _uiState.value.selectedCurrency
                // Only update if the baseCurrency changed and is available in current currencies
                // or if current selected currency is not in available currencies
                val availableCurrencies = _uiState.value.availableCurrencies
                if (baseCurrency != currentSelected) {
                    // If baseCurrency is available, use it; otherwise keep current selection
                    if (availableCurrencies.isEmpty() || availableCurrencies.contains(baseCurrency)) {
                        selectCurrency(baseCurrency)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadUnifiedModePreferences() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { unifiedMode, displayCurrency ->
                unifiedMode to displayCurrency
            }.collect { (unifiedMode, displayCurrency) ->
                val previousMode = _uiState.value.isUnifiedMode
                val previousCurrency = _uiState.value.selectedCurrency

                _uiState.value = _uiState.value.copy(isUnifiedMode = unifiedMode)

                if (unifiedMode && (previousMode != unifiedMode || previousCurrency != displayCurrency)) {
                    // Switch to unified mode: aggregate all currencies
                    selectCurrency(displayCurrency)
                }
            }
        }
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            // Load current month breakdown by currency
            transactionRepository.getCurrentMonthBreakdownByCurrency().collect { breakdownByCurrency ->
                updateBreakdownForSelectedCurrency(breakdownByCurrency, isCurrentMonth = true)
            }
        }
        
        viewModelScope.launch {
            // Load account balances
            accountBalanceRepository.getAllLatestBalances().collect { allBalances ->
                // Get hidden accounts from SharedPreferences
                val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
                
                // Filter out hidden accounts
                val balances = allBalances.filter { account ->
                    val key = "${account.bankName}_${account.accountLast4}"
                    !hiddenAccounts.contains(key)
                }
                // Separate credit cards from regular accounts (hide zero balance accounts)
                val regularAccounts = balances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
                val creditCards = balances.filter { it.isCreditCard }
                
                // Account loading completed
                Log.d("HomeViewModel", "Loaded ${balances.size} account(s)")
                
                // Check if we have multiple currencies and refresh exchange rates if needed
                val accountCurrencies = regularAccounts.map { it.currency }.distinct()
                val hasMultipleCurrencies = accountCurrencies.size > 1

                if (hasMultipleCurrencies && accountCurrencies.isNotEmpty()) {
                    currencyConversionService.refreshExchangeRatesForAccount(accountCurrencies)
                }

                // Convert all account balances to selected currency for total
                val selectedCurrency = _uiState.value.selectedCurrency
                val totalBalanceInSelectedCurrency = regularAccounts.sumOf { account ->
                    if (account.currency == selectedCurrency) {
                        account.balance
                    } else {
                        // Convert to selected currency
                        currencyConversionService.convertAmount(
                            amount = account.balance,
                            fromCurrency = account.currency,
                            toCurrency = selectedCurrency
                        ) ?: account.balance
                    }
                }

                val totalAvailableCreditInSelectedCurrency = creditCards.sumOf { card ->
                    // Available = Credit Limit - Outstanding Balance, converted to selected currency
                    val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                    if (card.currency == selectedCurrency) {
                        availableInCardCurrency
                    } else {
                        currencyConversionService.convertAmount(
                            amount = availableInCardCurrency,
                            fromCurrency = card.currency,
                            toCurrency = selectedCurrency
                        ) ?: availableInCardCurrency
                    }
                }

                _uiState.value = _uiState.value.copy(
                    accountBalances = regularAccounts,  // Only regular bank accounts
                    creditCards = creditCards,           // Only credit cards
                    totalBalance = totalBalanceInSelectedCurrency,
                    totalAvailableCredit = totalAvailableCreditInSelectedCurrency
                )
            }
        }
        
        viewModelScope.launch {
            // Load current month transactions by type (currency-filtered)
            val now = java.time.LocalDate.now()
            val startOfMonth = now.withDayOfMonth(1)
            val endOfMonth = now.withDayOfMonth(now.lengthOfMonth())

            transactionRepository.getTransactionsBetweenDates(
                startDate = startOfMonth,
                endDate = endOfMonth
            ).collect { transactions ->
                updateTransactionTypeTotals(transactions)
            }
        }
        
        viewModelScope.launch {
            // Load last month breakdown by currency
            transactionRepository.getLastMonthBreakdownByCurrency().collect { breakdownByCurrency ->
                updateBreakdownForSelectedCurrency(breakdownByCurrency, isCurrentMonth = false)
            }
        }
        
        viewModelScope.launch {
            // Load recent transactions (last 3) with conversion for unified mode
            combine(
                transactionRepository.getRecentTransactions(limit = 3),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { transactions, isUnified, displayCurrency ->
                Triple(transactions, isUnified, displayCurrency)
            }.collect { (transactions, isUnified, displayCurrency) ->
                val convertedAmounts = if (isUnified) {
                    val map = mutableMapOf<Long, java.math.BigDecimal>()
                    for (tx in transactions) {
                        if (!tx.currency.equals(displayCurrency, ignoreCase = true)) {
                            map[tx.id] = currencyConversionService.convertAmount(
                                tx.amount, tx.currency, displayCurrency
                            )
                        }
                    }
                    map
                } else {
                    emptyMap()
                }
                _uiState.value = _uiState.value.copy(
                    recentTransactions = transactions,
                    recentTransactionConvertedAmounts = convertedAmounts,
                    isLoading = false
                )
            }
        }
        
        viewModelScope.launch {
            // Load all active subscriptions with conversion for unified mode
            combine(
                subscriptionRepository.getActiveSubscriptions(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { subscriptions, isUnified, displayCurrency ->
                Triple(subscriptions, isUnified, displayCurrency)
            }.collect { (subscriptions, isUnified, displayCurrency) ->
                val totalAmount = if (isUnified) {
                    var total = java.math.BigDecimal.ZERO
                    for (sub in subscriptions) {
                        total += currencyConversionService.convertAmount(
                            sub.amount, sub.currency, displayCurrency
                        )
                    }
                    total
                } else {
                    subscriptions.sumOf { it.amount }
                }
                _uiState.value = _uiState.value.copy(
                    upcomingSubscriptions = subscriptions,
                    upcomingSubscriptionsTotal = totalAmount
                )
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                userPreferencesRepository.baseCurrency
            ) { unifiedMode, displayCurrency, baseCurrency ->
                Triple(unifiedMode, displayCurrency, baseCurrency)
            }.flatMapLatest { (unifiedMode, displayCurrency, baseCurrency) ->
                val today = LocalDate.now()
                if (unifiedMode) {
                    budgetGroupRepository.getGroupSpendingAllCurrencies(today.year, today.monthValue)
                        .map { raw ->
                            mapRawToConvertedSummary(raw, displayCurrency, baseCurrency)
                        }
                } else {
                    budgetGroupRepository.getGroupSpending(today.year, today.monthValue, baseCurrency)
                }
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(
                    budgetSummary = if (summary.groups.isNotEmpty()) summary else null
                )
            }
        }
    }
    
    private fun calculateMonthlyChange() {
        val currentExpenses = _uiState.value.currentMonthExpenses
        val lastExpenses = _uiState.value.lastMonthExpenses
        val currentTotal = _uiState.value.currentMonthTotal
        val lastTotal = _uiState.value.lastMonthTotal
        
        // Calculate expense change for simple comparison
        val expenseChange = currentExpenses - lastExpenses
        val totalChange = currentTotal - lastTotal
        
        _uiState.value = _uiState.value.copy(
            monthlyChange = totalChange,
            monthlyChangePercent = 0 // We're not using percentage anymore
        )
    }
    
    fun refreshHiddenAccounts() {
        viewModelScope.launch {
            // Force re-read of hidden accounts from SharedPreferences
            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
            
            // Re-fetch all accounts and filter
            accountBalanceRepository.getAllLatestBalances().first().let { allBalances ->
                val visibleBalances = allBalances.filter { account ->
                    val key = "${account.bankName}_${account.accountLast4}"
                    !hiddenAccounts.contains(key)
                }
                
                // Separate credit cards from regular accounts (hide zero balance accounts)
                val regularAccounts = visibleBalances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
                val creditCards = visibleBalances.filter { it.isCreditCard }
                
                // Update UI state
                _uiState.value = _uiState.value.copy(
                    accountBalances = regularAccounts,
                    creditCards = creditCards,
                    totalBalance = regularAccounts.sumOf { it.balance },
                    totalAvailableCredit = creditCards.sumOf { 
                        // Available = Credit Limit - Outstanding Balance
                        (it.creditLimit ?: BigDecimal.ZERO) - it.balance
                    }
                )
            }
        }
    }
    
    /**
     * Scans SMS messages for transactions.
     * @param forceResync If true, performs a full resync from scratch, reprocessing all SMS messages.
     *                    This is useful when bank parsers have been updated and old transactions need to be re-parsed.
     *                    If false (default), performs an incremental scan for new messages only.
     */
    fun scanSmsMessages(forceResync: Boolean = false) {
        val inputData = workDataOf(
            OptimizedSmsReaderWorker.INPUT_FORCE_RESYNC to forceResync
        )

        val workRequest = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .setInputData(inputData)
            .addTag(OptimizedSmsReaderWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            OptimizedSmsReaderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Update UI to show scanning
        _uiState.value = _uiState.value.copy(isScanning = true)

        // Track work progress
        observeWorkProgress()
    }

    private fun observeWorkProgress() {
        val workManager = WorkManager.getInstance(context)

        // Use getWorkInfosById for more direct observation
        workManager.getWorkInfosByTagLiveData(OptimizedSmsReaderWorker.WORK_NAME).observeForever { workInfos ->
            val currentWork = workInfos.firstOrNull { it.tags.contains(OptimizedSmsReaderWorker.WORK_NAME) }
            if (currentWork != null) {
                _smsScanWorkInfo.value = currentWork

                // Update scanning state based on work state
                when (currentWork.state) {
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED,
                    WorkInfo.State.BLOCKED -> {
                        _uiState.value = _uiState.value.copy(isScanning = false)
                    }
                    else -> {
                        // Still running or enqueued
                        _uiState.value = _uiState.value.copy(isScanning = true)
                    }
                }
            }
        }
    }

    fun cancelSmsScan() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(OptimizedSmsReaderWorker.WORK_NAME)
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    fun refreshAccountBalances() {
        viewModelScope.launch {
            // Force refresh the account balances by retriggering the calculation
            accountBalanceRepository.getAllLatestBalances().collect { allBalances ->
                // Get hidden accounts from SharedPreferences
                val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

                // Filter out hidden accounts
                val balances = allBalances.filter { account ->
                    val key = "${account.bankName}_${account.accountLast4}"
                    !hiddenAccounts.contains(key)
                }
                // Separate credit cards from regular accounts (hide zero balance accounts)
                val regularAccounts = balances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
                val creditCards = balances.filter { it.isCreditCard }

                // Account loading completed
                Log.d("HomeViewModel", "Refreshed ${balances.size} account(s)")

                // Check if we have multiple currencies and refresh exchange rates if needed
                val accountCurrencies = regularAccounts.map { it.currency }.distinct()
                val creditCardCurrencies = creditCards.map { it.currency }.distinct()
                val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()
                val hasMultipleCurrencies = allAccountCurrencies.size > 1

                if (hasMultipleCurrencies && allAccountCurrencies.isNotEmpty()) {
                    currencyConversionService.refreshExchangeRatesForAccount(allAccountCurrencies)
                }

                // Update available currencies to include account currencies
                val currentAvailableCurrencies = _uiState.value.availableCurrencies.toSet()
                val updatedAvailableCurrencies = (currentAvailableCurrencies + allAccountCurrencies)
                    .sortedWith { a, b ->
                        when {
                            a == "INR" -> -1 // INR first
                            b == "INR" -> 1
                            else -> a.compareTo(b) // Alphabetical for others
                        }
                    }

                // Convert all account balances to selected currency for total
                val selectedCurrency = _uiState.value.selectedCurrency
                val totalBalanceInSelectedCurrency = regularAccounts.sumOf { account ->
                    if (account.currency == selectedCurrency) {
                        account.balance
                    } else {
                        // Convert to selected currency
                        currencyConversionService.convertAmount(
                            amount = account.balance,
                            fromCurrency = account.currency,
                            toCurrency = selectedCurrency
                        ) ?: account.balance
                    }
                }

                val totalAvailableCreditInSelectedCurrency = creditCards.sumOf { card ->
                    // Available = Credit Limit - Outstanding Balance, converted to selected currency
                    val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                    if (card.currency == selectedCurrency) {
                        availableInCardCurrency
                    } else {
                        currencyConversionService.convertAmount(
                            amount = availableInCardCurrency,
                            fromCurrency = card.currency,
                            toCurrency = selectedCurrency
                        ) ?: availableInCardCurrency
                    }
                }

                _uiState.value = _uiState.value.copy(
                    accountBalances = regularAccounts,  // Only regular bank accounts
                    creditCards = creditCards,           // Only credit cards
                    totalBalance = totalBalanceInSelectedCurrency,
                    totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                    availableCurrencies = updatedAvailableCurrencies
                )
            }
        }
    }
    
    fun updateSystemPrompt() {
        viewModelScope.launch {
            try {
                llmRepository.updateSystemPrompt()
            } catch (e: Exception) {
                // Handle error silently or add error state if needed
            }
        }
    }
    
    fun showBreakdownDialog() {
        _uiState.value = _uiState.value.copy(showBreakdownDialog = true)
    }
    
    fun hideBreakdownDialog() {
        _uiState.value = _uiState.value.copy(showBreakdownDialog = false)
    }
    
    /**
     * Checks for app updates using Google Play In-App Updates.
     * Should be called with the current activity context.
     * @param activity The activity context
     * @param snackbarHostState Optional SnackbarHostState for showing restart prompt
     * @param scope Optional CoroutineScope for launching the snackbar
     */
    fun checkForAppUpdate(
        activity: ComponentActivity,
        snackbarHostState: androidx.compose.material3.SnackbarHostState? = null,
        scope: kotlinx.coroutines.CoroutineScope? = null
    ) {
        inAppUpdateManager.checkForUpdate(activity, snackbarHostState, scope)
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
    
    /**
     * Checks if eligible for in-app review and shows if appropriate.
     * Should be called with the current activity context.
     */
    fun checkForInAppReview(activity: ComponentActivity) {
        viewModelScope.launch {
            // Get current transaction count as additional eligibility factor
            val transactionCount = transactionRepository.getAllTransactions().first().size
            inAppReviewManager.checkAndShowReviewIfEligible(activity, transactionCount)
        }
    }
    
    fun selectCurrency(currency: String) {
        // Update monthly breakdown values from stored maps
        val availableCurrencies = _uiState.value.availableCurrencies
        updateUIStateForCurrency(currency, availableCurrencies)

        // Refresh account balances to convert them to the new selected currency
        refreshAccountBalances()

        // Also refresh transaction type totals for new currency
        viewModelScope.launch {
            val now = java.time.LocalDate.now()
            val startOfMonth = now.withDayOfMonth(1)
            val endOfMonth = now.withDayOfMonth(now.lengthOfMonth())

            val transactions = transactionRepository.getTransactionsBetweenDates(
                startDate = startOfMonth,
                endDate = endOfMonth
            ).first()
            updateTransactionTypeTotals(transactions)
        }
    }

    private fun updateTransactionTypeTotals(transactions: List<TransactionEntity>) {
        val selectedCurrency = _uiState.value.selectedCurrency
        val isUnified = _uiState.value.isUnifiedMode

        if (isUnified) {
            // Convert all transactions to display currency
            viewModelScope.launch {
                var creditCardTotal = BigDecimal.ZERO
                var transferTotal = BigDecimal.ZERO
                var investmentTotal = BigDecimal.ZERO

                for (tx in transactions) {
                    val converted = currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
                    when (tx.transactionType) {
                        com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT -> creditCardTotal += converted
                        com.pennywiseai.tracker.data.database.entity.TransactionType.TRANSFER -> transferTotal += converted
                        com.pennywiseai.tracker.data.database.entity.TransactionType.INVESTMENT -> investmentTotal += converted
                        else -> { /* skip */ }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    currentMonthCreditCard = creditCardTotal,
                    currentMonthTransfer = transferTotal,
                    currentMonthInvestment = investmentTotal
                )
            }
        } else {
            // Filter transactions by selected currency
            val currencyTransactions = transactions.filter { it.currency == selectedCurrency }

            val creditCardTotal = currencyTransactions
                .filter { it.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT }
                .sumOf { it.amount }
            val transferTotal = currencyTransactions
                .filter { it.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.TRANSFER }
                .sumOf { it.amount }
            val investmentTotal = currencyTransactions
                .filter { it.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.INVESTMENT }
                .sumOf { it.amount }

            _uiState.value = _uiState.value.copy(
                currentMonthCreditCard = creditCardTotal,
                currentMonthTransfer = transferTotal,
                currentMonthInvestment = investmentTotal
            )
        }
    }

    private fun updateBreakdownForSelectedCurrency(
        breakdownByCurrency: Map<String, TransactionRepository.MonthlyBreakdown>,
        isCurrentMonth: Boolean
    ) {
        // Store the breakdown map for later use when switching currencies
        if (isCurrentMonth) {
            currentMonthBreakdownMap = breakdownByCurrency
        } else {
            lastMonthBreakdownMap = breakdownByCurrency
        }

        // Update available currencies from all stored data
        val allCurrencies = (currentMonthBreakdownMap.keys + lastMonthBreakdownMap.keys).distinct()
        val availableCurrencies = allCurrencies.sortedWith { a, b ->
            when {
                a == "INR" -> -1 // INR first
                b == "INR" -> 1
                else -> a.compareTo(b) // Alphabetical for others
            }
        }

        // Auto-select currency: prefer baseCurrency from preferences, then INR, then first available
        val currentSelectedCurrency = _uiState.value.selectedCurrency
        if (!availableCurrencies.contains(currentSelectedCurrency) && availableCurrencies.isNotEmpty()) {
            // Need to get baseCurrency asynchronously
            viewModelScope.launch {
                val baseCurrency = userPreferencesRepository.baseCurrency.first()
                val selectedCurrency = if (availableCurrencies.contains(baseCurrency)) {
                    baseCurrency
                } else if (availableCurrencies.contains("INR")) {
                    "INR"
                } else {
                    availableCurrencies.first()
                }
                // Update UI state with values for selected currency
                updateUIStateForCurrency(selectedCurrency, availableCurrencies)
            }
        } else {
            // Update UI state with values for selected currency
            updateUIStateForCurrency(currentSelectedCurrency, availableCurrencies)
        }
    }

    private fun updateUIStateForCurrency(selectedCurrency: String, availableCurrencies: List<String>) {
        if (_uiState.value.isUnifiedMode) {
            // Aggregate all currencies, converting to selectedCurrency (displayCurrency)
            viewModelScope.launch {
                val currentBreakdown = aggregateBreakdowns(currentMonthBreakdownMap, selectedCurrency)
                val lastBreakdown = aggregateBreakdowns(lastMonthBreakdownMap, selectedCurrency)

                _uiState.value = _uiState.value.copy(
                    currentMonthTotal = currentBreakdown.total,
                    currentMonthIncome = currentBreakdown.income,
                    currentMonthExpenses = currentBreakdown.expenses,
                    lastMonthTotal = lastBreakdown.total,
                    lastMonthIncome = lastBreakdown.income,
                    lastMonthExpenses = lastBreakdown.expenses,
                    selectedCurrency = selectedCurrency,
                    availableCurrencies = availableCurrencies
                )
                calculateMonthlyChange()
            }
        } else {
            // Get breakdown for selected currency from stored maps
            val currentBreakdown = currentMonthBreakdownMap[selectedCurrency] ?: TransactionRepository.MonthlyBreakdown(
                total = BigDecimal.ZERO,
                income = BigDecimal.ZERO,
                expenses = BigDecimal.ZERO
            )

            val lastBreakdown = lastMonthBreakdownMap[selectedCurrency] ?: TransactionRepository.MonthlyBreakdown(
                total = BigDecimal.ZERO,
                income = BigDecimal.ZERO,
                expenses = BigDecimal.ZERO
            )

            _uiState.value = _uiState.value.copy(
                currentMonthTotal = currentBreakdown.total,
                currentMonthIncome = currentBreakdown.income,
                currentMonthExpenses = currentBreakdown.expenses,
                lastMonthTotal = lastBreakdown.total,
                lastMonthIncome = lastBreakdown.income,
                lastMonthExpenses = lastBreakdown.expenses,
                selectedCurrency = selectedCurrency,
                availableCurrencies = availableCurrencies
            )
            calculateMonthlyChange()
        }
    }

    private suspend fun aggregateBreakdowns(
        breakdownMap: Map<String, TransactionRepository.MonthlyBreakdown>,
        targetCurrency: String
    ): TransactionRepository.MonthlyBreakdown {
        var totalTotal = BigDecimal.ZERO
        var totalIncome = BigDecimal.ZERO
        var totalExpenses = BigDecimal.ZERO

        for ((currency, breakdown) in breakdownMap) {
            if (currency == targetCurrency) {
                totalTotal += breakdown.total
                totalIncome += breakdown.income
                totalExpenses += breakdown.expenses
            } else {
                totalTotal += currencyConversionService.convertAmount(breakdown.total, currency, targetCurrency)
                totalIncome += currencyConversionService.convertAmount(breakdown.income, currency, targetCurrency)
                totalExpenses += currencyConversionService.convertAmount(breakdown.expenses, currency, targetCurrency)
            }
        }

        return TransactionRepository.MonthlyBreakdown(
            total = totalTotal,
            income = totalIncome,
            expenses = totalExpenses
        )
    }

    override fun onCleared() {
        super.onCleared()
        inAppUpdateManager.cleanup()
    }

    private suspend fun mapRawToConvertedSummary(
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
            totalTargetGoal = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget },
            totalTargetActual = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual },
            totalExpectedBudget = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget },
            totalExpectedActual = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual },
            netSavings = netSavings,
            savingsRate = savingsRate,
            dailyAllowance = dailyAllowance,
            daysRemaining = raw.daysRemaining,
            currency = displayCurrency
        )
    }
}

data class HomeUiState(
    val currentMonthTotal: BigDecimal = BigDecimal.ZERO,
    val currentMonthIncome: BigDecimal = BigDecimal.ZERO,
    val currentMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val currentMonthCreditCard: BigDecimal = BigDecimal.ZERO,
    val currentMonthTransfer: BigDecimal = BigDecimal.ZERO,
    val currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    val lastMonthTotal: BigDecimal = BigDecimal.ZERO,
    val lastMonthIncome: BigDecimal = BigDecimal.ZERO,
    val lastMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthlyChange: BigDecimal = BigDecimal.ZERO,
    val monthlyChangePercent: Int = 0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val upcomingSubscriptions: List<SubscriptionEntity> = emptyList(),
    val upcomingSubscriptionsTotal: BigDecimal = BigDecimal.ZERO,
    val budgetSummary: BudgetOverallSummary? = null,
    val accountBalances: List<AccountBalanceEntity> = emptyList(),
    val creditCards: List<AccountBalanceEntity> = emptyList(),
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val totalAvailableCredit: BigDecimal = BigDecimal.ZERO,
    val selectedCurrency: String = "INR",
    val availableCurrencies: List<String> = emptyList(),
    val recentTransactionConvertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val showBreakdownDialog: Boolean = false,
    val isUnifiedMode: Boolean = false
)