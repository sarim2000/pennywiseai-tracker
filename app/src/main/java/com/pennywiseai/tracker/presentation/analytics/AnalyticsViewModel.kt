package com.pennywiseai.tracker.presentation.analytics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.ledger.countingTransferPairsOnce
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import com.pennywiseai.tracker.domain.spending.SpendForecast
import com.pennywiseai.tracker.domain.spending.analyticsRowsFor
import com.pennywiseai.tracker.domain.spending.defaultTypeFilter
import com.pennywiseai.tracker.domain.spending.netOf
import com.pennywiseai.tracker.domain.spending.refundNetting
import com.pennywiseai.tracker.presentation.common.AccountOption
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.accountOptions
import com.pennywiseai.tracker.presentation.common.getCycleAwareDateRange
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ChartType {
    LINE,
    HEATMAP;

    companion object {
        internal fun fromSaved(value: String?): ChartType = entries.firstOrNull { it.name == value } ?: LINE
    }
}

/**
 * The Analytics type filter: follows the setting-based default (the screen
 * outlives a trip to Settings) until the user picks a type; Clear hands it back
 * to the default, which it then follows again.
 */
internal class TypeFilterSelection {
    private var chosen = false
    private var default = TransactionTypeFilter.EXPENSE
    private val _value = MutableStateFlow(default)
    val value: StateFlow<TransactionTypeFilter> = _value.asStateFlow()

    fun onDefaultChanged(newDefault: TransactionTypeFilter) {
        default = newDefault
        if (!chosen) _value.value = newDefault
    }

    fun pick(filter: TransactionTypeFilter) {
        chosen = true
        _value.value = filter
    }

