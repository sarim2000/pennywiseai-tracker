package com.pennywiseai.tracker.ui.screens.analytics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
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
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.presentation.common.getCycleAwareDateRange
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.forTotals
import com.pennywiseai.tracker.R
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

/**
 * The rows an Analytics type view totals: those of [filter]'s types (Spending =
 * expense + card spend, #898), cut to what counts toward totals — loan-linked and
 * analytics-excluded parts drop out (#451, #800); they stay in history and balance.
 *
 * The Income view also drops refunds (INCOME + DEDUCT_SPENT): they offset spending
 * and are netted out of the spend views instead, so counting them as income too
 * would double-count them. Mirrors the Home card (#629).
 */
internal fun analyticsRowsFor(
    rows: List<TransactionWithSplits>,
    filter: TransactionTypeFilter
): List<TransactionWithSplits> {
    val types = filter.transactionTypes
    return (if (types == null) rows else rows.filter { it.transaction.transactionType in types })
        .forTotals()
        .filter {
            !(filter == TransactionTypeFilter.INCOME &&
                it.transaction.budgetImpactType == BudgetImpactType.DEDUCT_SPENT)
        }
}

/** [gross] less [refund], floored at zero: a spend bucket never shows negative spend. */
internal fun netOf(gross: BigDecimal, refund: BigDecimal?): BigDecimal =
    (gross - (refund ?: BigDecimal.ZERO)).coerceAtLeast(BigDecimal.ZERO)

/** Refund value to net off each spend surface, keyed like that surface's rows. */
internal class RefundNetting(
    val total: BigDecimal = BigDecimal.ZERO,
    val byCategory: Map<String, BigDecimal> = emptyMap(),
    /** Keyed "bankName_accountNumber". */
    val byAccount: Map<String, BigDecimal> = emptyMap(),
    val byMerchant: Map<String, BigDecimal> = emptyMap(),
    val byDay: Map<LocalDate, BigDecimal> = emptyMap()
)

/**
 * The type view Analytics opens on. With "count credit card as expense" on, Home's
 * "Spent" is Expense + Card, so open on Spending to show the same figure. Off, open
 * on Expense: those users count the card bill payment instead, and adding card
 * spends would count the same money twice.
 */
internal fun defaultTypeFilter(countCreditCardAsExpense: Boolean): TransactionTypeFilter =
    if (countCreditCardAsExpense) TransactionTypeFilter.SPENDING else TransactionTypeFilter.EXPENSE

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

/**
 * Refunds (INCOME + DEDUCT_SPENT) to net out of a spend view ([TransactionTypeFilter.netsRefunds]:
 * Expense and Spending), so every spend surface (total, category, account, merchant
 * and the trend) agrees with the Home card and the Transactions page, which are net
 * of refunds. Other views get nothing to net.
 *
 * [rows] is the untyped, scoped list: the type filter dropped these refunds, so they're
 * pulled back in here. A refund comes off its budgetCategory (rolled up via [parentOf];
 * skipped when outside [categoryFilter]), its account, its merchant (when it carries the
 * same merchant name) and its day. The FULL refund always comes off the headline total,
 * while each bucket floors at zero ([netOf]); so an over-refund or orphaned refund can
 * leave the total below the sum of the visible rows, exactly as on the Home card (#704).
 *
 * [amountOf] gives the refund in the view's currency, or null to skip it (unified mode,
 * never-rated pair: never face-value a foreign amount, #670).
 */
