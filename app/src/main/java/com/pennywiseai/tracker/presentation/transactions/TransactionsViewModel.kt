package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.getCycleAwareDateRange
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.presentation.common.CurrencyGroupedTotals
import com.pennywiseai.tracker.presentation.common.AccountOption
import com.pennywiseai.tracker.presentation.common.accountOptions
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByAccount
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.domain.usecase.DeleteTransactionUseCase
import com.pennywiseai.tracker.domain.usecase.RestoreTransactionUseCase
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.utils.dateQueryWindow
import com.pennywiseai.tracker.utils.SmsReportUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val merchantAliasRepository: MerchantAliasRepository,
    private val userPreferencesRepository: com.pennywiseai.tracker.data.preferences.UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: com.pennywiseai.tracker.data.repository.CardRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val profileRepository: ProfileRepository,
    private val transactionGroupRepository: TransactionGroupRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val restoreTransactionUseCase: RestoreTransactionUseCase,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // Keep home-screen widgets in sync with edits made here (delete/undo,
    // bulk category changes, transfer marking).
    private fun refreshWidgets() =
        com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(context)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // transactionId -> tag names, kept in sync for in-memory tag search.
    private val transactionTagsMap: StateFlow<Map<Long, List<String>>> =
        tagRepository.observeTransactionTagNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // raw merchant name -> user-set display alias (#583), so a merchant's alias
    // is searchable too, not just its raw name (#691).
    private val merchantAliases: StateFlow<Map<String, String>> =
        merchantAliasRepository.getAllAliases()
            .map { list -> list.associate { it.merchantName to it.alias } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    /** Drives the period chip's date label (#686). */
    val budgetCycleStartDay: StateFlow<Int> = userPreferencesRepository.budgetCycleStartDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)
    
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    // Multiple categories filter (for budget navigation)
    private val _categoriesFilter = MutableStateFlow<List<String>?>(null)
    val categoriesFilter: StateFlow<List<String>?> = _categoriesFilter.asStateFlow()

    // True only when the multi-category filter came from a budget drill-down —
    // the "totals may differ" banner is about budgets, not the user's own picks (#786).
    private val _categoriesFromBudget = MutableStateFlow(false)
    val categoriesFromBudget: StateFlow<Boolean> = _categoriesFromBudget.asStateFlow()

    private val _transactionTypeFilter = MutableStateFlow(TransactionTypeFilter.ALL)
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = _transactionTypeFilter.asStateFlow()

    private val _selectedProfileId = MutableStateFlow<Long?>(null)
    val selectedProfileId: StateFlow<Long?> = _selectedProfileId.asStateFlow()

    private val _ignoredAccountKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _profileAccountKeys = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val profileAccountKeys: StateFlow<Map<Long, Set<String>>> = _profileAccountKeys.asStateFlow()

    // Account filter — null means "All accounts". Key is "bankName_accountLast4".
    private val _accountFilter = MutableStateFlow<String?>(null)
    val accountFilter: StateFlow<String?> = _accountFilter.asStateFlow()

    // Tag filter — null means "All tags". Matches any transaction that carries the tag.
    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()

    val availableTags: StateFlow<List<String>> = tagRepository.observeAllTagNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pickable account options for the account filter dropdown (alias-preferred labels).
    val accountOptions: StateFlow<List<AccountOption>> =
        accountBalanceRepository.getAllLatestBalances()
            .map { accountOptions(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("INR") // Will be initialized from preferences
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    private val _isUnifiedMode = MutableStateFlow(false)
    val isUnifiedMode: StateFlow<Boolean> = _isUnifiedMode.asStateFlow()

    // Set by a review's drill-down, which lists one currency's own rows even in unified mode.
    private val pinnedCurrency = MutableStateFlow<String?>(null)

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
    val availableCurrencies: StateFlow<List<String>> = combine(selectedPeriod, customDateRange, budgetCycleStartDay) { period, customRange, _ ->
        period to customRange
    }.flatMapLatest { (period, customRange) ->
        if (period == TimePeriod.ALL) {
            transactionRepository.getAllCurrencies()
        } else if (period == TimePeriod.CUSTOM && customRange != null) {
            val (startDate, endDate) = customRange
            val startDateTime = startDate.atStartOfDay()
            val endDateTime = endDate.atTime(23, 59, 59)
            transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
        } else if (period.followsBudgetCycle) {
            val (startDate, endDate) = getCycleRange(period)
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
        .combine(userPreferencesRepository.baseCurrency) { currencies, base ->
            currencies.sortedWith { a, b ->
                when {
                    a == base -> -1
                    b == base -> 1
                    else -> a.compareTo(b)
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
        resolveTotals(groupedTotals, currency, isUnified)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilteredTotals()
    )

    private suspend fun resolveTotals(
        groupedTotals: CurrencyGroupedTotals,
        currency: String,
        isUnified: Boolean
    ): FilteredTotals = resolveFilteredTotals(groupedTotals, currency, isUnified) { amount, from, to ->
        currencyConversionService.convertAmountOrNull(amount, from, to)
    }

    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()

    // ─── Bulk-edit selection (#369) ──────────────────────────────────────────
    //
    // Selection mode is implicit in [selectedIds.isNotEmpty()] — no separate flag.
    // Long-press in the list enters this mode and tap-toggles add/remove rows.
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /**
     * Income / expense / credit totals of just the selected rows (#634),
     * resolved through [resolveTotals] so the selection card obeys exactly the
     * same currency rules as the list card — one currency at a time, or
     * converted in unified mode. Never a raw cross-currency sum.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectionTotals: StateFlow<FilteredTotals> = combine(
        _selectedIds,
        _uiState,
        _selectedCurrency,
        _isUnifiedMode
    ) { ids, state, currency, isUnified ->
        val selected = state.transactions.filter { it.id in ids }
        Triple(selected, currency, isUnified)
    }.mapLatest { (selected, currency, isUnified) ->
        resolveTotals(calculateCurrencyGroupedTotals(selected), currency, isUnified)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilteredTotals()
    )

    /** One-shot snackbar payload for a bulk action; cleared by the UI after showing. */
    data class BulkSnack(val message: UiText, val undo: (() -> Unit)? = null)
    private val _bulkSnack = MutableStateFlow<BulkSnack?>(null)
    val bulkSnack: StateFlow<BulkSnack?> = _bulkSnack.asStateFlow()
    fun consumeBulkSnack() { _bulkSnack.value = null }

    fun toggleSelection(id: Long) {
        _selectedIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    /** Select every row the current search/filters show, e.g. to bulk-delete all search hits. */
    fun selectAllVisible() { _selectedIds.value = _uiState.value.transactions.mapTo(HashSet()) { it.id } }

    init {
        // Prune selected ids that fall out of the visible list (filter change,
        // search, delete from elsewhere). Otherwise a bulk op would silently
        // skip rows the user thinks they had selected.
        viewModelScope.launch {
            _uiState
                .map { it.transactions.mapTo(HashSet()) { tx -> tx.id } }
                .distinctUntilChanged()
                .collect { visible ->
                    _selectedIds.update { it intersect visible }
                }
        }
    }

    /**
     * Apply [newCategory] to every currently-selected transaction. Captures the
     * (id → previous-category) pairs first so the snackbar's Undo can restore
     * them; if a row had its category changed elsewhere in between, the undo
     * will overwrite that change too — acceptable for an explicit user action.
     */
    fun bulkUpdateCategory(newCategory: String) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val previous = _uiState.value.transactions
                .filter { it.id in ids }
                .associate { it.id to it.category }
            previous.keys.forEach { id ->
                transactionRepository.updateCategory(id, newCategory)
            }
            refreshWidgets()
            clearSelection()
            _bulkSnack.value = BulkSnack(
                message = UiText.Plural(R.plurals.txn_list_bulk_category_updated, previous.size, listOf(previous.size, UiText.category(newCategory))),
                undo = {
                    viewModelScope.launch {
                        previous.forEach { (id, oldCategory) ->
                            transactionRepository.updateCategory(id, oldCategory)
                        }
                        refreshWidgets()
                    }
                }
            )
        }
    }

    // ─── Self-transfer suggestions (#385) ───────────────────────────────────
    //
    // Cheap heuristic on the *visible* (filtered) transactions: pair each
    // EXPENSE with an INCOME row of the same currency + amount that landed on
    // a different account within ±10 minutes. No silent merge — the UI just
    // surfaces a "↔ Mark as transfer" chip on the expense row, and the user
    // confirms with one tap. Confirming flips both rows' transactionType to
    // TRANSFER (which already excludes them from Net), with Undo restoring
    // the originals.
    val suggestedTransferPartnerOf: StateFlow<Map<Long, Long>> = _uiState
        .map { state -> computeSelfTransferPairs(state.transactions) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )


    /**
     * User-confirmed self-transfer: flip both rows to TRANSFER and populate
     * their `fromAccount` / `toAccount` so the detail screen can render the
     * "From → To" account flow (it falls back to `accountNumber` otherwise).
     * Undo restores the original `transactionType` + `fromAccount` /
     * `toAccount` for each row. A shared `transferPairId` makes each row one leg
     * of the transfer (see utils/TransferPairs.kt). Balances stay untouched: the
     * money moved when the two SMS arrived.
     */
    /**
     * Link the two currently-selected transactions as a transfer (#614) — the
     * manual counterpart to the auto-detected [suggestedTransferPartnerOf] chip,
     * for pairs the detector misses (transfer fees, gaps beyond the window, a
     * single-leg SMS the user pairs by hand). Requires exactly two selected rows,
     * one outgoing (expense) and one incoming (income); otherwise it surfaces a
     * hint. Reuses [markPairAsTransfer] so undo behaves identically.
     */
    fun bulkMarkAsTransfer() {
        val ids = _selectedIds.value.toList()
        if (ids.size != 2) {
            _bulkSnack.value = BulkSnack(UiText.Res(R.string.txn_list_bulk_transfer_need_two))
            return
        }
        val all = _uiState.value.transactions
        val a = all.firstOrNull { it.id == ids[0] } ?: return
        val b = all.firstOrNull { it.id == ids[1] } ?: return
        val hasExpense = listOf(a, b).any { it.transactionType == TransactionType.EXPENSE }
        val hasIncome = listOf(a, b).any { it.transactionType == TransactionType.INCOME }
        if (!(hasExpense && hasIncome)) {
            _bulkSnack.value = BulkSnack(UiText.Res(R.string.txn_list_bulk_transfer_need_pair))
            return
        }
        clearSelection()
        markPairAsTransfer(a.id, b.id)
    }

    fun markPairAsTransfer(aId: Long, bId: Long) {
        viewModelScope.launch {
            val all = _uiState.value.transactions
            val a = all.firstOrNull { it.id == aId } ?: return@launch
            val b = all.firstOrNull { it.id == bId } ?: return@launch

            // Source of the money = the EXPENSE row's account.
            // Destination = the INCOME row's account.
            // A split bill (#346) is money others owe you, not a transfer. Read fresh.
            if (listOf(aId, bId).any { transactionRepository.getTransactionById(it)?.lentSharesTotal != null }) {
                _bulkSnack.value = BulkSnack(UiText.Res(R.string.split_error_transfer))
                return@launch
            }

            val (expense, income) = if (a.transactionType == TransactionType.EXPENSE) a to b else b to a
            val fromAcct = expense.accountNumber
            val toAcct = income.accountNumber

            // Snapshot for Undo: type + the two account fields per row.
            data class Snapshot(val type: TransactionType, val fromAccount: String?, val toAccount: String?,
                val fromBankName: String?, val toBankName: String?, val transferPairId: String?)
            val originals = listOf(a, b).associate {
                it.id to Snapshot(it.transactionType, it.fromAccount, it.toAccount, it.fromBankName, it.toBankName, it.transferPairId)
            }

            val now = java.time.LocalDateTime.now()
            val pairId = java.util.UUID.randomUUID().toString()
            transactionRepository.updateTransaction(
                a.copy(
                    transactionType = TransactionType.TRANSFER,
                    fromAccount = fromAcct,
                    toAccount = toAcct,
                    fromBankName = expense.bankName,
                    toBankName = income.bankName,
                    transferPairId = pairId,
                    updatedAt = now
                )
            )
            transactionRepository.updateTransaction(
                b.copy(
                    transactionType = TransactionType.TRANSFER,
                    fromAccount = fromAcct,
                    toAccount = toAcct,
                    fromBankName = expense.bankName,
                    toBankName = income.bankName,
                    transferPairId = pairId,
                    updatedAt = now
                )
            )
            refreshWidgets()
            _bulkSnack.value = BulkSnack(
                message = UiText.Res(R.string.txn_list_bulk_marked_transfer),
                undo = {
                    viewModelScope.launch {
                        val current = _uiState.value.transactions
                        val nowUndo = java.time.LocalDateTime.now()
                        originals.forEach { (id, snapshot) ->
                            current.firstOrNull { it.id == id }?.let { row ->
                                transactionRepository.updateTransaction(
                                    row.copy(
                                        transactionType = snapshot.type,
                                        fromAccount = snapshot.fromAccount,
                                        toAccount = snapshot.toAccount,
                                        fromBankName = snapshot.fromBankName,
                                        toBankName = snapshot.toBankName,
                                        transferPairId = snapshot.transferPairId,
                                        updatedAt = nowUndo
                                    )
                                )
                            }
                        }
                        refreshWidgets()
                    }
                }
            )
        }
    }

    /**
     * Soft-delete every currently-selected transaction. Undo restores them all
     * via the same per-row undoDelete path the single-row flow uses.
     */
    fun bulkDelete() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val snapshot = _uiState.value.transactions.filter { it.id in ids }
            deleteTransactionUseCase(snapshot)
            refreshWidgets()
            clearSelection()
            _bulkSnack.value = BulkSnack(
                message = UiText.Plural(R.plurals.txn_list_bulk_deleted, snapshot.size),
                undo = {
                    viewModelScope.launch {
                        // The snapshot predates the delete, and restore skips rows it
                        // sees as not deleted; it re-reads each row before restoring.
                        restoreTransactionUseCase(snapshot.map { it.copy(isDeleted = true) })
                        refreshWidgets()
                    }
                }
            )
        }
    }

    // ─── Bulk add-to-group (#506) ────────────────────────────────────────────
    //
    // All existing groups, for the bulk "Add to group" picker. A transaction
    // belongs to at most one group, so adding to a new group moves it off any
    // group it was already in — the undo below restores the prior membership.
    val groups: StateFlow<List<TransactionGroupEntity>> =
        transactionGroupRepository.getAllGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Add every currently-selected transaction to the group [groupId]. Captures
     * each row's previous groupId first so Undo can restore the original
     * membership (re-linking to the old group, or unlinking if it had none).
     */
    fun bulkAddToGroup(groupId: Long, groupName: String) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val previous = _uiState.value.transactions
                .filter { it.id in ids }
                .associate { it.id to it.groupId }
            previous.keys.forEach { id ->
                transactionGroupRepository.addTransactionToGroup(id, groupId)
            }
            clearSelection()
            _bulkSnack.value = BulkSnack(
                message = UiText.Plural(R.plurals.txn_list_bulk_added_to_group, previous.size, listOf(previous.size, groupName)),
                undo = { restoreGroupMembership(previous) }
            )
        }
    }

    /**
     * Create a new group named [name] and add every currently-selected
     * transaction to it. Undo restores the prior membership and deletes the
     * now-empty group that was just created.
     */
    fun bulkCreateGroupAndAdd(name: String, note: String? = null) {
        val ids = _selectedIds.value
        if (ids.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            val previous = _uiState.value.transactions
                .filter { it.id in ids }
                .associate { it.id to it.groupId }
            val groupId = transactionGroupRepository.createGroup(name, note)
            previous.keys.forEach { id ->
                transactionGroupRepository.addTransactionToGroup(id, groupId)
            }
            clearSelection()
            _bulkSnack.value = BulkSnack(
                message = UiText.Plural(R.plurals.txn_list_bulk_added_to_group, previous.size, listOf(previous.size, name.trim())),
                undo = {
                    viewModelScope.launch {
                        restoreGroupMembership(previous).join()
                        transactionGroupRepository.deleteGroup(groupId)
                    }
                }
            )
        }
    }

    private fun restoreGroupMembership(previous: Map<Long, Long?>) =
        viewModelScope.launch {
            previous.forEach { (id, oldGroupId) ->
                if (oldGroupId != null) {
                    transactionGroupRepository.addTransactionToGroup(id, oldGroupId)
                } else {
                    transactionGroupRepository.removeTransactionFromGroup(id)
                }
            }
        }

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
    
    // Available categories for the current period (before category filter is applied)
    // Used for the category filter chips row
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()

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

    private val smsScanUseCustomDate: StateFlow<Boolean> = userPreferencesRepository.smsScanUseCustomDate
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val smsScanCustomDate: StateFlow<Long?> = userPreferencesRepository.smsScanCustomDate
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun isShowingLimitedData(): Boolean {
        if (smsScanAllTime.value) return false

        val currentPeriod = _selectedPeriod.value
        val scanStartDate = getSmsScanStartDate() ?: return false

        return when (currentPeriod) {
            TimePeriod.ALL -> true
            TimePeriod.CURRENT_FY -> {
                val dateRange = getDateRangeForPeriod(TimePeriod.CURRENT_FY)
                if (dateRange != null) {
                    val (fyStart, _) = dateRange
                    fyStart.isBefore(scanStartDate)
                } else {
                    false
                }
            }
            TimePeriod.CUSTOM -> {
                val customRange = customDateRange.value
                if (customRange != null) {
                    val (startDate, _) = customRange
                    startDate.isBefore(scanStartDate)
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun getSmsScanStartDate(): LocalDate? {
        if (smsScanUseCustomDate.value) {
            return smsScanCustomDate.value?.let { millis ->
                java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }
        }

        return LocalDate.now().minusMonths(smsScanMonths.value.toLong())
    }
    
    init {
        // Observe selected profile from preferences and cache profile account keys
        viewModelScope.launch {
            userPreferencesRepository.selectedProfileId.collect { profileId ->
                _selectedProfileId.value = profileId
            }
        }
        viewModelScope.launch {
            accountBalanceRepository.getAllLatestBalances().collect { balances ->
                _profileAccountKeys.value = buildProfileAccountKeys(balances)
            }
        }
        viewModelScope.launch {
            // Expand the set once and the plain match below covers both (#826).
            combine(
                ignoredAccountsStore.keysFlow,
                cardRepository.getAllCards(),
                accountBalanceRepository.getAllLatestBalances(),
                IgnoredAccountsStore.Companion::withLinkedCards,
            ).collect { _ignoredAccountKeys.value = it }
        }
        viewModelScope.launch {
            availableTags.collect { tags ->
                val current = _tagFilter.value
                if (current != null && tags.none { it.equals(current, ignoreCase = true) }) {
                    _tagFilter.value = null
                }
            }
        }

        // Load unified mode preferences
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                pinnedCurrency
            ) { unifiedMode, displayCurrency, pinned ->
                Triple(unifiedMode, displayCurrency, pinned)
            }.collect { (unifiedMode, displayCurrency, pinned) ->
                _isUnifiedMode.value = unifiedMode && pinned == null
                _selectedCurrency.value = when {
                    pinned != null -> pinned
                    unifiedMode -> displayCurrency
                    else -> baseCurrency
                }
            }
        }

        // A converted amount only means anything in the currency it was built for. Rows
        // format it with the live selection, so drop the old map on a switch: until the
        // new one lands, a row with no entry shows its own amount and currency.
        _selectedCurrency.drop(1).onEach { _convertedAmounts.value = emptyMap() }.launchIn(viewModelScope)

        // Compute available categories from transactions filtered by period only (no category filter)
        // This drives the category chips row in the UI
        merge(
            selectedPeriod.map { "period" },
            budgetCycleStartDay.map { "cycle" },
            categoriesFilter.map { "categories" },
            customDateRange.map { "customDate" }
        )
            .transformLatest { _ ->
                val period = selectedPeriod.value
                // A budget drill-down scopes the options to its categories; the user's
                // own ticks must not — an unticked category has to stay tickable (#786).
                val categories = categoriesFilter.value.takeIf { _categoriesFromBudget.value }
                // Always resolve a cycle range up-front; only the cycle-following
                // periods consume it, but keeping a real Pair avoids nullable
                // plumbing in the (non-suspend) filter helper.
                val cycleRange = if (period.followsBudgetCycle) {
                    getCycleRange(period)
                } else {
                    // Use a sentinel range — never read for other periods.
                    LocalDate.now() to LocalDate.now()
                }
                // Get all transactions without category filter applied
                getFilteredTransactions("", period, null, categories, TransactionTypeFilter.ALL, cycleRange)
                    .flatMapLatest { transactions ->
                        // Include split categories, not just each row's parent: a
                        // category that exists only inside a split is still one the
                        // user can filter on, and the auto-clear below must not
                        // discard it (#749). Observed, so a split edit refreshes the chips.
                        transactionRepository.observeSplitsForActiveTransactions()
                            .map { allSplits ->
                                val parentOf = this@TransactionsViewModel.categories.value.values.toList().parentNameOf()
                                filterableCategories(transactions, allSplits, parentOf)
                            }
                    }
                    .flowOn(Dispatchers.Default)
                    .collect { emit(it) }
            }
            .onEach { categories ->
                _availableCategories.value = categories
                // Auto-clear category filter if the selected category no longer exists in available categories.
                // Not on a pinned list: a review's category that fell to zero is empty, not every other category.
                val currentFilter = _categoryFilter.value
                if (currentFilter != null && currentFilter !in categories && pinnedCurrency.value == null) {
                    _categoryFilter.value = null
                }
            }
            .launchIn(viewModelScope)

        // Manually combine all flows using transformLatest
        merge(
            searchQuery.debounce(300).map { "search" },
            selectedPeriod.map { "period" },
            budgetCycleStartDay.map { "cycle" },
            categoryFilter.map { "category" },
            categoriesFilter.map { "categories" },
            transactionTypeFilter.map { "typeFilter" },
            _selectedProfileId.map { "profileFilter" },
            _profileAccountKeys.map { "profileAccountKeys" },
            // Re-filter the moment an account is ignored, rather than at the
            // next app start (#826).
            _ignoredAccountKeys.map { "ignoredAccounts" },
            _accountFilter.map { "accountFilter" },
            tagFilter.map { "tagFilter" },
            selectedCurrency.map { "currency" },
            _isUnifiedMode.map { "unifiedMode" },
            sortOption.map { "sort" },
            customDateRange.map { "customDate" },
            transactionTagsMap.map { "tags" },
            categories.map { "categoryIndex" },
            merchantAliases.map { "aliases" }
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

                val profileId = _selectedProfileId.value
                val accountKey = _accountFilter.value
                val tag = _tagFilter.value

                // Resolve the cycle window up-front so the inner (non-suspend)
                // filter helper can reuse it for the cycle-following periods.
                // Other periods never read this — pass a sentinel pair to keep
                // the helper signature non-nullable.
                val cycleRange = if (period.followsBudgetCycle) {
                    getCycleRange(period)
                } else {
                    LocalDate.now() to LocalDate.now()
                }

                // Get filtered transactions (without currency filter first).
                // The list is computed off the main thread and published whole below;
                // transformLatest cancels a stale computation before starting the next.
                getFilteredTransactions(query, period, category, categories, typeFilter, cycleRange)
                    .map { allTransactions ->
                        // Apply profile, account, then tag filters
                        val tracked = filterTrackedByProfile(
                            allTransactions, profileId, _ignoredAccountKeys.value, _profileAccountKeys.value
                        )
                        val transactions = filterByTag(
                            filterTransactionsByAccount(tracked, accountKey),
                            tag,
                            transactionTagsMap.value
                        )
                        val sorted = if (isUnified) {
                            // Show all transactions regardless of currency
                            sortTransactions(transactions, sort)
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
                                    ) && !isPinned(currentCurrency)
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
                                    // Off main now: never overwrite a choice made since this was read.
                                    _selectedCurrency.compareAndSet(currentCurrency, newCurrency)
                                    newCurrency
                                } else {
                                    currentCurrency
                                }

                            // Now filter by the selected currency (which may have just been auto-selected)
                            val currencyFilteredTransactions = transactions.filter {
                                it.currency.equals(finalCurrency, ignoreCase = true)
                            }

                            sortTransactions(currencyFilteredTransactions, sort)
                        }
                        ComputedList(
                            transactions = sorted,
                            grouped = groupTransactionsByDate(sorted),
                            // Calculate totals for filtered transactions
                            totals = calculateCurrencyGroupedTotals(sorted)
                        )
                    }
                    .flowOn(Dispatchers.Default)
                    .collect { emit(it) }
            }
            .onEach { computed ->
                _uiState.value = _uiState.value.copy(
                    transactions = computed.transactions,
                    groupedTransactions = computed.grouped,
                    isLoading = false
                )
                _currencyGroupedTotals.value = computed.totals

                // Auto-select primary currency if not already selected or if current currency no longer exists
                if (!_isUnifiedMode.value) {
                    val currentCurrency = selectedCurrency.value
                    if (!computed.totals.availableCurrencies.contains(currentCurrency) && computed.totals.hasAnyCurrency() && !isPinned(currentCurrency)) {
                        val baseCurrency = userPreferencesRepository.baseCurrency.first()
                        _selectedCurrency.value = computed.totals.getPrimaryCurrency(baseCurrency)
                    }
                    _convertedAmounts.value = emptyMap()
                } else {
                    // After publishing: conversion can wait on the network for rates, and the
                    // rows and grouped totals shouldn't. The map only means anything in the
                    // currency it was built for, so a switch meanwhile discards it.
                    val displayCurrency = _selectedCurrency.value
                    val converted = withContext(Dispatchers.Default) {
                        convertedAmountsFor(computed.transactions, displayCurrency)
                    }
                    if (_selectedCurrency.value == displayCurrency) {
                        _convertedAmounts.value = converted
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategory(transaction: TransactionEntity, newCategory: String) {
        if (transaction.category == newCategory) return
        viewModelScope.launch {
            transactionRepository.updateCategory(transaction.id, newCategory)
        }
    }

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }
    
    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }
    
    fun clearCategoryFilter() {
        _categoryFilter.value = null
    }
    
    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        _transactionTypeFilter.value = filter
    }

    /** Sets the account filter; null clears it ("All accounts"). */
    fun setAccountFilter(accountKey: String?) {
        _accountFilter.value = accountKey
    }

    /** Sets the tag filter; null clears it ("All tags"). */
    fun setTagFilter(tag: String?) {
        _tagFilter.value = tag?.takeIf { it.isNotBlank() }
    }

    fun clearTagFilter() {
        _tagFilter.value = null
    }
    
    fun setSelectedProfile(profileId: Long?) {
        _selectedProfileId.value = profileId
        viewModelScope.launch {
            userPreferencesRepository.updateSelectedProfileId(profileId)
        }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    /**
     * Lists only rows written in [currency], with no conversion, even in unified mode.
     * The screen calls this on every return to it, so only the first call counts: a later
     * one must not undo a currency the user has since chosen. The guard lives here rather
     * than in saved UI state because a ViewModel rebuilt after process death needs the pin back.
     */
    fun pinCurrency(currency: String) {
        if (!pinnedCurrency.compareAndSet(null, currency)) return
        _isUnifiedMode.value = false
        _selectedCurrency.value = currency
    }

    /**
     * A pinned list stays on its currency even with no rows there, so it never shows another
     * currency under the card it opened from. A currency the user picked later can still fall back.
     */
    private fun isPinned(currency: String) = currency == pinnedCurrency.value

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
     * The "This Month" / "Last Month" range for the Transactions tab. Honours
     * the user's configured budget cycle start day (e.g. 25th → 24th) so the
     * transactions list, totals, and analytics agree on the same window — and
     * so the two chips stay tiled with no unreachable days between them.
     *
     * Only ever called for a period where [TimePeriod.followsBudgetCycle] is
     * true, so the non-null assertion cannot fire.
     */
    private suspend fun getCycleRange(period: TimePeriod): Pair<LocalDate, LocalDate> {
        val startDay = userPreferencesRepository.getBudgetCycleStartDay()
        return getCycleAwareDateRange(period, startDay)!!
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            _deletedTransaction.value = transaction
            deleteTransactionUseCase(transaction)
            refreshWidgets()
        }
    }

    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            viewModelScope.launch {
                restoreTransactionUseCase(transaction)
                _deletedTransaction.value = null
                refreshWidgets()
            }
        }
    }

    fun undoDeleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            restoreTransactionUseCase(transaction)
            refreshWidgets()
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
        setAccountFilter(null)
        clearTagFilter()
        _selectedProfileId.value = null  // reset local state only; does not update the shared DataStore preference
        setSortOption(SortOption.DATE_NEWEST)
        if (pinnedCurrency.value != null) {
            // The preference collector restores the normal view, unified or not.
            pinnedCurrency.value = null
        } else if (!_isUnifiedMode.value) {
            viewModelScope.launch {
                _selectedCurrency.value = userPreferencesRepository.baseCurrency.first()
            }
        }
    }
    
    /** A navigated type name, or All when absent, unknown or not listable (Spending). */
    private fun navigatedType(type: String?): TransactionTypeFilter =
        TransactionTypeFilter.entries.firstOrNull { it.name == type && it.inTransactionsList }
            ?: TransactionTypeFilter.ALL

    private fun decodeUrlParam(value: String): String {
        return if (value.contains("+") || value.contains("%")) {
            java.net.URLDecoder.decode(value, "UTF-8")
        } else value
    }

    fun applyInitialFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        startDateEpochDay: Long? = null,
        endDateEpochDay: Long? = null,
        account: String? = null,
        tag: String? = null,
        type: String? = null
    ) {
        if (!hasAppliedInitialFilters) {
            // Only apply filters once, when first navigating to the screen
            clearCategoryFilter()
            updateSearchQuery("")
            selectPeriod(TimePeriod.THIS_MONTH)
            setTransactionTypeFilter(navigatedType(type))
            setSortOption(SortOption.DATE_NEWEST)
            setAccountFilter(account)
            setTagFilter(tag)

            category?.let {
                val decoded = decodeUrlParam(it)
                setCategoryFilter(decoded)
            }

            merchant?.let {
                val decoded = decodeUrlParam(it)
                updateSearchQuery(decoded)
            }

            period?.let { periodName ->
                applyNavigatedPeriod(periodName, startDateEpochDay, endDateEpochDay)
            }

            // Only set currency if it's provided (from navigation)
            currency?.let { selectCurrency(it) }

            hasAppliedInitialFilters = true
        }
    }

    /**
     * Maps a navigated period name onto the period filter. A "CUSTOM" period
     * carries its date range as epoch days (analytics custom range drill-down);
     * without both dates it is ignored and the current period is kept.
     */
    private fun applyNavigatedPeriod(
        periodName: String,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?
    ) {
        when (periodName) {
            "THIS_MONTH" -> selectPeriod(TimePeriod.THIS_MONTH)
            "LAST_MONTH" -> selectPeriod(TimePeriod.LAST_MONTH)
            "CURRENT_FY" -> selectPeriod(TimePeriod.CURRENT_FY)
            "ALL" -> selectPeriod(TimePeriod.ALL)
            "CUSTOM" -> if (startDateEpochDay != null && endDateEpochDay != null) {
                setCustomDateRange(
                    LocalDate.ofEpochDay(startDateEpochDay),
                    LocalDate.ofEpochDay(endDateEpochDay)
                )
            }
        }
    }

    fun applyNavigationFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        startDateEpochDay: Long? = null,
        endDateEpochDay: Long? = null,
        account: String? = null,
        tag: String? = null,
        type: String? = null
    ) {
        // Create current params to compare
        val currentParams = NavigationParams(
            category, merchant, period, currency, startDateEpochDay, endDateEpochDay, account, tag, type
        )

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
        setTransactionTypeFilter(navigatedType(type))
        setSortOption(SortOption.DATE_NEWEST)
        setAccountFilter(account)
        setTagFilter(tag)

        category?.let {
            val decoded = decodeUrlParam(it)
            setCategoryFilter(decoded)
        }

        merchant?.let {
            val decoded = decodeUrlParam(it)
            updateSearchQuery(decoded)
        }

        period?.let { periodName ->
            applyNavigatedPeriod(periodName, startDateEpochDay, endDateEpochDay)
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
                decodeUrlParam(cat)
            }.filter { it.isNotBlank() }

            if (categoryList.isNotEmpty()) {
                _categoriesFilter.value = categoryList
                _categoriesFromBudget.value = true
            }
        }
    }

    /**
     * Clears the multiple categories filter.
     */
    fun clearCategoriesFilter() {
        _categoriesFilter.value = null
        _categoriesFromBudget.value = false
    }

    /**
     * Include/exclude one category in the list filter (#786). The effective
     * selection starts as "everything available"; unticking narrows it, and
     * ticking the last one back returns to no filter. An empty selection is
     * never stored — it would show nothing and read as a broken list.
     */
    fun toggleCategory(category: String, available: List<String>) {
        val current = _categoriesFilter.value
            ?: _categoryFilter.value?.let { listOf(it) }
            ?: available
        val next = if (category in current) current - category else current + category
        _categoryFilter.value = null
        _categoriesFromBudget.value = false
        _categoriesFilter.value = when {
            next.isEmpty() -> null
            next.toSet() == available.toSet() -> null
            else -> next.filter { it in available }
        }
    }

    /** Converted amounts for transactions in foreign currencies (unified mode). */
    private suspend fun convertedAmountsFor(
        transactions: List<TransactionEntity>,
        displayCurrency: String
    ): Map<Long, BigDecimal> {
        val converted = mutableMapOf<Long, BigDecimal>()
        for (tx in transactions) {
            if (!tx.currency.equals(displayCurrency, ignoreCase = true)) {
                // Omit unconvertible rows from the map; the row then shows
                // its native amount + currency instead of a mislabeled
                // face-value figure in the display currency (#670).
                currencyConversionService.convertAmountOrNull(
                    tx.amount, tx.currency, displayCurrency
                )?.let { converted[tx.id] = it }
            }
        }
        return converted
    }

    private fun getFilteredTransactions(
        searchQuery: String,
        period: TimePeriod,
        category: String?,
        categories: List<String>?,
        typeFilter: TransactionTypeFilter,
        // Resolved at the call site (which is in a coroutine) so the inner
        // (non-suspend) Flow builder can branch on THIS_MONTH. For other
        // periods this is ignored.
        cycleRange: Pair<LocalDate, LocalDate>
    ): Flow<List<TransactionEntity>> {
        // Start with the base flow based on category filter
        // A single category (the Analytics tap-through) is just the one-element case
        // of the budget filter below, and has to share its split handling: a
        // transaction split into Groceries + Dining keeps its parent category, so
        // matching on `category` alone found nothing for either — and the
        // available-categories auto-clear then dropped the filter and showed
        // everything (#749).
        // A parent category includes its sub-categories (#374).
        val effectiveCategories = (categories?.takeIf { it.isNotEmpty() } ?: category?.let { listOf(it) })
            ?.let { this.categories.value.values.toList().expandWithChildren(it) }
        val range = periodRange(period, cycleRange)
        val baseFlow = if (range == null) {
            transactionRepository.getAllTransactions()
        } else {
            val (rangeStart, rangeEnd) = range
            val (queryStart, queryEnd) = dateQueryWindow(rangeStart, rangeEnd)
            transactionRepository.getTransactionsBetweenDates(queryStart, queryEnd).map { transactions ->
                // The exact filter: the query window is deliberately wider, see dateQueryWindow.
                transactions.filter { it.dateTime in rangeStart..rangeEnd }
            }
        }

        // Apply the category filter. A transaction is included if its main category OR
        // any of its split categories matches.
        val categoriesFilteredFlow = if (effectiveCategories != null) {
            baseFlow.flatMapLatest { transactions ->
                // Observe the splits rather than snapshot them, so editing a split while
                // this list is open re-evaluates the filter instead of leaving it stale.
                transactionRepository.observeSplitsForActiveTransactions()
                    .map { allSplits -> filterByCategories(transactions, effectiveCategories, allSplits) }
            }
        } else {
            baseFlow
        }
        
        // Apply transaction type filter
        val typeFilteredFlow = categoriesFilteredFlow.map { filterByType(it, typeFilter) }

        // Apply search filter. Tags and aliases are read per list, so the re-run a change
        // to either one triggers sees it.
        return typeFilteredFlow.map {
            filterBySearch(it, searchQuery, transactionTagsMap.value, merchantAliases.value)
        }
    }
    
    /** The inclusive date-time range [period] covers, or null when it covers all history. */
    private fun periodRange(
        period: TimePeriod,
        cycleRange: Pair<LocalDate, LocalDate>
    ): Pair<LocalDateTime, LocalDateTime>? {
        val dates = when (period) {
            TimePeriod.ALL -> null
            TimePeriod.CUSTOM -> customDateRange.value ?: run {
                // Guard against invalid state: CUSTOM period must have a date range
                // This should never happen due to clearCustomDateRange() logic, but be defensive
                android.util.Log.e("TransactionsViewModel",
                    "CUSTOM period selected but no date range set - falling back to THIS_MONTH")
                // Auto-correct the invalid state
                _selectedPeriod.value = TimePeriod.THIS_MONTH
                cycleRange
            }
            TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH -> cycleRange
            else -> getDateRangeForPeriod(period)
        } ?: return null
        return dates.first.atStartOfDay() to dates.second.atTime(23, 59, 59)
    }

    fun getReportUrl(transaction: TransactionEntity): String {
        return SmsReportUrlBuilder.buildUrl(context, transaction.smsBody, transaction.smsSender)
    }
    
}

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val groupedTransactions: Map<DateGroup, List<TransactionEntity>> = emptyMap(),
    val isLoading: Boolean = true
)