    fun reset() {
        chosen = false
        _value.value = default
    }
}

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
    entitlementGate: com.pennywiseai.tracker.billing.EntitlementGate,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    // The tag and day-of-week groupings are Pro (#422); basic tagging (create /
    // apply / filter-by-tag) stays free.
    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

    // name -> hex color for the user's categories (incl. recolored built-ins), so
    // Analytics renders each category in its assigned color instead of gray (#586).
    private val categoryColors: Flow<Map<String, String>> =
        categoryRepository.getAllCategories()
            .map { cats -> cats.associate { it.name to it.color } }

    // Colors plus the sub-category → parent map (#374): Analytics rolls each
    // sub-category into its parent, and a parent filter includes its children.
    private class CategoryIndex(
        val colors: Map<String, String>,
        val parentOf: Map<String, String>,
        val all: List<com.pennywiseai.tracker.data.database.entity.CategoryEntity>
    )
    private val categoryIndex: Flow<CategoryIndex> =
        categoryRepository.getAllCategories()
            .map { cats -> CategoryIndex(cats.associate { it.name to it.color }, cats.parentNameOf(), cats) }

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

    /** Drives the period chip's date label (#686). */
    val budgetCycleStartDay: StateFlow<Int> = userPreferencesRepository.budgetCycleStartDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    private val typeFilter = TypeFilterSelection()
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = typeFilter.value

    /** The type view Analytics opens on and resets to; follows the card-spend setting. */
    val defaultTypeFilter: StateFlow<TransactionTypeFilter> = userPreferencesRepository.countCreditCardAsExpense
        .map { defaultTypeFilter(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransactionTypeFilter.EXPENSE)

    private val nativeCurrency = MutableStateFlow("")
    internal val currencyScope: StateFlow<AnalyticsCurrencyScope> = combine(
        nativeCurrency,
        userPreferencesRepository.unifiedCurrencyMode,
        userPreferencesRepository.displayCurrency
    ) { selected, unified, display ->
        AnalyticsCurrencyScope(if (unified) display else selected, unified)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AnalyticsCurrencyScope("", false))

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

    /**
     * The breakdown's grouping (#422), remembered like the chart type. A stored Pro
     * grouping shows as Category while the user isn't Pro (a lapsed subscription).
     */
    val groupBy: StateFlow<AnalyticsGroupBy> = combine(
        userPreferencesRepository.getAnalyticsGroupBy(),
        isProEntitled
    ) { saved, isPro -> AnalyticsGroupBy.fromSaved(saved).allowedFor(isPro) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnalyticsGroupBy.CATEGORY)

    /** Picks a grouping. A Pro one without Pro is ignored; the screen shows the upgrade sheet instead. */
    fun setGroupBy(groupBy: AnalyticsGroupBy) {
        if (groupBy.allowedFor(isProEntitled.value) != groupBy) return
        viewModelScope.launch { userPreferencesRepository.saveAnalyticsGroupBy(groupBy.name) }
    }

    private val _selectedChartType = MutableStateFlow(ChartType.LINE)
    val selectedChartType: StateFlow<ChartType> = _selectedChartType.asStateFlow()

    init {
        viewModelScope.launch {
            defaultTypeFilter.collect { typeFilter.onDefaultChanged(it) }
        }

        viewModelScope.launch {
            userPreferencesRepository.getAnalyticsChartType().collect { saved ->
                _selectedChartType.value = ChartType.fromSaved(saved)
            }
        }

        viewModelScope.launch {
            nativeCurrency.compareAndSet("", userPreferencesRepository.baseCurrency.first())
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
        // Re-emit the period when the cycle start day changes so the data
        // reloads along with the chip label (#686).
        combine(_selectedPeriod, budgetCycleStartDay) { period, _ -> period },
        customDateRange,
        transactionTypeFilter,
        currencyScope,
        combine(_categoryFilter, selectedProfileId, _accountFilter, ::GroupFilters)
    ) { period, customRange, typeFilter, currencyScope, filters ->
        FilterState(
            period,
            customRange,
            typeFilter,
            currencyScope.currency,
            currencyScope.unified,
            filters.categoryFilter,
            filters.profileId,
            filters.accountFilter
        )
    }.combine(accountBalanceRepository.getAllLatestBalances()) { fs, balances ->
        fs to balances
    }.combine(categoryIndex) { (fs, balances), index ->
        Triple(fs, balances, index)
    }.combine(tagRepository.observeTransactionTagNames()) { triple, tagMap ->
        triple to tagMap
    }.flatMapLatest { (triple, tagMap) ->
        val (filterState, balances, catIndex) = triple
        val categoryColorMap = catIndex.colors
        // Roll sub-categories into their parent — except when the user filtered
        // on a sub-category itself: then the row should be that leaf, not its parent.
        // Only the selected sub-category is exempt; other children (e.g. the
        // Groceries half of a Coffee/Groceries split) still roll into their parent.
        val parentOf = catIndex.parentOf - listOfNotNull(filterState.categoryFilter)
        // Determine date range based on selected period. The two "month"
        // periods are special: they follow the user's custom budget cycle
        // (e.g. 25th → 24th) instead of the calendar month, so the analytics
        // range lines up with the home/cycle everywhere else in the app.
        val dateRange = if (filterState.period == TimePeriod.CUSTOM) {
            val customRange = filterState.customRange
            // Guard against invalid state: CUSTOM period must have a date range
            if (customRange == null) {
                android.util.Log.e("AnalyticsViewModel",
                    "CUSTOM period selected but no date range set - falling back to THIS_MONTH")
                // Auto-correct the invalid state
                _selectedPeriod.value = TimePeriod.THIS_MONTH
                getCycleRange(TimePeriod.THIS_MONTH)
            } else {
                customRange
            }
        } else if (filterState.period.followsBudgetCycle) {
            getCycleRange(filterState.period)
        } else {
            getDateRangeForPeriod(filterState.period)
        }

        if (filterState.currency.isBlank()) return@flatMapLatest emptyFlow()
        if (dateRange == null) {
            // No valid date range, return empty state
            flowOf(AnalyticsUiState(isLoading = false))
        } else {
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

                // Only native mode falls back to an available currency. The unified
                // display currency can be absent from the source transactions.
                val currentSelectedCurrency = filterState.currency
                if (!filterState.isUnifiedMode && allCurrencies.isNotEmpty() && currentSelectedCurrency !in allCurrencies) {
                    nativeCurrency.compareAndSet(currentSelectedCurrency, allCurrencies.first())
                    return@flatMapLatest emptyFlow()
                }

                // Load transactions with splits for proper category breakdown
                if (filterState.isUnifiedMode) {
                    // Unified mode: load ALL currencies
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = dateRange.first,
                        endDate = dateRange.second
                    ).map { txs -> scopeTransactions(txs) to true }
                } else {
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = dateRange.first,
                        endDate = dateRange.second,
                        currency = filterState.currency
                    ).map { txs -> scopeTransactions(txs) to false }
                }
            }.mapLatest { (allTransactionsWithSplits, isUnified) ->
                val filteredTransactionsWithSplits =
                    analyticsRowsFor(allTransactionsWithSplits, filterState.typeFilter)

                // Compute available categories BEFORE applying category filter
                val allCategoryNames = filteredTransactionsWithSplits
                    .map { txWithSplits -> txWithSplits.getAmountByCategory().keys }
                    .flatten()
                    .map { it.ifEmpty { "Others" } }
                    .distinct()
                    .sorted()

                // Apply category filter
                // A parent category filter includes its sub-categories (#374).
                val categoryFilterSet = filterState.categoryFilter?.let { catIndex.all.expandWithChildren(listOf(it)) }
                val categoryFilteredWithSplits = categoryFilterSet?.let { set ->
                    filteredTransactionsWithSplits.filter { txWithSplits ->
                        txWithSplits.getAmountByCategory().keys
                            .map { it.ifEmpty { "Others" } }
                            .any { it in set }
                    }
                } ?: filteredTransactionsWithSplits

                val filteredTransactions = categoryFilteredWithSplits.map { it.transaction }
                val displayCurrency = filterState.currency

                // Gross totals and full-transaction groups use the same converted rows.
                val counted = filteredTransactions.mapNotNull { tx ->
                    val amount = if (isUnified) {
                        currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, displayCurrency)
                            ?: return@mapNotNull null // Never-rated pair (#670).
                    } else tx.amount
                    CountedRow(tx, Money(amount, if (isUnified) displayCurrency else tx.currency))
                }
                var totalSpending = counted.sumOf { it.money.amount }

                // Build category breakdown considering splits
                val categoryAmounts = mutableMapOf<String, BigDecimal>()
                val categoryTransactionCounts = mutableMapOf<String, Int>()

                for (txWithSplits in categoryFilteredWithSplits) {
                    val fromCurrency = txWithSplits.transaction.currency
                    txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                        // Sub-categories roll up into their parent row (#374).
                        val categoryName = category.ifEmpty { "Others" }.let { parentOf[it] ?: it }
                        val converted = if (isUnified) {
                            currencyConversionService.convertAmountOrNull(amount, fromCurrency, displayCurrency)
                                ?: return@forEach // never-rated pair — skip, don't face-value (#670)
                        } else {
                            amount
                        }
                        categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                        categoryTransactionCounts[categoryName] = (categoryTransactionCounts[categoryName] ?: 0) + 1
                    }
                }

                // Net refunds out of the spend views (Expense, Spending) — see [refundNetting].
                val refunds = refundNetting(
                    allTransactionsWithSplits, filterState.typeFilter, categoryFilterSet, parentOf
                ) { tx ->
                    if (isUnified) {
                        // never-rated pair → null: skip, don't face-value (#670)
                        currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, displayCurrency)
                    } else tx.amount
                }
                refunds.byCategory.forEach { (category, refund) ->
                    categoryAmounts[category] = netOf(categoryAmounts[category] ?: BigDecimal.ZERO, refund)
                }
                if (filterState.typeFilter.netsRefunds) totalSpending = netOf(totalSpending, refunds.total)
                // Total refund value netted off the headline total. Used to explain the
                // gap when the per-category floor leaves the bars summing above the total (#704).
                val refundNettedTotal = refunds.total

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

                // When refunds over-net a category, its bar floors at zero while the full
                // refund still comes off the total — so the bars can sum above the headline
                // total. Surface the netted refund only when that actually happens, so the
                // UI can explain why the "Spending by category" bars don't add up (#704).
                val categorySum = categoryBreakdown.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
                val refundNettedFromTotal =
                    if (refundNettedTotal > BigDecimal.ZERO && categorySum > totalSpending) refundNettedTotal
                    else BigDecimal.ZERO

                // Tags overlap, unlike categories: a transaction counts in full under
                // each of its tags, so tag totals can add up to more than the total.
                // Untagged rows go under "No tag".
                val noTagLabel = context.getString(R.string.analytics_no_tag)
                val tagBreakdown = groupTotals(counted) { tagKeys(tagMap[it.id]) }
                    .map { (tag, group) ->
                        val amount = group.amountIn(displayCurrency)
                        TagData(
                            name = tag ?: noTagLabel,
                            amount = amount,
                            percentage = shareOf(amount, totalSpending),
                            transactionCount = group.rows.size,
                            isUntagged = tag == null
                        )
                    }
                    .sortedByDescending { it.amount }
                val tagsOverlap = counted.any { (tagMap[it.tx.id]?.size ?: 0) > 1 }

                val merchantBreakdown = groupTotals(counted) { listOf(it.merchantName) }
                    .map { (merchant, group) ->
                        MerchantData(
                            name = merchant,
                            amount = netOf(group.amountIn(displayCurrency), refunds.byMerchant[merchant]),
                            transactionCount = group.rows.size,
                            isSubscription = group.rows.any { it.tx.isRecurring }
                        )
                    }
                    .sortedByDescending { it.amount }
                    .take(10)

                // Group by account. Labels prefer the alias, as the account filter does.
                val accountLabels = accountOptions(balances).associate { it.key to it.label }
                val accountBreakdown = groupTotals(counted) { listOf(accountKeyOf(it)) }
                    .map { (accountKey, group) ->
                        // Net refunds credited back to this account (floored at zero) so
                        // the account total — and its % of the net grand total — stay sane.
                        val accountAmount = netOf(group.amountIn(displayCurrency), refunds.byAccount[accountKey])
                        AccountBreakdownData(
                            key = accountKey,
                            label = accountLabelFor(accountKey, group.rows.first().tx, accountLabels),
                            amount = accountAmount,
                            percentage = shareOf(accountAmount, totalSpending),
                            transactionCount = group.rows.size
                        )
                    }
                    .sortedByDescending { it.amount }

                // Group by day of week, all seven days in the locale's week order.
                // Refunds net off the weekday they landed on, as the trend nets per day.
                val locale = java.util.Locale.getDefault()
                val weekdayRefunds = refunds.byDay.entries
                    .groupBy({ it.key.dayOfWeek }, { it.value })
                    .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }
                val byWeekday = groupTotals(counted) { listOf(it.dateTime.dayOfWeek) }
                val dayOfWeekBreakdown = if (counted.isEmpty()) emptyList() else weekdayOrder(locale).map { day ->
                    val group = byWeekday[day]
                    val amount = netOf(group?.amountIn(displayCurrency) ?: BigDecimal.ZERO, weekdayRefunds[day])
                    TagData(
                        name = day.getDisplayName(java.time.format.TextStyle.FULL, locale),
                        amount = amount,
                        percentage = shareOf(amount, totalSpending),
                        transactionCount = group?.rows?.size ?: 0
                    )
                }

                // The periods before this one, most recent first: budget cycles for the
                // month periods, financial years for the FY. All time and custom ranges
                // have no natural predecessor, so they get no history.
                val historyKind = when {
                    filterState.period.followsBudgetCycle -> PeriodKind.CYCLE
                    filterState.period == TimePeriod.CURRENT_FY -> PeriodKind.FINANCIAL_YEAR
                    else -> null
                }
                val previousRanges = when (historyKind) {
                    PeriodKind.CYCLE -> {
                        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
                        generateSequence(BudgetCycle.previousCycle(dateRange, startDay)) {
                            BudgetCycle.previousCycle(it, startDay)
                        }.take(HISTORY_PERIODS - 1).toList()
                    }
                    PeriodKind.FINANCIAL_YEAR -> (1 until HISTORY_PERIODS).map { n ->
                        dateRange.first.minusYears(n.toLong()) to dateRange.first.minusYears(n - 1L).minusDays(1)
                    }
                    null -> emptyList()
                }
                // One query over every previous period, bucketed in memory below: the
                // history used to cost a query per period on each database emission.
                val historyRows = previousRanges.lastOrNull()?.let { oldest ->
                    val span = oldest.first to previousRanges.first().second
                    scopeTransactions(
                        if (isUnified) {
                            transactionRepository.getTransactionsWithSplitsFiltered(span.first, span.second).first()
                        } else {
                            transactionRepository.getTransactionsWithSplitsFiltered(span.first, span.second, displayCurrency).first()
                        }
                    )
                }.orEmpty()

                // This view's net total over a previous range (same filters, refunds
                // netted), from [historyRows] with the range query's own bounds; null
                // when the range has no rows at all.
                suspend fun spendIn(range: Pair<LocalDate, LocalDate>): BigDecimal? {
                    val from = range.first.atStartOfDay()
                    val to = range.second.atTime(23, 59, 59)
                    val rows = historyRows.filter {
                        val at = it.transaction.dateTime
                        !at.isBefore(from) && !at.isAfter(to)
                    }
                    return if (rows.isEmpty()) null else netSpend(rows, filterState.typeFilter, categoryFilterSet, parentOf, isUnified, displayCurrency)
                }
                val previousSpend = previousRanges.map { spendIn(it) }
                val periodHistory = historyKind?.let { kind ->
                    val today = LocalDate.now()
                    val inProgress = !today.isAfter(dateRange.second)
                    // A running period is compared with the same number of days of the
                    // one before, not its full total: day 10 against a whole month always
                    // reads as "less".
                    val change = previousRanges.firstOrNull()?.let { prev ->
                        val prevSpend = previousSpend.first() ?: return@let null
                        val base = if (inProgress) {
                            val elapsed = ChronoUnit.DAYS.between(dateRange.first, today)
                            spendIn(prev.first to minOf(prev.first.plusDays(elapsed), prev.second)) ?: BigDecimal.ZERO
                        } else prevSpend
                        PeriodChange.of(totalSpending, base, PeriodSpend(prev.first, prev.second, prevSpend, true), inProgress)
                    }
                    val past = previousRanges.zip(previousSpend) { range, spend ->
                        PeriodSpend(range.first, range.second, spend ?: BigDecimal.ZERO, spend != null)
                    }.reversed()
                    PeriodHistory(
                        kind = kind,
                        currency = displayCurrency,
                        periods = past + PeriodSpend(dateRange.first, dateRange.second, totalSpending, true),
                        change = change
                    )
                }

                // Month-end forecast: this cycle only, in a spend view. "Usual" is this
                // view's total (same filters, refunds netted) over the previous cycles.
                val forecast = if (filterState.period == TimePeriod.THIS_MONTH && filterState.typeFilter.netsRefunds) {
                    SpendForecast.of(totalSpending, displayCurrency, dateRange.first, dateRange.second, LocalDate.now(), previousSpend)
                } else null

                val trend = calculateTrend(counted, dateRange, filterState.period, displayCurrency, refunds.byDay)
                AnalyticsUiState(
                    totalSpending = totalSpending,
                    categoryBreakdown = categoryBreakdown,
                    topMerchants = merchantBreakdown,
                    transactionCount = counted.size,
                    currency = displayCurrency,
                    period = filterState.period,
                    dateRange = dateRange,
                    typeFilter = filterState.typeFilter,
                    categoryFilter = filterState.categoryFilter,
                    accountFilter = filterState.accountFilter,
                    isLoading = false,
                    spendingTrend = trend.line,
                    dailyTrend = trend.daily,
                    availableCategories = allCategoryNames,
                    refundNettedFromTotal = refundNettedFromTotal,
                    accountBreakdown = accountBreakdown,
                    tagBreakdown = tagBreakdown,
                    tagsOverlap = tagsOverlap,
                    dayOfWeekBreakdown = dayOfWeekBreakdown,
                    forecast = forecast,
                    periodHistory = periodHistory
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    /** A view's headline total over [rows] (already scoped), computed like totalSpending. */
    private suspend fun netSpend(
        rows: List<TransactionWithSplits>,
        filter: TransactionTypeFilter,
        categoryFilter: Set<String>?,
        parentOf: Map<String, String>,
        isUnified: Boolean,
        displayCurrency: String
    ): BigDecimal {
        val amountOf: suspend (TransactionEntity) -> BigDecimal? = { tx ->
            if (isUnified) currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, displayCurrency)
            else tx.amount
        }
        var total = BigDecimal.ZERO
        for (tw in analyticsRowsFor(rows, filter)) {
            if (categoryFilter != null &&
                tw.getAmountByCategory().keys.none { it.ifEmpty { "Others" } in categoryFilter }
            ) continue
            total += amountOf(tw.transaction) ?: continue // never-rated pair (#670)
        }
        return netOf(total, refundNetting(rows, filter, categoryFilter, parentOf, amountOf).total)
    }

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    /** Back to the default view, which then follows the card-spend setting again. */
    fun resetTransactionTypeFilter() = typeFilter.reset()

    fun setTransactionTypeFilter(filter: TransactionTypeFilter) = typeFilter.pick(filter)

    fun selectCurrency(currency: String) {
        nativeCurrency.value = currency
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
     * The "This Month" / "Last Month" range for the Analytics tab. Honours the
     * user's configured budget cycle start day (e.g. 25th → 24th) instead of
     * the calendar month, so the chart and stats line up with the cycle used
     * everywhere else in the app.
     *
     * Only ever called for a period where [TimePeriod.followsBudgetCycle] is
     * true, so the non-null assertion cannot fire.
     */
    private suspend fun getCycleRange(period: TimePeriod): Pair<LocalDate, LocalDate> {
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        return getCycleAwareDateRange(period, startDay)!!
    }
}

