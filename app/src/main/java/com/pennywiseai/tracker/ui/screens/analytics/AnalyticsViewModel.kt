package com.pennywiseai.tracker.ui.screens.analytics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.AccountOption
import com.pennywiseai.tracker.presentation.common.accountOptions
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.domain.model.BudgetCycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.pennywiseai.tracker.ui.components.BalancePoint

enum class ChartType { LINE, BAR, HEATMAP }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val profileRepository: ProfileRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    // name -> hex color for the user's categories (incl. recolored built-ins), so
    // Analytics renders each category in its assigned color instead of gray (#586).
    private val categoryColors: Flow<Map<String, String>> =
        categoryRepository.getAllCategories()
            .map { cats -> cats.associate { it.name to it.color } }

    // Profile filter — reuses the global Home profile selection so the two stay in sync.
    val selectedProfileId: StateFlow<Long?> = userPreferencesRepository.selectedProfileId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProfile(id: Long?) {
        viewModelScope.launch { userPreferencesRepository.updateSelectedProfileId(id) }
    }
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()
    
    private val _transactionTypeFilter = MutableStateFlow(TransactionTypeFilter.EXPENSE)
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = _transactionTypeFilter.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("")
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    // Account filter — null means "All accounts". Key is "bankName_accountLast4".
    private val _accountFilter = MutableStateFlow<String?>(null)
    val accountFilter: StateFlow<String?> = _accountFilter.asStateFlow()

    // Pickable account options for the account filter dropdown (alias-preferred labels).
    val accountOptions: StateFlow<List<AccountOption>> =
        accountBalanceRepository.getAllLatestBalances()
            .map { accountOptions(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isUnifiedMode = MutableStateFlow(false)
    val isUnifiedMode: StateFlow<Boolean> = _isUnifiedMode.asStateFlow()

    private val _selectedChartType = MutableStateFlow(ChartType.LINE)
    val selectedChartType: StateFlow<ChartType> = _selectedChartType.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.getAnalyticsChartType().collect { saved ->
                if (saved != null) {
                    try {
                        _selectedChartType.value = ChartType.valueOf(saved)
                    } catch (_: IllegalArgumentException) { }
                }
            }
        }

        // Load unified mode and baseCurrency preferences
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            _selectedCurrency.value = baseCurrency
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { unifiedMode, displayCurrency ->
                unifiedMode to displayCurrency
            }.collect { (unifiedMode, displayCurrency) ->
                _isUnifiedMode.value = unifiedMode
                if (unifiedMode) {
                    _selectedCurrency.value = displayCurrency
                }
            }
        }
    }

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

    private val _availableCurrencies = MutableStateFlow<List<String>>(emptyList())
    val availableCurrencies: StateFlow<List<String>> = _availableCurrencies.asStateFlow()

    // Reactive UI state that automatically updates when any filter changes
    // Uses flatMapLatest to cancel previous data loads when filters change (prevents race conditions)
    val uiState: StateFlow<AnalyticsUiState> = combine(
        _selectedPeriod,
        customDateRange,
        _transactionTypeFilter,
        _selectedCurrency,
        combine(_isUnifiedMode, _categoryFilter, selectedProfileId, _accountFilter) { u, c, p, a ->
            UnifiedCatProfileAccount(u, c, p, a)
        }
    ) { period, customRange, typeFilter, currency, extra ->
        FilterState(
            period,
            customRange,
            typeFilter,
            currency,
            extra.isUnified,
            extra.categoryFilter,
            extra.profileId,
            extra.accountFilter
        )
    }.combine(accountBalanceRepository.getAllLatestBalances()) { fs, balances ->
        fs to balances
    }.combine(categoryColors) { (fs, balances), colors ->
        Triple(fs, balances, colors)
    }.combine(tagRepository.observeTransactionTagNames()) { triple, tagMap ->
        triple to tagMap
    }.flatMapLatest { (triple, tagMap) ->
        val (filterState, balances, categoryColorMap) = triple
        // Determine date range based on selected period. THIS_MONTH is special:
        // it follows the user's custom budget cycle (e.g. 25th → 24th) instead
        // of the calendar month, so the analytics range lines up with the
        // home/cycle everywhere else in the app.
        val dateRange = if (filterState.period == TimePeriod.CUSTOM) {
            val customRange = filterState.customRange
            // Guard against invalid state: CUSTOM period must have a date range
            if (customRange == null) {
                android.util.Log.e("AnalyticsViewModel",
                    "CUSTOM period selected but no date range set - falling back to THIS_MONTH")
                // Auto-correct the invalid state
                _selectedPeriod.value = TimePeriod.THIS_MONTH
                getThisCycleRange()
            } else {
                customRange
            }
        } else if (filterState.period == TimePeriod.THIS_MONTH) {
            getThisCycleRange()
        } else {
            getDateRangeForPeriod(filterState.period)
        }

        if (dateRange == null) {
            // No valid date range, return empty state
            flowOf(AnalyticsUiState(isLoading = false))
        } else {
            // First load all transactions for the date range to get available currencies
            transactionRepository.getTransactionsBetweenDates(
                startDate = dateRange.first,
                endDate = dateRange.second
            ).flatMapLatest { allTransactions ->
                // Update available currencies using standard sorting (INR first, then alphabetical)
                val allCurrencies = CurrencyUtils.sortCurrencies(
                    allTransactions.map { it.currency }.distinct()
                )
                _availableCurrencies.value = allCurrencies

                // Auto-select primary currency if not already selected or if current currency no longer exists
                val currentSelectedCurrency = filterState.currency
                if (!allCurrencies.contains(currentSelectedCurrency) && allCurrencies.isNotEmpty()) {
                    val baseCurrency = _selectedCurrency.value.ifEmpty { allCurrencies.first() }
                    _selectedCurrency.value = if (allCurrencies.contains(baseCurrency)) baseCurrency else allCurrencies.first()
                }

                // Use database-level filtering for better performance
                // Convert TransactionTypeFilter to TransactionType for database query
                val dbTransactionType = when (filterState.typeFilter) {
                    TransactionTypeFilter.ALL -> null // null means no type filter at DB level
                    TransactionTypeFilter.INCOME -> com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME
                    TransactionTypeFilter.EXPENSE -> com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE
                    TransactionTypeFilter.CREDIT -> com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT
                    TransactionTypeFilter.TRANSFER -> com.pennywiseai.tracker.data.database.entity.TransactionType.TRANSFER
                    TransactionTypeFilter.INVESTMENT -> com.pennywiseai.tracker.data.database.entity.TransactionType.INVESTMENT
                }

                // Scope a loaded transaction list to the selected profile and exclude
                // hidden accounts (mirrors the Home screen's account scoping).
                fun scopeTransactions(
                    txs: List<TransactionWithSplits>
                ): List<TransactionWithSplits> {
                    val hidden = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
                        .getStringSet("hidden_accounts", emptySet()) ?: emptySet()
                    val profileKeys = buildProfileAccountKeys(balances)
                    val keptIds = filterTransactionsByProfile(
                        txs.map { it.transaction },
                        filterState.profileId,
                        profileKeys
                    ).mapTo(HashSet()) { it.id }
                    val accountKey = filterState.accountFilter
                    return txs.filter { tw ->
                        val txAccountKey =
                            "${tw.transaction.bankName}_${tw.transaction.accountNumber}"
                        tw.transaction.id in keptIds &&
                            txAccountKey !in hidden &&
                            (accountKey.isNullOrBlank() || txAccountKey == accountKey)
                    }
                }

                // Load transactions with splits for proper category breakdown
                if (filterState.isUnifiedMode) {
                    // Unified mode: load ALL currencies
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = dateRange.first,
                        endDate = dateRange.second
                    ).map { txs -> Triple(scopeTransactions(txs), dbTransactionType, true) }
                } else {
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = dateRange.first,
                        endDate = dateRange.second,
                        currency = filterState.currency
                    ).map { txs -> Triple(scopeTransactions(txs), dbTransactionType, false) }
                }
            }.mapLatest { (allTransactionsWithSplits, transactionTypeFilter, isUnified) ->
                // Filter by transaction type in memory (splits are already loaded)
                // Exclude loan repayments — they are fixed obligations, not discretionary spending
                val filteredTransactionsWithSplits = (if (transactionTypeFilter != null) {
                    allTransactionsWithSplits.filter { it.transaction.transactionType == transactionTypeFilter }
                } else {
                    allTransactionsWithSplits
                })
                    .filter { it.transaction.loanId == null }
                    // Drop transactions the user excluded from analytics (#451). They
                    // stay in history and count toward balance — only these breakdowns,
                    // totals, averages and the spending trend ignore them.
                    .filter { !it.transaction.excludedFromAnalytics }

                // Compute available categories BEFORE applying category filter
                val allCategoryNames = filteredTransactionsWithSplits
                    .map { txWithSplits -> txWithSplits.getAmountByCategory().keys }
                    .flatten()
                    .map { it.ifEmpty { "Others" } }
                    .distinct()
                    .sorted()

                // Apply category filter
                val categoryFilteredWithSplits = filterState.categoryFilter?.let { cat ->
                    filteredTransactionsWithSplits.filter { txWithSplits ->
                        txWithSplits.getAmountByCategory().keys
                            .map { it.ifEmpty { "Others" } }
                            .contains(cat)
                    }
                } ?: filteredTransactionsWithSplits

                val filteredTransactions = categoryFilteredWithSplits.map { it.transaction }
                val displayCurrency = _selectedCurrency.value

                // Calculate total — convert if unified mode
                var totalSpending = BigDecimal.ZERO
                if (isUnified) {
                    for (tx in filteredTransactions) {
                        totalSpending += currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                    }
                } else {
                    totalSpending = filteredTransactions.map { it.amount.toDouble() }.sum().toBigDecimal()
                }

                // Build category breakdown considering splits
                val categoryAmounts = mutableMapOf<String, BigDecimal>()
                val categoryTransactionCounts = mutableMapOf<String, Int>()

                for (txWithSplits in categoryFilteredWithSplits) {
                    val fromCurrency = txWithSplits.transaction.currency
                    txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                        val categoryName = category.ifEmpty { "Others" }
                        val converted = if (isUnified) {
                            currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
                        } else {
                            amount
                        }
                        categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                        categoryTransactionCounts[categoryName] = (categoryTransactionCounts[categoryName] ?: 0) + 1
                    }
                }

                // Net "Refund" income (INCOME + DEDUCT_SPENT) out of Expenses so EVERY
                // spend surface — total, category, account, merchant and the trend —
                // agrees with the Home card and the Transactions page (both already net
                // of refunds). A categorised refund subtracts from its budgetCategory;
                // it also shrinks the headline total, its account, its merchant (when
                // the refund carries the same merchant) and the trend on its day. All
                // spend surfaces are floored at zero. Refunds live in
                // allTransactionsWithSplits (loaded untyped) but were dropped by the
                // EXPENSE type filter above, so pull them back in here. Only the Expense
                // view is adjusted; Income/other views are untouched.
                val refundByAccount = mutableMapOf<String, BigDecimal>()
                val refundByMerchant = mutableMapOf<String, BigDecimal>()
                val refundByDay = mutableMapOf<LocalDate, BigDecimal>()
                if (filterState.typeFilter == TransactionTypeFilter.EXPENSE) {
                    for (tw in allTransactionsWithSplits) {
                        val tx = tw.transaction
                        if (tx.loanId != null || tx.excludedFromAnalytics) continue
                        if (tx.transactionType != com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME ||
                            tx.budgetImpactType != com.pennywiseai.tracker.data.database.entity.BudgetImpactType.DEDUCT_SPENT
                        ) continue
                        val category = (tx.budgetCategory ?: "").ifEmpty { "Others" }
                        // Respect an active category filter.
                        if (filterState.categoryFilter != null && filterState.categoryFilter != category) continue
                        val converted = if (isUnified) {
                            currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                        } else tx.amount
                        if (converted <= BigDecimal.ZERO) continue
                        // A refund (INCOME + DEDUCT_SPENT) reverses a prior expense, so the
                        // FULL amount always comes off the headline total — keeping Analytics
                        // consistent with the Home card, widget and Transactions page, which
                        // are all net of refunds. The per-category / account / merchant / trend
                        // buckets floor at zero (a bucket can't show negative spend), so an
                        // over-refund or an orphaned refund (whose category has no expense
                        // here) can leave the total below the sum of the visible rows — exactly
                        // as it does on the Home card.
                        val existing = categoryAmounts[category] ?: BigDecimal.ZERO
                        categoryAmounts[category] = (existing - converted).coerceAtLeast(BigDecimal.ZERO)
                        totalSpending -= converted
                        val accountKey = "${tx.bankName}_${tx.accountNumber}"
                        refundByAccount[accountKey] = (refundByAccount[accountKey] ?: BigDecimal.ZERO) + converted
                        refundByMerchant[tx.merchantName] = (refundByMerchant[tx.merchantName] ?: BigDecimal.ZERO) + converted
                        val day = tx.dateTime.toLocalDate()
                        refundByDay[day] = (refundByDay[day] ?: BigDecimal.ZERO) + converted
                    }
                    totalSpending = totalSpending.coerceAtLeast(BigDecimal.ZERO)
                }

                val categoryBreakdown = categoryAmounts.map { (categoryName, categoryTotal) ->
                    CategoryData(
                        name = categoryName,
                        amount = categoryTotal,
                        // Clamp at 100%: when an over-refund pushes the total below a
                        // category's floored spend, the raw ratio can exceed 100%, which
                        // reads as nonsensical. A category is never "more than all spending".
                        percentage = if (totalSpending > BigDecimal.ZERO) {
                            (categoryTotal.divide(totalSpending, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat().coerceAtMost(100f)
                        } else 0f,
                        transactionCount = categoryTransactionCounts[categoryName] ?: 0,
                        color = categoryColorMap[categoryName]
                    )
                }.sortedByDescending { it.amount }

                // Build tag breakdown from the many-to-many tag map. A
                // transaction can carry several tags, and its full amount
                // counts toward each of them (tags overlap, unlike categories),
                // so tag percentages can sum to more than 100%. Untagged
                // transactions are skipped.
                val tagAmounts = mutableMapOf<String, BigDecimal>()
                val tagTransactionCounts = mutableMapOf<String, Int>()

                for (tx in filteredTransactions) {
                    val tagNames = tagMap[tx.id]?.takeIf { it.isNotEmpty() } ?: continue
                    val converted = if (isUnified) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                    } else {
                        tx.amount
                    }
                    for (tagName in tagNames) {
                        tagAmounts[tagName] = (tagAmounts[tagName] ?: BigDecimal.ZERO) + converted
                        tagTransactionCounts[tagName] = (tagTransactionCounts[tagName] ?: 0) + 1
                    }
                }

                val tagBreakdown = tagAmounts.map { (tagName, tagTotal) ->
                    TagData(
                        name = tagName,
                        amount = tagTotal,
                        // Clamp at 100% like categoryBreakdown: refunds reduce totalSpending
                        // but not a tag's summed amount, so the raw ratio for a single tag can
                        // exceed 100%. (Different tags can still each be <100% yet sum to more,
                        // since a transaction counts toward every tag it carries.)
                        percentage = if (totalSpending > BigDecimal.ZERO) {
                            (tagTotal.divide(totalSpending, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat().coerceAtMost(100f)
                        } else 0f,
                        transactionCount = tagTransactionCounts[tagName] ?: 0
                    )
                }.sortedByDescending { it.amount }

                // Group by merchant — convert if unified
                val merchantBreakdown = filteredTransactions
                    .groupBy { it.merchantName }
                    .entries
                    .map { (merchant, txns) ->
                        val gross = if (isUnified) {
                            var sum = BigDecimal.ZERO
                            for (tx in txns) {
                                sum += currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                            }
                            sum
                        } else {
                            txns.map { it.amount.toDouble() }.sum().toBigDecimal()
                        }
                        // Net any refund that carries this merchant's name (floored at zero).
                        val merchantAmount = (gross - (refundByMerchant[merchant] ?: BigDecimal.ZERO))
                            .coerceAtLeast(BigDecimal.ZERO)
                        MerchantData(
                            name = merchant,
                            amount = merchantAmount,
                            transactionCount = txns.size,
                            isSubscription = txns.any { it.isRecurring }
                        )
                    }
                    .sortedByDescending { it.amount }
                    .take(10)

                // Group by account — convert if unified. Labels prefer the alias
                // (via accountOptions) and fall back to "$bankName ••$last4".
                val accountLabels = accountOptions(balances).associate { it.key to it.label }
                val accountBreakdown = filteredTransactions
                    .groupBy { "${it.bankName}_${it.accountNumber}" }
                    .entries
                    .map { (accountKey, txns) ->
                        val gross = if (isUnified) {
                            var sum = BigDecimal.ZERO
                            for (tx in txns) {
                                sum += currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                            }
                            sum
                        } else {
                            txns.sumOf { it.amount.toDouble() }.toBigDecimal()
                        }
                        // Net refunds credited back to this account (floored at zero) so
                        // the account total — and its % of the net grand total — stay sane.
                        val accountAmount = (gross - (refundByAccount[accountKey] ?: BigDecimal.ZERO))
                            .coerceAtLeast(BigDecimal.ZERO)
                        AccountBreakdownData(
                            key = accountKey,
                            label = accountLabels[accountKey] ?: run {
                                val first = txns.first()
                                com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
                                    .accountLabel(first.bankName ?: "Unknown", first.accountNumber ?: "----")
                            },
                            amount = accountAmount,
                            percentage = if (totalSpending > BigDecimal.ZERO) {
                                (accountAmount.divide(totalSpending, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat().coerceAtMost(100f)
                            } else 0f,
                            transactionCount = txns.size
                        )
                    }
                    .sortedByDescending { it.amount }

                // Calculate average amount
                val averageAmount = if (filteredTransactions.isNotEmpty()) {
                    totalSpending.divide(BigDecimal(filteredTransactions.size), 2, java.math.RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }

                // Get top category info
                val topCategory = categoryBreakdown.firstOrNull()

                AnalyticsUiState(
                    totalSpending = totalSpending,
                    categoryBreakdown = categoryBreakdown,
                    topMerchants = merchantBreakdown,
                    transactionCount = filteredTransactions.size,
                    averageAmount = averageAmount,
                    topCategory = topCategory?.name,
                    topCategoryPercentage = topCategory?.percentage ?: 0f,
                    currency = displayCurrency,
                    isLoading = false,
                    spendingTrend = calculateSpendingTrend(
                        filteredTransactions, dateRange.first, dateRange.second, isUnified, displayCurrency, refundByDay
                    ),
                    availableCategories = allCategoryNames,
                    accountBreakdown = accountBreakdown,
                    tagBreakdown = tagBreakdown
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        _transactionTypeFilter.value = filter
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }

    fun clearCategoryFilter() {
        _categoryFilter.value = null
    }

    /** Sets the account filter; null clears it ("All accounts"). */
    fun setAccountFilter(accountKey: String?) {
        _accountFilter.value = accountKey
    }

    fun setChartType(type: ChartType) {
        _selectedChartType.value = type
        viewModelScope.launch {
            userPreferencesRepository.saveAnalyticsChartType(type.name)
        }
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

    /**
     * The "This Month" range for the Analytics tab. Honours the user's
     * configured budget cycle start day (e.g. 25th → 24th) instead of the
     * calendar month, so the chart and stats line up with the cycle used
     * everywhere else in the app.
     */
    private suspend fun getThisCycleRange(): Pair<LocalDate, LocalDate> {
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        val (start, end) = BudgetCycle.currentCycle(LocalDate.now(), startDay)
        return start to end
    }

    private suspend fun calculateSpendingTrend(
        transactions: List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate,
        isUnified: Boolean,
        displayCurrency: String,
        refundByDay: Map<LocalDate, BigDecimal> = emptyMap()
    ): List<BalancePoint> {
        val selectedPeriod = _selectedPeriod.value
        val trend = mutableListOf<BalancePoint>()
        // In unified mode the list is multi-currency, so the points are denominated
        // in displayCurrency (each amount converted below). In native mode the list
        // is already single-currency.
        val currency = if (isUnified) {
            displayCurrency
        } else {
            transactions.firstOrNull()?.currency ?: _selectedCurrency.value
        }
        // Pre-convert once (convertAmount is suspend, so it can't run inside the
        // non-suspend map{} below). In unified mode each amount is converted to
        // displayCurrency; in native mode the list is already single-currency.
        val convertedById: Map<Long, Double> = if (isUnified) {
            val byId = HashMap<Long, Double>(transactions.size)
            for (tx in transactions) {
                byId[tx.id] = currencyConversionService
                    .convertAmount(tx.amount, tx.currency, displayCurrency).toDouble()
            }
            byId
        } else {
            emptyMap()
        }
        val amountIn: (com.pennywiseai.tracker.data.database.entity.TransactionEntity) -> Double = { tx ->
            if (isUnified) convertedById[tx.id] ?: tx.amount.toDouble() else tx.amount.toDouble()
        }
        // Refunds already netted out of the total/category/etc. are passed here as a
        // per-day map and subtracted from each bucket below. Each bucket floors at zero
        // so a refund larger than that bucket's spend can't render a negative point.
        fun refundsInRange(start: LocalDate, end: LocalDate): BigDecimal =
            refundByDay.entries
                .filter { !it.key.isBefore(start) && !it.key.isAfter(end) }
                .fold(BigDecimal.ZERO) { acc, e -> acc + e.value }

        when {
            selectedPeriod == TimePeriod.ALL || selectedPeriod == TimePeriod.CURRENT_FY -> {
                val actualStartDate = if (selectedPeriod == TimePeriod.ALL && transactions.isNotEmpty()) {
                    val firstTxDate = transactions.minByOrNull { it.dateTime }?.dateTime?.toLocalDate() ?: startDate
                    if (firstTxDate.isAfter(startDate)) firstTxDate.withDayOfMonth(1) else startDate
                } else {
                    startDate
                }

                val yearsInRange = ChronoUnit.YEARS.between(actualStartDate, endDate)
                val aggregateByYear = selectedPeriod == TimePeriod.ALL && yearsInRange >= 2

                if (aggregateByYear) {
                    var currentYear = actualStartDate.withDayOfYear(1)
                    val lastYear = endDate.withDayOfYear(1)
                    while (!currentYear.isAfter(lastYear) && !currentYear.isAfter(LocalDate.now().withDayOfYear(1))) {
                        val endOfYear = currentYear.withDayOfYear(currentYear.lengthOfYear())
                        val totalAmount = transactions.filter {
                            !it.dateTime.toLocalDate().isBefore(currentYear) && !it.dateTime.toLocalDate().isAfter(endOfYear)
                        }.map(amountIn).sum().toBigDecimal()
                            .minus(refundsInRange(currentYear, endOfYear)).coerceAtLeast(BigDecimal.ZERO)
                        trend.add(BalancePoint(timestamp = currentYear.atStartOfDay(), balance = totalAmount, currency = currency))
                        currentYear = currentYear.plusYears(1)
                    }
                } else {
                    var currentMonth = actualStartDate.withDayOfMonth(1)
                    val lastMonth = endDate.withDayOfMonth(1)
                    while (!currentMonth.isAfter(lastMonth) && !currentMonth.isAfter(LocalDate.now().withDayOfMonth(1))) {
                        val endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
                        val totalAmount = transactions.filter {
                            !it.dateTime.toLocalDate().isBefore(currentMonth) && !it.dateTime.toLocalDate().isAfter(endOfMonth)
                        }.map(amountIn).sum().toBigDecimal()
                            .minus(refundsInRange(currentMonth, endOfMonth)).coerceAtLeast(BigDecimal.ZERO)
                        trend.add(BalancePoint(timestamp = currentMonth.atStartOfDay(), balance = totalAmount, currency = currency))
                        currentMonth = currentMonth.plusMonths(1)
                    }
                }
            }
            else -> {
                val transactionsByDate = transactions.groupBy { it.dateTime.toLocalDate() }
                var currentDate = startDate
                while (!currentDate.isAfter(endDate) && !currentDate.isAfter(LocalDate.now())) {
                    val totalAmount = ((transactionsByDate[currentDate] ?: emptyList()).map(amountIn).sum().toBigDecimal() -
                        (refundByDay[currentDate] ?: BigDecimal.ZERO)).coerceAtLeast(BigDecimal.ZERO)
                    trend.add(BalancePoint(timestamp = currentDate.atStartOfDay(), balance = totalAmount, currency = currency))
                    currentDate = currentDate.plusDays(1)
                }
            }
        }
        return trend
    }
}

/**
 * Internal state for combining all filter parameters.
 * Used in reactive Flow to trigger data reload when any filter changes.
 */
private data class FilterState(
    val period: TimePeriod,
    val customRange: Pair<LocalDate, LocalDate>?,
    val typeFilter: TransactionTypeFilter,
    val currency: String,
    val isUnifiedMode: Boolean = false,
    val categoryFilter: String? = null,
    val profileId: Long? = null,
    val accountFilter: String? = null
)

/**
 * Helper tuple for combining the unified-mode, category, profile, and account
 * filter flows (Kotlin's [combine] caps at a small arity).
 */
private data class UnifiedCatProfileAccount(
    val isUnified: Boolean,
    val categoryFilter: String?,
    val profileId: Long?,
    val accountFilter: String?
)

data class AnalyticsUiState(
    val totalSpending: BigDecimal = BigDecimal.ZERO,
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val topMerchants: List<MerchantData> = emptyList(),
    val transactionCount: Int = 0,
    val averageAmount: BigDecimal = BigDecimal.ZERO,
    val topCategory: String? = null,
    val topCategoryPercentage: Float = 0f,
    val currency: String = "",
    val isLoading: Boolean = true,
    val spendingTrend: List<BalancePoint> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val accountBreakdown: List<AccountBreakdownData> = emptyList(),
    val tagBreakdown: List<TagData> = emptyList()
)

data class AccountBreakdownData(
    val key: String,
    val label: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

data class CategoryData(
    val name: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int,
    // Hex color (e.g. "#4CAF50") of the user's category, when known. Null falls
    // back to the built-in CategoryMapping palette, then gray (#586).
    val color: String? = null
)

data class TagData(
    val name: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

data class MerchantData(
    val name: String,
    val amount: BigDecimal,
    val transactionCount: Int,
    val isSubscription: Boolean
)