/** One finished computation of the list, published to the UI state in one go. */
private class ComputedList(
    val transactions: List<TransactionEntity>,
    val grouped: Map<DateGroup, List<TransactionEntity>>,
    val totals: CurrencyGroupedTotals
)

enum class DateGroup(@StringRes val labelRes: Int) {
    TODAY(R.string.txn_list_group_today),
    YESTERDAY(R.string.txn_list_group_yesterday),
    THIS_WEEK(R.string.txn_list_group_this_week),
    EARLIER(R.string.txn_list_group_earlier)
}

enum class SortOption(@StringRes val labelRes: Int) {
    DATE_NEWEST(R.string.txn_list_sort_newest),
    DATE_OLDEST(R.string.txn_list_sort_oldest),
    AMOUNT_HIGHEST(R.string.txn_list_sort_amount_highest),
    AMOUNT_LOWEST(R.string.txn_list_sort_amount_lowest),
    MERCHANT_AZ(R.string.txn_list_sort_merchant_az),
    MERCHANT_ZA(R.string.txn_list_sort_merchant_za)
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
    val currency: String?,
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null,
    val account: String? = null,
    val tag: String? = null,
    val type: String? = null
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

/**
 * Self-transfer suggestion heuristic (#385): pair each EXPENSE with an INCOME row
 * of the same currency + amount that landed on a *different* account within the
 * match window, so the UI can surface a "↔ Mark as transfer" chip (the user
 * confirms — no silent merge). Pure so it can be unit-tested directly.
 *
 * Account identity is null when unknown. A transfer moves between two accounts,
 * so a pair is rejected only when BOTH legs are on the same *known* account;
 * account-less manual rows (no accountNumber) are still allowed to pair — without
 * this they'd all share one key and never pair (#611).
 */
internal fun computeSelfTransferPairs(
    txns: List<TransactionEntity>,
    matchWindowMinutes: Long = 60L
): Map<Long, Long> {
    if (txns.size < 2) return emptyMap()
    val acctOf: (TransactionEntity) -> String? = { tx ->
        tx.accountNumber?.takeIf { it.isNotBlank() }
            ?.let { "${tx.bankName.orEmpty()}|$it" }
    }

    // Bucket INCOME rows by (currency, amount) for O(1) lookup per EXPENSE.
    val incomesByKey = HashMap<Pair<String, BigDecimal>, MutableList<TransactionEntity>>()
    for (tx in txns) {
        if (tx.transactionType == TransactionType.INCOME) {
            incomesByKey.getOrPut(tx.currency to tx.amount) { mutableListOf() }.add(tx)
        }
    }
    if (incomesByKey.isEmpty()) return emptyMap()

    val pair = HashMap<Long, Long>(txns.size / 8)
    val claimedIncome = HashSet<Long>()
    for (expense in txns) {
        if (expense.transactionType != TransactionType.EXPENSE) continue
        val candidates = incomesByKey[expense.currency to expense.amount] ?: continue
        // Closest in time within the window, not on the same known account, not
        // already claimed by another expense.
        val expenseAcct = acctOf(expense)
        val match = candidates
            .asSequence()
            .filter { it.id !in claimedIncome }
            .filter { income ->
                val incomeAcct = acctOf(income)
                incomeAcct == null || expenseAcct == null || incomeAcct != expenseAcct
            }
            .filter {
                java.time.Duration.between(expense.dateTime, it.dateTime)
                    .toMinutes().let { d -> kotlin.math.abs(d) <= matchWindowMinutes }
            }
            .minByOrNull {
                kotlin.math.abs(
                    java.time.Duration.between(expense.dateTime, it.dateTime).toMinutes()
                )
            }
            ?: continue
        pair[expense.id] = match.id
        pair[match.id] = expense.id
        claimedIncome.add(match.id)
    }
    return pair
}