internal data class AnalyticsTrend(val line: List<BalancePoint>, val daily: List<BalancePoint>)

internal fun calculateTrend(
    rows: List<CountedRow>,
    dateRange: Pair<LocalDate, LocalDate>,
    period: TimePeriod,
    currency: String,
    refunds: Map<LocalDate, BigDecimal>,
    today: LocalDate = LocalDate.now()
): AnalyticsTrend {
    val (start, end) = dateRange
    val firstDate = if (period == TimePeriod.ALL) {
        rows.minOfOrNull { it.tx.dateTime.toLocalDate() }?.withDayOfMonth(1)?.coerceAtLeast(start) ?: start
    } else start
    val lastDate = minOf(end, today)
    val byDay = groupTotals(rows) { listOf(it.dateTime.toLocalDate()) }
    val dates = generateSequence(firstDate) { it.plusDays(1) }.takeWhile { it <= lastDate }
    // Keep unfloored daily net amounts until the chosen line buckets are formed.
    // Otherwise an over-refund on one day would disappear from its month or year.
    val netByDate = dates.associateWith { date ->
        (byDay[date]?.amountIn(currency) ?: BigDecimal.ZERO) - (refunds[date] ?: BigDecimal.ZERO)
    }
    fun point(date: LocalDate, amount: BigDecimal) =
        BalancePoint(date.atStartOfDay(), amount.coerceAtLeast(BigDecimal.ZERO), currency)

    val daily = netByDate.map { (date, amount) -> point(date, amount) }
    if (period != TimePeriod.CURRENT_FY && period != TimePeriod.ALL) return AnalyticsTrend(daily, daily)

    val byYear = period == TimePeriod.ALL && ChronoUnit.YEARS.between(firstDate, end) >= 2
    val line = netByDate.entries.groupBy { (date, _) ->
        if (byYear) date.withDayOfYear(1) else date.withDayOfMonth(1)
    }.map { (date, values) -> point(date, values.sumOf { it.value }) }
    return AnalyticsTrend(line, daily)
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
private data class GroupFilters(
    val categoryFilter: String?,
    val profileId: Long?,
    val accountFilter: String?
)

internal data class AnalyticsCurrencyScope(val currency: String, val unified: Boolean)

data class AnalyticsUiState(
    val totalSpending: BigDecimal = BigDecimal.ZERO,
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val topMerchants: List<MerchantData> = emptyList(),
    val transactionCount: Int = 0,
    val currency: String = "",
    val period: TimePeriod = TimePeriod.THIS_MONTH,
    val dateRange: Pair<LocalDate, LocalDate>? = null,
    val typeFilter: TransactionTypeFilter = TransactionTypeFilter.EXPENSE,
    val categoryFilter: String? = null,
    val accountFilter: String? = null,
    val isLoading: Boolean = true,
    val spendingTrend: List<BalancePoint> = emptyList(),
    val dailyTrend: List<BalancePoint> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    // Refund value netted off the total when the per-category floor makes the
    // "Spending by category" bars sum above the headline total (#704). Zero when
    // the bars reconcile; the UI shows an explainer only when this is positive.
    val refundNettedFromTotal: BigDecimal = BigDecimal.ZERO,
    val accountBreakdown: List<AccountBreakdownData> = emptyList(),
    val tagBreakdown: List<TagData> = emptyList(),
    // A counted row carries several tags, so the tag totals can add up to more
    // than the total; the tag view says so in a footnote (#422).
    val tagsOverlap: Boolean = false,
    // Mon–Sun in the locale's week order; reuses TagData's name/amount/share shape.
    val dayOfWeekBreakdown: List<TagData> = emptyList(),
    val forecast: SpendForecast? = null,
    // The selected period and the ones before it, for the Insights hero chart;
    // null for All time and custom ranges.
    val periodHistory: PeriodHistory? = null
)

/** Bars in the Insights hero chart: the selected period and the five before it. */
internal const val HISTORY_PERIODS = 6

enum class PeriodKind { CYCLE, FINANCIAL_YEAR }

/** One period's net total in the view's currency. */
data class PeriodSpend(
    val start: LocalDate,
    val end: LocalDate,
    val amount: BigDecimal,
    /** False when the period had no rows at all: drawn as an empty stub, not a figure. */
    val hasData: Boolean
)

/** The selected period against the one before it. */
data class PeriodChange(
    val previous: PeriodSpend,
    /** Whole-number size of the change; the direction is [isUp]. */
    val percent: Int,
    val isUp: Boolean,
    /** The selected period is still running, so it was compared with the same days of [previous]. */
    val toDate: Boolean
) {
    companion object {
        /** Null when there is nothing to compare with (no spend in the base). */
        fun of(current: BigDecimal, base: BigDecimal, previous: PeriodSpend, toDate: Boolean): PeriodChange? {
            if (base.signum() <= 0) return null
            val percent = (current - base).abs()
                .multiply(BigDecimal(100))
                .divide(base, 0, java.math.RoundingMode.HALF_UP)
                .toInt()
            return PeriodChange(previous, percent, current > base, toDate)
        }
    }
}

/** One currency's history: [periods] run oldest first and end with the selected period. */
data class PeriodHistory(
    val kind: PeriodKind,
    val currency: String,
    val periods: List<PeriodSpend>,
    val change: PeriodChange?
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
    val transactionCount: Int,
    // The "No tag" row: there's no Transactions filter for it, so it isn't tappable.
    val isUntagged: Boolean = false
)

data class MerchantData(
    val name: String,
    val amount: BigDecimal,
    val transactionCount: Int,
    val isSubscription: Boolean
)
