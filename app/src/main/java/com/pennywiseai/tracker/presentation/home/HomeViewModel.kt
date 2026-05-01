package com.pennywiseai.tracker.presentation.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.manager.InAppUpdateManager
import com.pennywiseai.tracker.data.manager.InAppReviewManager
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterAccountsByProfile
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val transactionGroupRepository: TransactionGroupRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val loanRepository: LoanRepository,
    private val llmRepository: LlmRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val profileRepository: ProfileRepository,
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

    // Per-currency total loss from LENT loans settled this month. Folded into the displayed
    // currentMonthExpenses so a settlement loss shows up as money the user actually lost.
    private var currentMonthLoanLossByCurrency: Map<String, BigDecimal> = emptyMap()

    // Per-currency principal lent during this month for currently-active LENT loans.
    // Resolved against the selected/display currency in updateUIStateForCurrency so the
    // Lent pill stays in sync when the user switches currency tabs.
    private var currentMonthLentByCurrency: Map<String, BigDecimal> = emptyMap()

    // Track if user has manually selected a currency to prevent auto-reset
    private var hasUserSelectedCurrency = false

    // Cached base currency for use in sort comparators (updated from preferences)
    private var baseCurrency = ""

    // Cache the latest account balances as a StateFlow so that combine blocks
    // re-emit when account profiles change (e.g. via Manage Accounts).
    // null = not loaded yet (avoids emitting stale empty data on cold launch)
    private val _cachedAccountBalances = MutableStateFlow<List<AccountBalanceEntity>?>(null)
    private val cachedAccountBalances: List<AccountBalanceEntity> get() = _cachedAccountBalances.value ?: emptyList()

    init {
        loadUnifiedModePreferences()
        loadUserName()
        // Load base currency FIRST so selectedCurrency is set before data loads
        viewModelScope.launch {
            val base = userPreferencesRepository.baseCurrency.first()
            baseCurrency = base
            _uiState.value = _uiState.value.copy(
                selectedCurrency = base,
                availableCurrencies = listOf(base)
            )
            loadHomeData()
        }
        // Keep listening for base currency changes
        loadBaseCurrency()
        observeSelectedProfile()
        observeProfiles()
    }

    fun toggleBalanceVisibility() {
        _uiState.value = _uiState.value.copy(
            isBalanceHidden = !_uiState.value.isBalanceHidden
        )
    }

    private fun loadUserName() {
        userPreferencesRepository.userPreferences
            .onEach { prefs ->
                _uiState.value = _uiState.value.copy(
                    userName = prefs.userName,
                    profileImageUri = prefs.profileImageUri,
                    profileBackgroundColor = prefs.profileBackgroundColor
                )
            }
            .launchIn(viewModelScope)
    }

    private fun loadBaseCurrency() {
        var previousBaseCurrency: String? = null
        userPreferencesRepository.baseCurrency
            .onEach { newBaseCurrency ->
                // Only update if the baseCurrency ACTUALLY CHANGED (not just re-emitted)
                if (newBaseCurrency == previousBaseCurrency) return@onEach
                previousBaseCurrency = newBaseCurrency
                this@HomeViewModel.baseCurrency = newBaseCurrency

                val currentSelected = _uiState.value.selectedCurrency
                val availableCurrencies = _uiState.value.availableCurrencies
                if (baseCurrency != currentSelected && !hasUserSelectedCurrency) {
                    if (availableCurrencies.isEmpty() || availableCurrencies.contains(baseCurrency)) {
                        selectCurrency(baseCurrency)
                        hasUserSelectedCurrency = false  // Reset since this was auto-selection
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSelectedProfile() {
        userPreferencesRepository.selectedProfileId
            .onEach { profileId ->
                _uiState.value = _uiState.value.copy(selectedProfileId = profileId)
                // Guard against cold-launch race: if balances aren't cached yet, the combine
                // blocks that include _cachedAccountBalances will apply the filter automatically
                // once balances load. Only call refreshAccountBalances() eagerly when the cache
                // is already populated (i.e. on user-driven profile switches after launch).
                if (_cachedAccountBalances.value != null) {
                    refreshAccountBalances()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeProfiles() {
        profileRepository.observeAllProfiles()
            .onEach { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
            .launchIn(viewModelScope)
    }

    fun updateSelectedProfile(profileId: Long?) {
        viewModelScope.launch {
            userPreferencesRepository.updateSelectedProfileId(profileId)
        }
    }

    private fun filterTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return filterTransactionsByProfile(
            transactions,
            _uiState.value.selectedProfileId,
            buildProfileAccountKeys(cachedAccountBalances)
        )
    }

    private fun filterVisibleBalances(
        allBalances: List<AccountBalanceEntity>,
        hiddenAccounts: Set<String>
    ): List<AccountBalanceEntity> {
        return filterAccountsByProfile(allBalances, hiddenAccounts, _uiState.value.selectedProfileId)
    }

    private fun computeBreakdownByCurrency(
        transactions: List<TransactionEntity>
    ): Map<String, TransactionRepository.MonthlyBreakdown> {
        return transactions.groupBy { it.currency }.mapValues { (_, txs) ->
            val income = txs.filter { it.transactionType == TransactionType.INCOME }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
            val expenses = txs.filter { it.transactionType == TransactionType.EXPENSE }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
            TransactionRepository.MonthlyBreakdown(
                total = income - expenses,
                income = income,
                expenses = expenses
            )
        }
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
            // Load current month breakdown by currency (filtered by business/personal).
            // Loan-linked transactions are excluded — loan principal is shown separately
            // as "Lent this month", and settlement losses are folded into expenses below.
            val now = LocalDate.now()
            val startOfMonth = now.withDayOfMonth(1)
            combine(
                transactionRepository.getTransactionsBetweenDates(startOfMonth, now),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                val nonLoan = filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
                    .filter { it.loanId == null }
                computeBreakdownByCurrency(nonLoan)
            }.collect { breakdownByCurrency ->
                updateBreakdownForSelectedCurrency(breakdownByCurrency, isCurrentMonth = true)
            }
        }
        
        viewModelScope.launch {
            // Load account balances — combined with unified mode preferences so that
            // individual account entities are pre-converted when unified mode is on.
            combine(
                accountBalanceRepository.getAllLatestBalances(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { allBalances, isUnified, displayCurrency ->
                Triple(allBalances, isUnified, displayCurrency)
            }.collect { (allBalances, isUnified, displayCurrency) ->
                // Cache the raw (unfiltered) balances for refreshAccountBalances/refreshHiddenAccounts
                _cachedAccountBalances.value = allBalances

                // Get hidden accounts from SharedPreferences
                val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

                // Filter out hidden accounts and apply business filter
                val balances = filterVisibleBalances(allBalances, hiddenAccounts)
                // Separate credit cards from regular accounts (hide zero balance accounts)
                val rawRegularAccounts = balances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
                val rawCreditCards = balances.filter { it.isCreditCard }

                // Check if we have multiple currencies and refresh exchange rates if needed
                val accountCurrencies = rawRegularAccounts.map { it.currency }.distinct()
                val creditCardCurrencies = rawCreditCards.map { it.currency }.distinct()
                val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()

                if (allAccountCurrencies.size > 1 && allAccountCurrencies.isNotEmpty()) {
                    currencyConversionService.refreshExchangeRatesForAccount(allAccountCurrencies)
                }

                val selectedCurrency = if (isUnified) displayCurrency else _uiState.value.selectedCurrency

                // Pre-convert individual account entities when unified mode is on
                val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
                val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

                // Calculate totals from (possibly converted) entities
                var totalBalanceInSelectedCurrency = BigDecimal.ZERO
                for (account in regularAccounts) {
                    if (account.currency == selectedCurrency) {
                        totalBalanceInSelectedCurrency += account.balance
                    } else if (currencyConversionService.hasValidRate(account.currency, selectedCurrency)) {
                        totalBalanceInSelectedCurrency += currencyConversionService.convertAmount(
                            amount = account.balance,
                            fromCurrency = account.currency,
                            toCurrency = selectedCurrency
                        )
                    }
                }

                var totalAvailableCreditInSelectedCurrency = BigDecimal.ZERO
                for (card in creditCards) {
                    val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                    if (card.currency == selectedCurrency) {
                        totalAvailableCreditInSelectedCurrency += availableInCardCurrency
                    } else if (currencyConversionService.hasValidRate(card.currency, selectedCurrency)) {
                        totalAvailableCreditInSelectedCurrency += currencyConversionService.convertAmount(
                            amount = availableInCardCurrency,
                            fromCurrency = card.currency,
                            toCurrency = selectedCurrency
                        )
                    }
                }

                // Update available currencies to include account currencies
                val currentAvailableCurrencies = _uiState.value.availableCurrencies.toSet()
                val updatedAvailableCurrencies = (currentAvailableCurrencies + allAccountCurrencies)
                    .sortedWith { a, b ->
                        when {
                            a == baseCurrency -> -1
                            b == baseCurrency -> 1
                            else -> a.compareTo(b)
                        }
                    }

                // Determine if balance is ready (all conversions successful)
                val needsConversion = allAccountCurrencies.size > 1 &&
                    allAccountCurrencies.any { it != selectedCurrency }
                val balanceReady = if (needsConversion) {
                    allAccountCurrencies
                        .filter { it != selectedCurrency }
                        .all { currency ->
                            currencyConversionService.hasValidRate(currency, selectedCurrency)
                        }
                } else {
                    true
                }

                _uiState.value = _uiState.value.copy(
                    accountBalances = regularAccounts,  // Pre-converted in unified mode
                    creditCards = creditCards,           // Pre-converted in unified mode
                    totalBalance = totalBalanceInSelectedCurrency,
                    totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                    availableCurrencies = updatedAvailableCurrencies,
                    isBalanceReady = balanceReady
                )
            }
        }

        viewModelScope.launch {
            // Load current month transactions by type (currency-filtered, business-filtered)
            val now = java.time.LocalDate.now()
            val startOfMonth = now.withDayOfMonth(1)
            val endOfMonth = now.withDayOfMonth(now.lengthOfMonth())

            combine(
                transactionRepository.getTransactionsBetweenDates(
                    startDate = startOfMonth,
                    endDate = endOfMonth
                ),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
            }.collect { transactions ->
                updateTransactionTypeTotals(transactions)
            }
        }

        viewModelScope.launch {
            // Track principal lent during the current month — surfaced separately from
            // "Spent this month" so loan outflows don't masquerade as everyday spending.
            // Stored per-currency so updateUIStateForCurrency can resolve the right value
            // whenever the user switches currency tabs or unified mode toggles.
            val now = LocalDate.now()
            val startOfMonth = now.withDayOfMonth(1).atStartOfDay()
            val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(java.time.LocalTime.MAX)

            combine(
                loanRepository.getActiveLentTransactionsInPeriod(startOfMonth, endOfMonth),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { lentTxns, profileId, balances ->
                val keys = buildProfileAccountKeys(balances)
                filterTransactionsByProfile(lentTxns, profileId, keys)
            }.collect { filtered ->
                currentMonthLentByCurrency = filtered.groupBy { it.currency }.mapValues { (_, txs) ->
                    txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
                }
                updateUIStateForCurrency(_uiState.value.selectedCurrency, _uiState.value.availableCurrencies)
            }
        }

        viewModelScope.launch {
            // Track losses on LENT loans settled this month. Each loss feeds back into the
            // displayed "Spent this month" via updateUIStateForCurrency.
            //
            // LoanEntity has no profile column, so the profile filter is applied via the
            // source EXPENSE transaction (same bank+account → same profile).
            val now = LocalDate.now()
            val startOfMonth = now.withDayOfMonth(1).atStartOfDay()
            val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(java.time.LocalTime.MAX)

            combine(
                loanRepository.getLentLoansSettledInPeriod(startOfMonth, endOfMonth),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { settledLoans, profileId, balances ->
                Triple(settledLoans, profileId, balances)
            }.collect { (settledLoans, profileId, balances) ->
                val keys = buildProfileAccountKeys(balances)
                val byCurrency = mutableMapOf<String, BigDecimal>()
                for (loan in settledLoans) {
                    val sourceTx = loanRepository.getOriginalTransactionForLoan(loan.id)
                    val belongsToProfile = sourceTx == null ||
                        filterTransactionsByProfile(listOf(sourceTx), profileId, keys).isNotEmpty()
                    if (!belongsToProfile) continue

                    val loss = loanRepository.getSettlementLoss(loan)
                    if (loss > BigDecimal.ZERO) {
                        byCurrency.merge(loan.currency, loss) { a, b -> a + b }
                    }
                }
                currentMonthLoanLossByCurrency = byCurrency
                updateUIStateForCurrency(_uiState.value.selectedCurrency, _uiState.value.availableCurrencies)
            }
        }

        viewModelScope.launch {
            // Load last month breakdown by currency (filtered by business/personal)
            val now = LocalDate.now()
            val dayOfMonth = now.dayOfMonth
            val lastMonth = now.minusMonths(1)
            val lastMonthStart = lastMonth.withDayOfMonth(1)
            val lastMonthMaxDay = minOf(dayOfMonth, lastMonth.lengthOfMonth())
            val lastMonthEnd = lastMonth.withDayOfMonth(lastMonthMaxDay)
            combine(
                transactionRepository.getTransactionsBetweenDates(lastMonthStart, lastMonthEnd),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                val nonLoan = filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
                    .filter { it.loanId == null }
                computeBreakdownByCurrency(nonLoan)
            }.collect { breakdownByCurrency ->
                updateBreakdownForSelectedCurrency(breakdownByCurrency, isCurrentMonth = false)
            }
        }

        viewModelScope.launch {
            // Load cumulative spending sparkline for current month + last month comparison
            val now = LocalDate.now()
            val firstOfMonth = now.withDayOfMonth(1)
            val lastMonthStart = firstOfMonth.minusMonths(1)

            combine(
                transactionRepository.getTransactionsBetweenDates(
                    startDate = lastMonthStart,
                    endDate = now
                ),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { allTransactions, profileId, balances ->
                filterTransactionsByProfile(allTransactions, profileId, buildProfileAccountKeys(balances))
            }.collect { allTransactions ->
                val selectedCurrency = _uiState.value.selectedCurrency
                val isUnified = _uiState.value.isUnifiedMode

                // Split into current month and last month
                val currentMonthTxs = allTransactions.filter { it.dateTime.toLocalDate() >= firstOfMonth }
                val lastMonthTxs = allTransactions.filter {
                    val d = it.dateTime.toLocalDate()
                    d >= lastMonthStart && d < firstOfMonth
                }

                // Filter to EXPENSE only (excluding loans), respect currency/unified mode
                val currentExpenses = if (isUnified) {
                    currentMonthTxs.filter { it.transactionType == TransactionType.EXPENSE && it.loanId == null }
                } else {
                    currentMonthTxs.filter {
                        it.transactionType == TransactionType.EXPENSE && it.currency == selectedCurrency && it.loanId == null
                    }
                }

                // Group by day and sum amounts (convert if unified mode)
                val dailySums = mutableMapOf<LocalDate, BigDecimal>()
                for (tx in currentExpenses) {
                    val day = tx.dateTime.toLocalDate()
                    val amount = if (isUnified && tx.currency != selectedCurrency) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
                    } else {
                        tx.amount
                    }
                    dailySums[day] = (dailySums[day] ?: BigDecimal.ZERO) + amount
                }

                // Build cumulative list: one entry per day from 1st to today
                val cumulativeList = mutableListOf<BigDecimal>()
                var cumulative = BigDecimal.ZERO
                var day = firstOfMonth
                while (!day.isAfter(now)) {
                    cumulative += (dailySums[day] ?: BigDecimal.ZERO)
                    cumulativeList.add(cumulative)
                    day = day.plusDays(1)
                }

                // Build last month's cumulative spending (same day count for comparison)
                val lastMonthExpenses = if (isUnified) {
                    lastMonthTxs.filter { it.transactionType == TransactionType.EXPENSE && it.loanId == null }
                } else {
                    lastMonthTxs.filter {
                        it.transactionType == TransactionType.EXPENSE && it.currency == selectedCurrency && it.loanId == null
                    }
                }

                val lastMonthDailySums = mutableMapOf<LocalDate, BigDecimal>()
                for (tx in lastMonthExpenses) {
                    val txDay = tx.dateTime.toLocalDate()
                    val amount = if (isUnified && tx.currency != selectedCurrency) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
                    } else {
                        tx.amount
                    }
                    lastMonthDailySums[txDay] = (lastMonthDailySums[txDay] ?: BigDecimal.ZERO) + amount
                }

                val daysToInclude = now.dayOfMonth
                val lastMonthCumulative = mutableListOf<BigDecimal>()
                var lastCum = BigDecimal.ZERO
                var lastDay = lastMonthStart
                var dayCount = 0
                while (dayCount < daysToInclude && lastDay < firstOfMonth) {
                    lastCum += (lastMonthDailySums[lastDay] ?: BigDecimal.ZERO)
                    lastMonthCumulative.add(lastCum)
                    lastDay = lastDay.plusDays(1)
                    dayCount++
                }

                _uiState.value = _uiState.value.copy(
                    spendingHistory = cumulativeList,
                    balanceHistory = cumulativeList,
                    lastMonthSpendingHistory = lastMonthCumulative
                )
                calculateMonthlyChange()
            }
        }

        viewModelScope.launch {
            // Load active loans summary for home carousel
            combine(
                loanRepository.getActiveLoans(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { loans, isUnified, displayCurrency ->
                if (loans.isEmpty()) null
                else Triple(loans, isUnified, displayCurrency)
            }.collect { summary ->
                if (summary == null) {
                    _uiState.value = _uiState.value.copy(loanSummary = null)
                    return@collect
                }

                val (loans, isUnified, displayCurrency) = summary
                val selectedCurrency = if (isUnified) displayCurrency else _uiState.value.selectedCurrency

                val loanCurrencies = loans.map { it.currency }.distinct()
                if (isUnified && loanCurrencies.size > 1) {
                    currencyConversionService.refreshExchangeRatesForAccount((loanCurrencies + selectedCurrency).distinct())
                }

                val loansForTotals = if (isUnified) {
                    loans
                } else {
                    loans.filter { it.currency.equals(selectedCurrency, ignoreCase = true) }
                }

                var lentTotal = BigDecimal.ZERO
                var borrowedTotal = BigDecimal.ZERO
                for (loan in loansForTotals) {
                    val amount = if (isUnified) {
                        currencyConversionService.convertAmount(
                            amount = loan.remainingAmount,
                            fromCurrency = loan.currency,
                            toCurrency = selectedCurrency
                        )
                    } else {
                        loan.remainingAmount
                    }
                    when (loan.direction) {
                        com.pennywiseai.tracker.data.database.entity.LoanDirection.LENT -> lentTotal += amount
                        com.pennywiseai.tracker.data.database.entity.LoanDirection.BORROWED -> borrowedTotal += amount
                    }
                }

                _uiState.value = _uiState.value.copy(
                    loanSummary = LoanSummary(
                        activeLoans = loans,
                        totalLentRemaining = lentTotal,
                        totalBorrowedRemaining = borrowedTotal
                    )
                )
            }
        }

        viewModelScope.launch {
            // Load transaction heatmap (last 26 weeks / 182 days)
            val heatmapStart = LocalDate.now().minusDays(182)
            combine(
                transactionRepository.getTransactionsBetweenDates(
                    startDate = heatmapStart,
                    endDate = LocalDate.now()
                ),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
            }.collect { transactions ->
                val heatmap = transactions
                    .groupBy { it.dateTime.toLocalDate().toEpochDay() }
                    .mapValues { it.value.size }
                _uiState.value = _uiState.value.copy(transactionHeatmap = heatmap)
            }
        }

        viewModelScope.launch {
            // Load recent items: ungrouped transactions + groups, merged and sorted by most recent activity
            val rawGroupsFlow = transactionGroupRepository.getAllGroups().flatMapLatest { groups ->
                if (groups.isEmpty()) flowOf(emptyList())
                else combine(groups.map { group ->
                    transactionGroupRepository.getTransactionsForGroup(group.id)
                        .map { txns -> group to txns }
                }) { it.toList() }
            }

            combine(
                combine(
                    transactionGroupRepository.getRecentUngroupedTransactions(),
                    _cachedAccountBalances
                ) { ungrouped, balances ->
                    val profileId = _uiState.value.selectedProfileId
                    val keys = buildProfileAccountKeys(balances ?: emptyList())
                    filterTransactionsByProfile(ungrouped, profileId, keys)
                        .map { HomeRecentItem.SingleTransaction(it) }
                },
                combine(
                    rawGroupsFlow,
                    _cachedAccountBalances
                ) { groupPairs, balances ->
                    val profileId = _uiState.value.selectedProfileId
                    val keys = buildProfileAccountKeys(balances ?: emptyList())
                    groupPairs.mapNotNull { (group, txns) ->
                        val filtered = filterTransactionsByProfile(txns, profileId, keys)
                        if (filtered.isEmpty()) null
                        else HomeRecentItem.GroupItem(group, filtered)
                    }
                },
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { singles, groups, isUnified, displayCurrency ->
                val merged = (singles + groups)
                    .sortedByDescending { it.sortTime }
                    .take(5)

                if (!isUnified) return@combine merged

                merged.map { item ->
                    when (item) {
                        is HomeRecentItem.SingleTransaction -> {
                            val converted = if (!item.transaction.currency.equals(displayCurrency, ignoreCase = true))
                                currencyConversionService.convertAmount(item.transaction.amount, item.transaction.currency, displayCurrency)
                            else null
                            item.copy(convertedAmount = converted)
                        }
                        is HomeRecentItem.GroupItem -> {
                            val amounts = item.transactions
                                .filter { !it.currency.equals(displayCurrency, ignoreCase = true) }
                                .associate { tx ->
                                    tx.id to currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                                }
                            item.copy(convertedAmounts = amounts)
                        }
                    }
                }
            }.collect { items ->
                _uiState.value = _uiState.value.copy(recentItems = items, isLoading = false)
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
                    budgetSummary = summary
                )
            }
        }
    }
    
    private fun calculateMonthlyChange() {
        val currentExpenses = _uiState.value.currentMonthExpenses
        val lastExpenses = _uiState.value.lastMonthExpenses
        val change = currentExpenses - lastExpenses
        val changePercent = if (lastExpenses != BigDecimal.ZERO) {
            change.multiply(BigDecimal(100)).divide(lastExpenses, 0, RoundingMode.HALF_UP).toInt()
        } else {
            0
        }
        _uiState.value = _uiState.value.copy(
            monthlyChange = change,
            monthlyChangePercent = changePercent
        )
    }
    
    fun refreshHiddenAccounts() {
        viewModelScope.launch {
            // Use cached balances instead of re-fetching from the repository
            val allBalances = cachedAccountBalances
            if (allBalances.isEmpty()) return@launch

            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

            val visibleBalances = filterVisibleBalances(allBalances, hiddenAccounts)

            val rawRegularAccounts = visibleBalances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
            val rawCreditCards = visibleBalances.filter { it.isCreditCard }

            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode

            // Pre-convert individual account entities when unified mode is on
            val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
            val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

            var totalBalance = BigDecimal.ZERO
            for (account in regularAccounts) {
                if (account.currency == selectedCurrency) {
                    totalBalance += account.balance
                } else if (currencyConversionService.hasValidRate(account.currency, selectedCurrency)) {
                    totalBalance += currencyConversionService.convertAmount(
                        amount = account.balance,
                        fromCurrency = account.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }
            var totalAvailableCredit = BigDecimal.ZERO
            for (card in creditCards) {
                val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                if (card.currency == selectedCurrency) {
                    totalAvailableCredit += availableInCardCurrency
                } else if (currencyConversionService.hasValidRate(card.currency, selectedCurrency)) {
                    totalAvailableCredit += currencyConversionService.convertAmount(
                        amount = availableInCardCurrency,
                        fromCurrency = card.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }
            // Determine if balance is ready (all conversions successful)
            val accountCurrencies = regularAccounts.map { it.currency }.distinct()
            val creditCardCurrencies = creditCards.map { it.currency }.distinct()
            val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()
            val needsConversion = allAccountCurrencies.size > 1 &&
                allAccountCurrencies.any { it != selectedCurrency }
            val balanceReady = if (needsConversion) {
                allAccountCurrencies
                    .filter { it != selectedCurrency }
                    .all { currency ->
                        currencyConversionService.hasValidRate(currency, selectedCurrency)
                    }
            } else {
                true
            }

            _uiState.value = _uiState.value.copy(
                accountBalances = regularAccounts,
                creditCards = creditCards,
                totalBalance = totalBalance,
                totalAvailableCredit = totalAvailableCredit,
                isBalanceReady = balanceReady
            )
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
            // Use cached balances instead of starting a new .collect — this prevents
            // a race condition where two competing collectors would cause the balance
            // to show with the wrong currency symbol.
            val allBalances = cachedAccountBalances
            if (allBalances.isEmpty()) return@launch

            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

            val balances = filterVisibleBalances(allBalances, hiddenAccounts)
            val rawRegularAccounts = balances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
            val rawCreditCards = balances.filter { it.isCreditCard }

            val accountCurrencies = rawRegularAccounts.map { it.currency }.distinct()
            val creditCardCurrencies = rawCreditCards.map { it.currency }.distinct()
            val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()

            if (allAccountCurrencies.size > 1 && allAccountCurrencies.isNotEmpty()) {
                currencyConversionService.refreshExchangeRatesForAccount(allAccountCurrencies)
            }

            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode

            // Pre-convert individual account entities when unified mode is on
            val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
            val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

            var totalBalanceInSelectedCurrency = BigDecimal.ZERO
            for (account in regularAccounts) {
                if (account.currency == selectedCurrency) {
                    totalBalanceInSelectedCurrency += account.balance
                } else if (currencyConversionService.hasValidRate(account.currency, selectedCurrency)) {
                    totalBalanceInSelectedCurrency += currencyConversionService.convertAmount(
                        amount = account.balance,
                        fromCurrency = account.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }

            var totalAvailableCreditInSelectedCurrency = BigDecimal.ZERO
            for (card in creditCards) {
                val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                if (card.currency == selectedCurrency) {
                    totalAvailableCreditInSelectedCurrency += availableInCardCurrency
                } else if (currencyConversionService.hasValidRate(card.currency, selectedCurrency)) {
                    totalAvailableCreditInSelectedCurrency += currencyConversionService.convertAmount(
                        amount = availableInCardCurrency,
                        fromCurrency = card.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }

            // Determine if balance is ready (all conversions successful)
            val needsConversion = allAccountCurrencies.size > 1 &&
                allAccountCurrencies.any { it != selectedCurrency }
            val balanceReady = if (needsConversion) {
                allAccountCurrencies
                    .filter { it != selectedCurrency }
                    .all { currency ->
                        currencyConversionService.hasValidRate(currency, selectedCurrency)
                    }
            } else {
                true
            }

            _uiState.value = _uiState.value.copy(
                accountBalances = regularAccounts,
                creditCards = creditCards,
                totalBalance = totalBalanceInSelectedCurrency,
                totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                isBalanceReady = balanceReady
            )
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
        hasUserSelectedCurrency = true
        _uiState.value = _uiState.value.copy(isBalanceReady = false)
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

            val allTransactions = transactionRepository.getTransactionsBetweenDates(
                startDate = startOfMonth,
                endDate = endOfMonth
            ).first()
            val transactions = filterTransactions(allTransactions)
            updateTransactionTypeTotals(transactions)
        }
    }

    private fun updateTransactionTypeTotals(transactions: List<TransactionEntity>) {
        val selectedCurrency = _uiState.value.selectedCurrency
        val isUnified = _uiState.value.isUnifiedMode
        val nonLoanTransactions = transactions.filter { it.loanId == null }

        if (isUnified) {
            // Convert all transactions to display currency
            viewModelScope.launch {
                var creditCardTotal = BigDecimal.ZERO
                var transferTotal = BigDecimal.ZERO
                var investmentTotal = BigDecimal.ZERO

                for (tx in nonLoanTransactions) {
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
            val currencyTransactions = nonLoanTransactions.filter { it.currency == selectedCurrency }

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

        // Update available currencies — merge transaction currencies with existing account currencies
        val transactionCurrencies = (currentMonthBreakdownMap.keys + lastMonthBreakdownMap.keys)
        val existingCurrencies = _uiState.value.availableCurrencies
        val availableCurrencies = (existingCurrencies + transactionCurrencies).distinct().sortedWith { a, b ->
            when {
                a == baseCurrency -> -1
                b == baseCurrency -> 1
                else -> a.compareTo(b)
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
                val loanLoss = aggregateAcrossCurrencies(currentMonthLoanLossByCurrency, selectedCurrency, isUnified = true)
                val lent = aggregateAcrossCurrencies(currentMonthLentByCurrency, selectedCurrency, isUnified = true)

                _uiState.value = _uiState.value.copy(
                    currentMonthTotal = currentBreakdown.total - loanLoss,
                    currentMonthIncome = currentBreakdown.income,
                    currentMonthExpenses = currentBreakdown.expenses + loanLoss,
                    currentMonthLent = lent,
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

            val loanLoss = currentMonthLoanLossByCurrency[selectedCurrency] ?: BigDecimal.ZERO
            val lent = currentMonthLentByCurrency[selectedCurrency] ?: BigDecimal.ZERO

            _uiState.value = _uiState.value.copy(
                currentMonthTotal = currentBreakdown.total - loanLoss,
                currentMonthIncome = currentBreakdown.income,
                currentMonthExpenses = currentBreakdown.expenses + loanLoss,
                currentMonthLent = lent,
                lastMonthTotal = lastBreakdown.total,
                lastMonthIncome = lastBreakdown.income,
                lastMonthExpenses = lastBreakdown.expenses,
                selectedCurrency = selectedCurrency,
                availableCurrencies = availableCurrencies
            )
            calculateMonthlyChange()
        }
    }

    private suspend fun aggregateAcrossCurrencies(
        byCurrency: Map<String, BigDecimal>,
        selectedCurrency: String,
        isUnified: Boolean
    ): BigDecimal {
        if (byCurrency.isEmpty()) return BigDecimal.ZERO
        if (!isUnified) return byCurrency[selectedCurrency] ?: BigDecimal.ZERO
        var total = BigDecimal.ZERO
        for ((currency, amount) in byCurrency) {
            total += if (currency == selectedCurrency) amount
            else currencyConversionService.convertAmount(amount, currency, selectedCurrency)
        }
        return total
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

    private suspend fun convertAccountEntities(
        entities: List<AccountBalanceEntity>,
        targetCurrency: String,
        isUnifiedMode: Boolean
    ): List<AccountBalanceEntity> {
        if (!isUnifiedMode) return entities
        return entities.map { account ->
            if (account.currency == targetCurrency) {
                account
            } else {
                val convertedBalance = currencyConversionService.convertAmount(
                    amount = account.balance,
                    fromCurrency = account.currency,
                    toCurrency = targetCurrency
                )
                val convertedCreditLimit = if (account.isCreditCard && account.creditLimit != null) {
                    currencyConversionService.convertAmount(
                        amount = account.creditLimit,
                        fromCurrency = account.currency,
                        toCurrency = targetCurrency
                    )
                } else {
                    account.creditLimit
                }
                account.copy(
                    balance = convertedBalance,
                    creditLimit = convertedCreditLimit,
                    currency = targetCurrency
                )
            }
        }
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
            if (txWithSplits.transaction.transactionType != TransactionType.INCOME &&
                txWithSplits.transaction.transactionType != TransactionType.TRANSFER &&
                txWithSplits.transaction.transactionType != TransactionType.INVESTMENT) {
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
    val userName: String = "User",
    val profileImageUri: String? = null,
    val profileBackgroundColor: Int = 0,
    val currentMonthTotal: BigDecimal = BigDecimal.ZERO,
    val currentMonthIncome: BigDecimal = BigDecimal.ZERO,
    val currentMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val currentMonthLent: BigDecimal = BigDecimal.ZERO,
    val currentMonthCreditCard: BigDecimal = BigDecimal.ZERO,
    val currentMonthTransfer: BigDecimal = BigDecimal.ZERO,
    val currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    val lastMonthTotal: BigDecimal = BigDecimal.ZERO,
    val lastMonthIncome: BigDecimal = BigDecimal.ZERO,
    val lastMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthlyChange: BigDecimal = BigDecimal.ZERO,
    val monthlyChangePercent: Int = 0,
    val recentTransactions: List<TransactionEntity> = emptyList(), // kept for widget compat
    val recentItems: List<HomeRecentItem> = emptyList(),
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
    val spendingHistory: List<BigDecimal> = emptyList(),
    val balanceHistory: List<BigDecimal> = emptyList(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val showBreakdownDialog: Boolean = false,
    val isUnifiedMode: Boolean = false,
    val transactionHeatmap: Map<Long, Int> = emptyMap(),
    val isBalanceHidden: Boolean = true,
    val isBalanceReady: Boolean = false,
    val lastMonthSpendingHistory: List<BigDecimal> = emptyList(),
    val loanSummary: LoanSummary? = null,
    val selectedProfileId: Long? = null,
    val profiles: List<ProfileEntity> = emptyList()
)

data class LoanSummary(
    val activeLoans: List<LoanEntity>,
    val totalLentRemaining: BigDecimal,
    val totalBorrowedRemaining: BigDecimal
)