internal suspend fun refundNetting(
    rows: List<TransactionWithSplits>,
    filter: TransactionTypeFilter,
    categoryFilter: Set<String>?,
    parentOf: Map<String, String>,
    amountOf: suspend (TransactionEntity) -> BigDecimal?
): RefundNetting {
    if (!filter.netsRefunds) return RefundNetting()
    var total = BigDecimal.ZERO
    val byCategory = mutableMapOf<String, BigDecimal>()
    val byAccount = mutableMapOf<String, BigDecimal>()
    val byMerchant = mutableMapOf<String, BigDecimal>()
    val byDay = mutableMapOf<LocalDate, BigDecimal>()
    fun <K> MutableMap<K, BigDecimal>.add(key: K, amount: BigDecimal) { this[key] = (this[key] ?: BigDecimal.ZERO) + amount }
    for (tw in rows) {
        val tx = tw.transaction.forTotals() ?: continue
        if (tx.transactionType != TransactionType.INCOME || tx.budgetImpactType != BudgetImpactType.DEDUCT_SPENT) continue
        val rawCategory = (tx.budgetCategory ?: "").ifEmpty { "Others" }
        if (categoryFilter != null && rawCategory !in categoryFilter) continue
        val amount = amountOf(tx) ?: continue
        if (amount <= BigDecimal.ZERO) continue
        total += amount
        byCategory.add(parentOf[rawCategory] ?: rawCategory, amount)
        byAccount.add("${tx.bankName}_${tx.accountNumber}", amount)
        byMerchant.add(tx.merchantName, amount)
        byDay.add(tx.dateTime.toLocalDate(), amount)
    }
    return RefundNetting(total, byCategory, byAccount, byMerchant, byDay)
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
        // Re-emit the period when the cycle start day changes so the data
        // reloads along with the chip label (#686).
        combine(_selectedPeriod, budgetCycleStartDay) { period, _ -> period },
        customDateRange,
        transactionTypeFilter,
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
                val displayCurrency = _selectedCurrency.value

                // Calculate total — convert if unified mode
                var totalSpending = BigDecimal.ZERO
                // In unified mode, count only rows that actually converted, so the
                // count and average stay consistent with totalSpending rather than
                // including rows whose amount was skipped (#670).
                var unifiedConvertedCount = 0
                if (isUnified) {
                    for (tx in filteredTransactions) {
                        // No known rate ever → leave the row out; face-valuing a
                        // foreign amount into displayCurrency corrupts the total (#670).
                        totalSpending += currencyConversionService
                            .convertAmountOrNull(tx.amount, tx.currency, displayCurrency) ?: continue
                        unifiedConvertedCount++
                    }
                } else {
                    totalSpending = filteredTransactions.map { it.amount.toDouble() }.sum().toBigDecimal()
                }
                val effectiveCount = if (isUnified) unifiedConvertedCount else filteredTransactions.size

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

                // Every counted row with its amount in the view's currency, converted
                // once for the tag, account and weekday groups (#422). Unified mode
                // leaves out a never-rated pair rather than face-value it (#670).
                val counted = filteredTransactions.mapNotNull { tx ->
                    if (isUnified) {
                        currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, displayCurrency)
                            ?.let { CountedRow(tx, Money(it, displayCurrency)) }
                    } else {
                        CountedRow(tx, Money(tx.amount, tx.currency))
                    }
                }

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

                // Group by merchant — convert if unified
                val merchantBreakdown = filteredTransactions
                    .groupBy { it.merchantName }
                    .entries
                    .map { (merchant, txns) ->
                        var convertedCount = 0
                        val gross = if (isUnified) {
                            var sum = BigDecimal.ZERO
                            for (tx in txns) {
                                sum += currencyConversionService
                                    .convertAmountOrNull(tx.amount, tx.currency, displayCurrency) ?: continue
                                convertedCount++
                            }
                            sum
                        } else {
                            convertedCount = txns.size
                            txns.map { it.amount.toDouble() }.sum().toBigDecimal()
                        }
                        // Net any refund that carries this merchant's name (floored at zero).
                        val merchantAmount = netOf(gross, refunds.byMerchant[merchant])
                        MerchantData(
                            name = merchant,
                            amount = merchantAmount,
                            // Count only converted rows so it matches the amount (#670).
                            transactionCount = convertedCount,
                            isSubscription = txns.any { it.isRecurring }
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

                // Calculate average amount — divide by the converted count so the
                // average isn't dragged down by rows excluded from totalSpending (#670).
                val averageAmount = if (effectiveCount > 0) {
                    totalSpending.divide(BigDecimal(effectiveCount), 2, java.math.RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }

                // Get top category info
                val topCategory = categoryBreakdown.firstOrNull()

                AnalyticsUiState(
                    totalSpending = totalSpending,
                    categoryBreakdown = categoryBreakdown,
                    topMerchants = merchantBreakdown,
                    transactionCount = effectiveCount,
                    averageAmount = averageAmount,
                    topCategory = topCategory?.name,
                    topCategoryPercentage = topCategory?.percentage ?: 0f,
                    currency = displayCurrency,
                    isLoading = false,
                    spendingTrend = calculateSpendingTrend(
                        filteredTransactions, dateRange.first, dateRange.second, isUnified, displayCurrency, refunds.byDay
                    ),
                    availableCategories = allCategoryNames,
                    refundNettedFromTotal = refundNettedFromTotal,
                    accountBreakdown = accountBreakdown,
                    tagBreakdown = tagBreakdown,
                    tagsOverlap = tagsOverlap,
                    dayOfWeekBreakdown = dayOfWeekBreakdown
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

    /** Back to the default view, which then follows the card-spend setting again. */
    fun resetTransactionTypeFilter() = typeFilter.reset()

    fun setTransactionTypeFilter(filter: TransactionTypeFilter) = typeFilter.pick(filter)

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
                // Absent from the map = never-rated pair; amountIn treats it as 0
                // so the row is excluded from the trend, matching the totals (#670).
                val converted = currencyConversionService
                    .convertAmountOrNull(tx.amount, tx.currency, displayCurrency) ?: continue
                byId[tx.id] = converted.toDouble()
            }
            byId
        } else {
            emptyMap()
        }
        val amountIn: (com.pennywiseai.tracker.data.database.entity.TransactionEntity) -> Double = { tx ->
            if (isUnified) convertedById[tx.id] ?: 0.0 else tx.amount.toDouble()
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
    val dayOfWeekBreakdown: List<TagData> = emptyList()
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

