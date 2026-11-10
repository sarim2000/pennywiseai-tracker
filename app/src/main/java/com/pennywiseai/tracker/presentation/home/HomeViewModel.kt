package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.preferences.HomeSection
import com.pennywiseai.tracker.data.preferences.HomeSectionLayout
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
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.manager.InAppUpdateManager
import com.pennywiseai.tracker.data.manager.InAppReviewManager
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewPrompt
import com.pennywiseai.tracker.domain.review.reviewPromptFor
import com.pennywiseai.tracker.domain.spending.Bill
import com.pennywiseai.tracker.domain.spending.SafeToSpend
import com.pennywiseai.tracker.domain.spending.SpendForecast
import com.pennywiseai.tracker.domain.spending.SubscriptionCost
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterAccountsByProfile
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.database.entity.nameForBuiltin
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.canReadSms
import com.pennywiseai.tracker.utils.forTotals
import com.pennywiseai.tracker.utils.sumByCurrency
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import com.pennywiseai.tracker.domain.usecase.DeleteTransactionUseCase
import com.pennywiseai.tracker.domain.usecase.RestoreTransactionUseCase
import javax.inject.Inject

/**
 * Accounts that make up Home's spendable "bank" total: not credit cards or loan
 * accounts (their balance is owed, #792), and not zero-balance accounts.
 */
internal fun spendableAccounts(balances: List<AccountBalanceEntity>): List<AccountBalanceEntity> =
    balances.filter { !it.isLiability && it.balance != BigDecimal.ZERO }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val transactionGroupRepository: TransactionGroupRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val loanRepository: LoanRepository,
    private val periodReviewLoader: PeriodReviewLoader,
    goalRepository: com.pennywiseai.tracker.data.repository.GoalRepository,
    private val llmRepository: LlmRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val profileRepository: ProfileRepository,
    private val inAppUpdateManager: InAppUpdateManager,
    private val inAppReviewManager: InAppReviewManager,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val restoreTransactionUseCase: RestoreTransactionUseCase,
    @ApplicationContext private val context: Context,
    entitlementGate: com.pennywiseai.tracker.billing.EntitlementGate,
    private val categoryRepository: CategoryRepository,
    private val recurringSuggestionsUseCase: com.pennywiseai.tracker.domain.usecase.RecurringSuggestionsUseCase,
) : ViewModel() {

    /**
     * The converter Home's figure functions (HomeFigures.kt and its neighbours) take.
     * First in the class on purpose: properties initialise in declaration order, and the
     * collectors init{} launches use it.
     */
    private val convert: ConvertAmount = { amount, from, to ->
        currencyConversionService.convertAmountOrNull(amount, from, to)
    }

    /**
     * Drives the subtle Pro chip in the home top bar — hidden when the
     * user is already entitled so we never push to existing buyers.
     */
    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled
    
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()

    // SMS scanning work progress tracking
    private val _smsScanWorkInfo = MutableStateFlow<WorkInfo?>(null)
    val smsScanWorkInfo: StateFlow<WorkInfo?> = _smsScanWorkInfo.asStateFlow()

    /**
     * Groups for the Home "Groups" section (#664). Groups were only reachable
     * through Settings / the recent list's incidental group cards; an empty
     * list keeps the section (and its Home footprint) entirely absent.
     */
    val groupSummaries: StateFlow<List<com.pennywiseai.tracker.data.repository.GroupSummary>> =
        transactionGroupRepository.observeGroupSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Active goals for the Home "Goals" card (#914): soonest due first, Reached last; empty hides the card. */
    val goals: StateFlow<List<com.pennywiseai.tracker.data.repository.GoalProgress>> =
        goalRepository.observeActiveGoals()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Home section order + visibility (#770); default until DataStore emits. */
    val homeSectionLayout: StateFlow<List<Pair<HomeSection, Boolean>>> =
        userPreferencesRepository.homeSectionLayout
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeSectionLayout.DEFAULT)

    fun moveHomeSection(section: HomeSection, delta: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateHomeSectionLayout { HomeSectionLayout.move(it, section, delta) }
        }
    }

    fun setHomeSectionVisible(section: HomeSection, visible: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateHomeSectionLayout { HomeSectionLayout.withVisibility(it, section, visible) }
        }
    }

    /**
     * The user's current budget cycle window (start, end). Recomputed whenever
     * the start day pref changes — used everywhere the home surface used to
     * do `now.withDayOfMonth(1)..now.withDayOfMonth(lengthOfMonth())`. Exposed
     * as a StateFlow so the greeting card / breakdown dialog can read the
     * same window the data is bucketed against.
     */
    private val _currentCycleWindow = MutableStateFlow(
        BudgetCycle.currentCycle(LocalDate.now(), BudgetCycle.DEFAULT_START_DAY)
    )
    val currentCycleWindow: StateFlow<Pair<LocalDate, LocalDate>> = _currentCycleWindow.asStateFlow()

    // Store currency breakdown maps for quick access when switching currencies
    private var currentMonthBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> = emptyMap()
    private var lastMonthBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> = emptyMap()

    // The previous SpendForecast.USUAL_PERIODS complete cycles, most recent first, for
    // the forecast's "usual". The first one is lastMonthBreakdownMap.
    private var previousCycleBreakdownMaps: List<Map<String, TransactionRepository.MonthlyBreakdown>> = emptyList()
    // The previous cycle cut at today's day-of-cycle, for the "vs this time last month" chip.
    private var lastMonthSamePointBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> = emptyMap()

    // Per-currency LENT-loan settlement loss for each of those cycles, folded into "usual"
    // the same way currentMonthLoanLossByCurrency is folded into this cycle's spend.
    private var previousCycleLoanLoss: List<Map<String, BigDecimal>> = emptyList()

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

    // Visible spendable accounts in their own currency (not pre-converted), for Safe to spend.
    private val spendableRaw = MutableStateFlow<List<AccountBalanceEntity>>(emptyList())

    // Today's date as a flow, so day-based figures move on when Home is resumed on a new day.
    private val today = MutableStateFlow(LocalDate.now())

    /**
     * Declared above init{} deliberately: Kotlin initialises properties in declaration
     * order, and init calls observeReviewPrompt(). Leaving this further down the class
     * means the flow does not exist yet when that runs.
     */
    private val _reviewPrompt = MutableStateFlow<ReviewPrompt?>(null)
    val reviewPrompt: StateFlow<ReviewPrompt?> = _reviewPrompt.asStateFlow()

    /** The one-time Home card pointing engaged free users to Pro (#945). */
    val showProHomeCard: StateFlow<Boolean> = combine(
        entitlementGate.resolvedProEntitlement,
        userPreferencesRepository.proHomeCardHandled,
        transactionRepository.observeActiveTransactionCount(),
        _reviewPrompt,
    ) { isPro, handled, count, reviewPrompt ->
        shouldShowProHomeCard(
            isPro = isPro,
            handled = handled,
            installedAtMillis = context.packageManager
                .getPackageInfo(context.packageName, 0).firstInstallTime,
            nowMillis = System.currentTimeMillis(),
            transactionCount = count,
            reviewPromptShowing = reviewPrompt != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The one-time "start budgets on payday?" card: the day the salary seems to land, offered
     * only while the budget cycle is still the calendar month (day 1) and the card hasn't been
     * accepted or dismissed. Null hides it. Detection reruns on any change to those months'
     * transactions (a salary arriving with a later scan, or one recategorized as Salary).
     */
    val paydaySuggestion: StateFlow<Int?> = combine(
        userPreferencesRepository.paydaySuggestionHandled,
        userPreferencesRepository.budgetCycleStartDay,
    ) { handled, cycleDay -> !handled && cycleDay == 1 }
        .distinctUntilChanged()
        .flatMapLatest { eligible ->
            if (!eligible) return@flatMapLatest flowOf(null)
            // Complete months only: a stray credit early in the current month would
            // otherwise be that month's "salary" and break the agreement check.
            val thisMonth = YearMonth.now()
            combine(
                transactionRepository.getTransactionsBetweenDates(
                    thisMonth.minusMonths(PAYDAY_HISTORY_MONTHS).atDay(1).atStartOfDay(),
                    thisMonth.atDay(1).atStartOfDay().minusNanos(1)
                ),
                categoryRepository.getAllCategories(),
            ) { transactions, categories ->
                // By built-in identity, so a translated or renamed Salary category still matches.
                detectPayday(transactions, categories.nameForBuiltin("Salary"))?.takeIf { it != 1 }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The compact "possible recurring bills" card: the untracked repeating payments
     * found in history, or null to hide it. Dismissing hides it for 30 days, and
     * after that it comes back only for a suggestion it hasn't shown before.
     */
    val recurringSuggestions: StateFlow<List<com.pennywiseai.tracker.domain.spending.RecurringSuggestion>?> = combine(
        recurringSuggestionsUseCase.suggestions,
        userPreferencesRepository.recurringHomeCardSeen,
        userPreferencesRepository.recurringHomeCardDismissedAt,
    ) { suggestions, seen, dismissedAt ->
        val quiet = dismissedAt != null &&
            System.currentTimeMillis() - dismissedAt < RECURRING_CARD_QUIET_MILLIS
        suggestions.takeIf { list -> !quiet && list.any { it.key !in seen } }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissRecurringSuggestions(shown: List<com.pennywiseai.tracker.domain.spending.RecurringSuggestion>) {
        viewModelScope.launch {
            userPreferencesRepository.dismissRecurringHomeCard(shown.map { it.key }, System.currentTimeMillis())
        }
    }

    /**
     * Home's "Safe to spend": visible spendable accounts (minus the ones the user left
     * out) less active subscriptions due before the cycle ends, capped at what's left
     * in the overall budget ([safeToSpendCap]). Netted per currency;
     * in unified mode each figure is converted to the display currency first, and one
     * with no rate stays in its own currency as a separate line. Null hides the card.
     */
    val safeToSpend: StateFlow<SafeToSpendUi?> = combine(
        spendableRaw,
        subscriptionRepository.getActiveSubscriptions(),
        userPreferencesRepository.safeToSpendExcludedAccounts,
        combine(
            _currentCycleWindow,
            today,
            _uiState.map { it.budgetSummary }.distinctUntilChanged(),
        ) { window, day, budgets -> Triple(window.second, day, budgets?.safeToSpendCap(day)) },
        combine(
            userPreferencesRepository.unifiedCurrencyMode,
            userPreferencesRepository.displayCurrency,
            _uiState.map { it.selectedCurrency }.distinctUntilChanged(),
            currencyConversionService.getAllRatesFlow(),
        ) { unified, display, selected, _ -> if (unified) display to true else selected to false },
    ) { accounts, subscriptions, excluded, (periodEnd, today, budgetLeft), (primaryCurrency, unified) ->
        if (accounts.isEmpty()) return@combine null
        suspend fun Money.forTotal(): Money =
            if (!unified || currency == primaryCurrency) this
            else currencyConversionService.convertAmountOrNull(amount, currency, primaryCurrency)
                ?.let { Money(it, primaryCurrency) } ?: this // no rate: keep it apart (#670)
        val bills = SafeToSpend.billsInWindow(SafeToSpend.billsFrom(subscriptions), today, periodEnd)
        val results = SafeToSpend.calculate(
            balances = countedInSafeToSpend(accounts, excluded).map { Money(it.balance, it.currency).forTotal() },
            bills = bills.map { it.copy(amount = it.amount.forTotal()) },
            today = today,
            periodEnd = periodEnd,
            budgetLeft = budgetLeft,
        )
        val primary = results.firstOrNull { it.currency == primaryCurrency }
            ?: SafeToSpend.calculate(listOf(Money.zero(primaryCurrency)), emptyList(), today, periodEnd).single()
        SafeToSpendUi(primary, results - primary, periodEnd, accounts, excluded, bills)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Called when Home resumes. On a new day, move the cycle window (if it rolled over)
     * and re-run the day-based figures: forecast and Safe to spend.
     */
    fun refreshDate() {
        val now = LocalDate.now()
        if (now == today.value) return
        today.value = now
        viewModelScope.launch {
            _currentCycleWindow.value = BudgetCycle.currentCycle(now, userPreferencesRepository.budgetCycleStartDay.first())
            updateUIStateForCurrency(_uiState.value.selectedCurrency, _uiState.value.availableCurrencies)
        }
    }

    fun setSafeToSpendAccountIncluded(account: AccountBalanceEntity, included: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSafeToSpendAccountIncluded(safeToSpendKey(account), included)
        }
    }

    init {
        loadUnifiedModePreferences()
        loadUserName()
        observeReviewPrompt()
        // Mirror the "count card spend as expense" pref into UI state so the
        // "Money in motion" card can hide its Credit chip when it's on (#705).
        viewModelScope.launch {
            userPreferencesRepository.countCreditCardAsExpense.collect { on ->
                _uiState.value = _uiState.value.copy(countCreditCardAsExpense = on)
            }
        }
        // Load base currency FIRST so selectedCurrency is set before data loads
        viewModelScope.launch {
            val base = userPreferencesRepository.baseCurrency.first()
            baseCurrency = base
            // In unified mode every figure is converted into the display currency, so
            // start there — starting on the base currency would label converted
            // amounts with the wrong symbol (e.g. USD values shown as ₹).
            val initial = if (userPreferencesRepository.unifiedCurrencyMode.first()) {
                userPreferencesRepository.displayCurrency.first()
            } else base
            _uiState.value = _uiState.value.copy(
                selectedCurrency = initial,
                availableCurrencies = listOf(initial)
            )
            loadHomeData()
        }
        // Keep listening for base currency changes
        loadBaseCurrency()
        observeSelectedProfile()
        observeProfiles()
        observeBudgetCycle()
    }

    /**
     * Recompute the current cycle window whenever the user's start day changes
     * (or once on first emission). Cheap: just two `LocalDate` reads + arithmetic.
     */
    private fun observeBudgetCycle() {
        // Followed live: a shake or the eye anywhere in the app updates Home too.
        userPreferencesRepository.isBalanceHidden
            .onEach { hidden -> _uiState.value = _uiState.value.copy(isBalanceHidden = hidden) }
            .launchIn(viewModelScope)

        userPreferencesRepository.budgetCycleStartDay
            .onEach { startDay ->
                _currentCycleWindow.value = BudgetCycle.currentCycle(LocalDate.now(), startDay)
            }
            .launchIn(viewModelScope)
    }

    /** Hides or shows amounts app-wide; remembered across launches. */
    fun toggleBalanceVisibility() {
        viewModelScope.launch { userPreferencesRepository.toggleBalanceHidden() }
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
                // Unified mode pins the selection to the display currency.
                if (userPreferencesRepository.unifiedCurrencyMode.first()) return@onEach
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
            // Load current cycle breakdown by currency (filtered by business/personal).
            // Loan-linked transactions are excluded — loan principal is shown separately
            // as "Lent this cycle", and settlement losses are folded into expenses below.
            //
            // flatMapLatest on the cycle window so a start-day change (e.g. Settings
            // → "25th" while the screen is open) cancels the in-flight query and
            // re-fetches against the new window — without this, the captured
            // cycleStart would go stale. `today` too: resuming on a new day inside the
            // same cycle leaves the window unchanged, and the old end date would keep
            // today's spending out of "Spent this month".
            combine(_currentCycleWindow, today) { (cycleStart, _), day -> cycleStart to day }
                .distinctUntilChanged()
                .flatMapLatest { (cycleStart, day) ->
                    transactionRepository.getTransactionsBetweenDates(cycleStart, day)
                }
                .combine(userPreferencesRepository.selectedProfileId) { transactions, profileId ->
                    transactions to profileId
                }
                .combine(_cachedAccountBalances.filterNotNull()) { (transactions, profileId), balances ->
                    filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
                        .forTotals()
                }
                .combine(userPreferencesRepository.countCreditCardAsExpense) { nonLoan, creditAsExpense ->
                    breakdownByCurrency(nonLoan, creditAsExpense)
                }
                .collect { breakdownByCurrency ->
                    updateBreakdownForSelectedCurrency(breakdownByCurrency, isCurrentMonth = true)
                }
        }
        
        viewModelScope.launch {
            // Load account balances — combined with unified mode preferences so that
            // individual account entities are pre-converted when unified mode is on.
            combine(
                accountBalanceRepository.getAllLatestBalances(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                // Re-run conversion whenever exchange rates change, so a balance that
                // first rendered raw (rates not fetched yet, or device was offline)
                // self-heals the moment the rate lands instead of staying mislabelled.
                currencyConversionService.getAllRatesFlow()
            ) { allBalances, isUnified, displayCurrency, _ ->
                Triple(allBalances, isUnified, displayCurrency)
            }.collect { (allBalances, isUnified, displayCurrency) ->
                // Cache the raw (unfiltered) balances for refreshAccountBalances/refreshHiddenAccounts
                _cachedAccountBalances.value = allBalances

                // Get hidden accounts from SharedPreferences
                val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

                // Filter out hidden accounts and apply business filter
                val balances = filterVisibleBalances(allBalances, hiddenAccounts)
                // Separate credit cards from regular accounts (hide zero balance accounts).
                // Loan accounts land in neither: they are owed, not spendable (#792).
                val rawRegularAccounts = spendableAccounts(balances)
                spendableRaw.value = rawRegularAccounts
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

                // Only balances already in selectedCurrency are totalled; anything else (native
                // mode, or no rate yet) is left out and flagged, never added raw (₹ + $ ≠ anything).
                val totals = balanceTotals(regularAccounts, creditCards, selectedCurrency)
                val totalBalanceInSelectedCurrency = totals.balance
                val totalAvailableCreditInSelectedCurrency = totals.availableCredit
                val hasApproximateBalance = totals.hasOtherCurrencies

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

                // Balance is ready as soon as we have account data.
                // Conversion failures are non-blocking — convertAmount returns the
                // original amount as fallback, so no account is silently dropped.

                _uiState.value = _uiState.value.copy(
                    accountBalances = regularAccounts,  // Pre-converted in unified mode
                    creditCards = creditCards,           // Pre-converted in unified mode
                    totalBalance = totalBalanceInSelectedCurrency,
                    totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                    availableCurrencies = updatedAvailableCurrencies,
                    isBalanceReady = true,
                    isApproximateBalance = hasApproximateBalance
                )
            }
        }

        viewModelScope.launch {
            // Load current cycle transactions by type (currency-filtered, business-filtered).
            // Re-fires on cycle-window change via flatMapLatest.
            _currentCycleWindow
                .flatMapLatest { (cycleStart, cycleEnd) ->
                    transactionRepository.getTransactionsBetweenDates(
                        startDate = cycleStart,
                        endDate = cycleEnd
                    )
                }
                .combine(userPreferencesRepository.selectedProfileId) { transactions, profileId ->
                    transactions to profileId
                }
                .combine(_cachedAccountBalances.filterNotNull()) { (transactions, profileId), balances ->
                    filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
                }
                .collect { transactions ->
                    updateTransactionTypeTotals(transactions)
                }
        }

        viewModelScope.launch {
            // Track principal lent during the current cycle — surfaced separately from
            // "Spent this cycle" so loan outflows don't masquerade as everyday spending.
            // Stored per-currency so updateUIStateForCurrency can resolve the right value
            // whenever the user switches currency tabs or unified mode toggles.
            _currentCycleWindow
                .flatMapLatest { (cycleStart, cycleEnd) ->
                    val start = cycleStart.atStartOfDay()
                    val end = cycleEnd.atTime(java.time.LocalTime.MAX)
                    loanRepository.getActiveLentTransactionsInPeriod(start, end)
                }
                .combine(userPreferencesRepository.selectedProfileId) { lentTxns, profileId ->
                    lentTxns to profileId
                }
                .combine(_cachedAccountBalances.filterNotNull()) { (lentTxns, profileId), balances ->
                    val keys = buildProfileAccountKeys(balances)
                    filterTransactionsByProfile(lentTxns, profileId, keys)
                }
                .collect { filtered ->
                    currentMonthLentByCurrency = filtered.groupBy { it.currency }.mapValues { (_, txs) ->
                        // Honour partial-loan tagging: only the loan_contribution
                        // portion of a transaction (when set) counts toward
                        // "Lent this cycle"; falls back to the full amount for
                        // legacy / full-amount transactions.
                        txs.fold(BigDecimal.ZERO) { acc, tx -> acc + (tx.loanContribution ?: tx.amount) }
                    }
                    updateUIStateForCurrency(_uiState.value.selectedCurrency, _uiState.value.availableCurrencies)
                }
        }

        viewModelScope.launch {
            // Track losses on LENT loans settled this cycle. Each loss feeds back into the
            // displayed "Spent this cycle" via updateUIStateForCurrency.
            //
            // LoanEntity has no profile column, so the profile filter is applied via the
            // source EXPENSE transaction (same bank+account → same profile).
            _currentCycleWindow
                .flatMapLatest { (cycleStart, cycleEnd) ->
                    val start = cycleStart.atStartOfDay()
                    val end = cycleEnd.atTime(java.time.LocalTime.MAX)
                    loanRepository.getLentLoansSettledInPeriod(start, end)
                }
                .combine(userPreferencesRepository.selectedProfileId) { settledLoans, profileId ->
                    settledLoans to profileId
                }
                .combine(_cachedAccountBalances.filterNotNull()) { (settledLoans, profileId), balances ->
                    Triple(settledLoans, profileId, balances)
                }
                // A collect, not a trailing combine: a flow with no collector never runs,
                // so settlement losses silently stopped reaching "Spent" after #571.
                .collect { (settledLoans, profileId, balances) ->
                    val keys = buildProfileAccountKeys(balances)
                    currentMonthLoanLossByCurrency = loanRepository.settlementLossByCurrency(settledLoans) {
                        filterTransactionsByProfile(listOf(it), profileId, keys).isNotEmpty()
                    }
                    updateUIStateForCurrency(_uiState.value.selectedCurrency, _uiState.value.availableCurrencies)
                }
        }

        viewModelScope.launch {
            // Load previous-cycle breakdown by currency (filtered by business/personal)
            // "Last month" here means "previous cycle" so the comparison lines up with the
            // custom start day (a user on the 25th-of-month cycle sees 25th→24th vs
            // 25th→24th, not 1st→today vs 1st→today).
            //
            // One query covers the last SpendForecast.USUAL_PERIODS cycles so the
            // forecast's "usual" uses exactly this spend definition too.
            _currentCycleWindow
                .flatMapLatest { current ->
                    val startDay = userPreferencesRepository.budgetCycleStartDay.first()
                    val cycles = generateSequence(BudgetCycle.previousCycle(current, startDay)) {
                        BudgetCycle.previousCycle(it, startDay)
                    }.take(SpendForecast.USUAL_PERIODS).toList()
                    transactionRepository.getTransactionsBetweenDates(cycles.last().first, cycles.first().second)
                        .map { transactions -> cycles to transactions }
                }
                .combine(userPreferencesRepository.selectedProfileId) { pair, profileId ->
                    pair to profileId
                }
                .combine(_cachedAccountBalances.filterNotNull()) { (pair, profileId), balances ->
                    val (cycles, transactions) = pair
                    val keys = buildProfileAccountKeys(balances)
                    previousCycleLoanLoss = cycles.map { (start, end) ->
                        val settled = loanRepository.getLentLoansSettledInPeriod(
                            start.atStartOfDay(), end.atTime(java.time.LocalTime.MAX)
                        ).first()
                        loanRepository.settlementLossByCurrency(settled) {
                            filterTransactionsByProfile(listOf(it), profileId, keys).isNotEmpty()
                        }
                    }
                    cycles to filterTransactionsByProfile(transactions, profileId, keys).forTotals()
                }
                .combine(userPreferencesRepository.countCreditCardAsExpense) { (cycles, nonLoan), creditAsExpense ->
                    Triple(cycles, nonLoan, creditAsExpense)
                }
                // `today` too: the same-point window grows by a day every day.
                .combine(today) { (cycles, nonLoan, creditAsExpense), day ->
                    val full = cycles.map { (start, end) ->
                        breakdownByCurrency(
                            nonLoan.filter { it.dateTime.toLocalDate() in start..end },
                            creditAsExpense
                        )
                    }
                    // As many days into the previous cycle as today is into this one, under
                    // the headline's own spend rules (credit-card preference included).
                    val (prevStart, _) = cycles.first()
                    val cutoff = samePointCutoff(cycles.first(), _currentCycleWindow.value.first, day)
                    val samePoint = breakdownByCurrency(
                        nonLoan.filter { it.dateTime.toLocalDate() in prevStart..cutoff },
                        creditAsExpense
                    )
                    full to samePoint
                }
                .collect { (breakdowns, samePoint) ->
                    previousCycleBreakdownMaps = breakdowns
                    lastMonthSamePointBreakdownMap = samePoint
                    updateBreakdownForSelectedCurrency(breakdowns.first(), isCurrentMonth = false)
                }
        }

        viewModelScope.launch {
            // Load cumulative spending sparkline for current cycle + previous cycle comparison.
            // Keyed on `today` as well, like the "Spent" query, so a next-day resume
            // extends the chart instead of freezing it at the ViewModel's first day.
            var now = today.value
            combine(_currentCycleWindow, today) { window, day -> window to day }
                .distinctUntilChanged()
                .flatMapLatest { (window, day) ->
                    now = day
                    val startDay = userPreferencesRepository.budgetCycleStartDay.first()
                    val (lastMonthStart, _) = BudgetCycle.previousCycle(window, startDay)
                    transactionRepository.getTransactionsBetweenDates(
                        startDate = lastMonthStart,
                        endDate = day
                    )
                }
                .combine(userPreferencesRepository.selectedProfileId) { allTransactions, profileId ->
                    allTransactions to profileId
                }
                .combine(_cachedAccountBalances.filterNotNull()) { (allTransactions, profileId), balances ->
                    filterTransactionsByProfile(allTransactions, profileId, buildProfileAccountKeys(balances))
                }
                // Rebuild on a currency switch too, or the sparkline keeps drawing the
                // previous currency's figures.
                .combine(
                    _uiState.map { it.selectedCurrency to it.isUnifiedMode }.distinctUntilChanged()
                ) { allTransactions, _ -> allTransactions }
                .collect { allTransactions ->
                    val (firstOfMonth, _) = _currentCycleWindow.value
                    val startDay = userPreferencesRepository.budgetCycleStartDay.first()
                    val (lastMonthStart, _) = BudgetCycle.previousCycle(firstOfMonth to _currentCycleWindow.value.second, startDay)
                    val selectedCurrency = _uiState.value.selectedCurrency
                    val isUnified = _uiState.value.isUnifiedMode

                    val trend = spendTrend(
                        allTransactions,
                        cycleStart = firstOfMonth,
                        previousCycleStart = lastMonthStart,
                        today = now,
                        currency = selectedCurrency,
                        isUnified = isUnified,
                        convert = convert
                    )
                    _uiState.value = _uiState.value.copy(
                        spendingHistory = trend.thisCycle,
                        balanceHistory = trend.thisCycle,
                        lastMonthSpendingHistory = trend.lastCycleSamePoint
                    )
                }
        }

        viewModelScope.launch {
            // Load active loans summary for home carousel
            combine(
                loanRepository.getActiveLoans(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                // Native mode totals only the selected currency, so re-total on a switch
                // instead of relabelling the old currency's figures.
                _uiState.map { it.selectedCurrency }.distinctUntilChanged()
            ) { loans, isUnified, displayCurrency, _ ->
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
                        currencyConversionService.convertAmountOrNull(
                            amount = loan.remainingAmount,
                            fromCurrency = loan.currency,
                            toCurrency = selectedCurrency
                        ) ?: continue // never-rated pair — skip, don't face-value (#670)
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
                                currencyConversionService.convertAmountOrNull(item.transaction.amount, item.transaction.currency, displayCurrency)
                            else null
                            item.copy(convertedAmount = converted)
                        }
                        is HomeRecentItem.GroupItem -> {
                            val amounts = item.transactions
                                .filter { !it.currency.equals(displayCurrency, ignoreCase = true) }
                                .mapNotNull { tx ->
                                    currencyConversionService
                                        .convertAmountOrNull(tx.amount, tx.currency, displayCurrency)
                                        ?.let { tx.id to it }
                                }
                                .toMap()
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
            }.collect { (allActive, isUnified, displayCurrency) ->
                // Money going out only: an income "subscription" (salary, rent received)
                // isn't a bill, and a weekly or annual charge is folded to its monthly
                // share so the card's "Monthly total" is one.
                val subscriptions = allActive.filter { it.direction == SubscriptionDirection.EXPENSE }
                // Unified mode collapses to one converted figure. Native mode keeps
                // currencies apart — summing ₹ + $ into one figure mislabels it — so
                // we expose a per-currency map the card renders as "₹499 · $10".
                val monthlyCosts = subscriptions.map { it to SubscriptionCost.monthlyEquivalent(it.amount, it.billingCycle) }
                val totalAmount = if (isUnified) {
                    var total = java.math.BigDecimal.ZERO
                    for ((sub, monthly) in monthlyCosts) {
                        total += currencyConversionService.convertAmountOrNull(
                            monthly, sub.currency, displayCurrency
                        ) ?: continue // never-rated pair — skip, don't face-value (#670)
                    }
                    total
                } else {
                    java.math.BigDecimal.ZERO
                }
                val totalByCurrency: Map<String, Money> = if (isUnified) {
                    emptyMap()
                } else {
                    monthlyCosts.sumByCurrency({ it.first.currency }, { it.second })
                }
                _uiState.value = _uiState.value.copy(
                    upcomingSubscriptions = subscriptions,
                    upcomingSubscriptionsTotal = totalAmount,
                    upcomingSubscriptionsByCurrency = totalByCurrency,
                    nextDueSubscription = subscriptions
                        .filter { it.nextPaymentDate != null }
                        .minByOrNull { it.nextPaymentDate!! }
                )
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                userPreferencesRepository.baseCurrency,
                // Re-run on a new day too: a weekly budget can reset inside the same
                // monthly cycle, and Safe to spend's cap reads this summary's windows.
                combine(_currentCycleWindow, today) { cycle, _ -> cycle }
            ) { unifiedMode, displayCurrency, baseCurrency, cycle ->
                Quad(unifiedMode, displayCurrency, baseCurrency, cycle)
            }.flatMapLatest { (unifiedMode, displayCurrency, baseCurrency, cycle) ->
                // Always pass today's year-month to the budget repo. The
                // home dashboard is the *current* view; a user on startDay=25
                // seeing the page on Jul 1 should get the Jul view, not the
                // Jun cycle-start view (the cycle's start year-month is for
                // the *Budgets page* navigation, not the home). The repo
                // resolves each budget's window from resolveBudgetWindow
                // independently, so the per-cadence window math is correct
                // regardless of which calendar month the page is on.
                val startDay = userPreferencesRepository.budgetCycleStartDay.first()
                val today = LocalDate.now()
                val todayYm = YearMonth.of(today.year, today.monthValue)
                if (unifiedMode) {
                    budgetGroupRepository.getGroupSpendingAllCurrencies(todayYm.year, todayYm.monthValue)
                        .map { raw ->
                            homeBudgetSummary(raw, displayCurrency, baseCurrency, convert)
                        }
                } else {
                    budgetGroupRepository.getGroupSpending(todayYm.year, todayYm.monthValue, baseCurrency)
                }
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(
                    budgetSummary = summary
                )
            }
        }
    }
    
    private fun calculateMonthlyChange() {
        // Compare like with like: spend so far vs the same number of days into the
        // previous cycle, not the whole previous cycle — that made every early-cycle
        // day read as a green "% less".
        // ponytail: settlement losses sit in the headline but not the same-point window
        // (as with the old full-cycle figure); add a windowed loss query if it matters.
        val state = _uiState.value
        val current = state.currentMonthExpenses
        val lastAtSamePoint = state.lastMonthSamePointExpenses
        _uiState.value = state.copy(
            monthlyChange = current - lastAtSamePoint,
            monthlyChangePercent = monthlyChangePercent(current, lastAtSamePoint)
        )
    }
    
    fun refreshHiddenAccounts() {
        viewModelScope.launch {
            // Use cached balances instead of re-fetching from the repository
            val allBalances = cachedAccountBalances
            if (allBalances.isEmpty()) return@launch

            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

            val visibleBalances = filterVisibleBalances(allBalances, hiddenAccounts)

            val rawRegularAccounts = spendableAccounts(visibleBalances)
            spendableRaw.value = rawRegularAccounts
            val rawCreditCards = visibleBalances.filter { it.isCreditCard }

            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode

            // Pre-convert individual account entities when unified mode is on
            val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
            val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

            // Only balances already in selectedCurrency are totalled; anything else (native
            // mode, or no rate yet) is left out and flagged, never added raw (₹ + $ ≠ anything).
            val totals = balanceTotals(regularAccounts, creditCards, selectedCurrency)
            val totalBalance = totals.balance
            val totalAvailableCredit = totals.availableCredit
            val hasApproximateBalance = totals.hasOtherCurrencies

            _uiState.value = _uiState.value.copy(
                accountBalances = regularAccounts,
                creditCards = creditCards,
                totalBalance = totalBalance,
                totalAvailableCredit = totalAvailableCredit,
                isBalanceReady = true,
                isApproximateBalance = hasApproximateBalance
            )
        }
    }

    /**
     * (forceResync, rescanAll) of a scan waiting for SMS access. Lives as long as the
     * ViewModel, so it outlasts an activity recreated while App info is open.
     * ponytail: lost on process death; Home then shows the scan empty state and hint instead.
     */
    private var pendingScan: Pair<Boolean, Boolean>? = null

    /** SMS access arrived: run the waiting scan, or a normal one. */
    fun onSmsAccessGranted() {
        val (forceResync, rescanAll) = pendingScan ?: (false to false)
        pendingScan = null
        scanSmsMessages(forceResync = forceResync, rescanAll = rescanAll)
    }

    /** Home opened with access while a scan was waiting for it (activity recreated). */
    fun runPendingScan() {
        if (pendingScan != null && context.canReadSms()) onSmsAccessGranted()
    }

    /**
     * Scans SMS messages for transactions.
     * @param forceResync If true, performs a full resync from scratch, reprocessing all SMS messages.
     *                    This is useful when bank parsers have been updated and old transactions need to be re-parsed.
     *                    If false (default), performs an incremental scan for new messages only.
     * @param rescanAll If true, re-reads the whole scan window without deleting
     *                  anything: adds missed messages and keeps every edit.
     */
    fun scanSmsMessages(forceResync: Boolean = false, rescanAll: Boolean = false) {
        // Every scan starts here (sync, resync, empty state, the tutorial). Without access it
        // would find nothing, and a rebuild would delete rows it can't read back: ask instead,
        // and run the scan that was asked for once access arrives.
        if (!context.canReadSms()) {
            pendingScan = forceResync to rescanAll
            _uiState.value = _uiState.value.copy(smsAccessNeeded = true)
            return
        }
        val inputData = workDataOf(
            OptimizedSmsReaderWorker.INPUT_FORCE_RESYNC to forceResync,
            OptimizedSmsReaderWorker.INPUT_RESCAN_ALL to rescanAll
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
        scanWorkId = workRequest.id
        // A rebuild deletes and recreates SMS rows, so its saved count isn't "new".
        scanReportsNew = !forceResync
        scanResultJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = true, scanNewCount = null)

        // Track work progress
        observeWorkProgress()
    }

    fun onSmsAccessPrompted() {
        _uiState.value = _uiState.value.copy(smsAccessNeeded = false)
    }

    private fun observeWorkProgress() {
        val workManager = WorkManager.getInstance(context)

        // Use getWorkInfosById for more direct observation
        workManager.getWorkInfosByTagLiveData(OptimizedSmsReaderWorker.WORK_NAME).observeForever { workInfos ->
            // A running scan wins (so an earlier, finished one sharing the tag can't
            // end it early), then the user's own scan, then any (scans started
            // elsewhere, e.g. onboarding or SmsScanManager, still drive isScanning).
            val currentWork = workInfos.firstOrNull { !it.state.isFinished }
                ?: workInfos.firstOrNull { it.id == scanWorkId }
                ?: workInfos.firstOrNull()
            if (currentWork != null) {
                _smsScanWorkInfo.value = currentWork

                // Update scanning state based on work state
                when (currentWork.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        // "+N" only for the scan the user started here.
                        val justFinished = _uiState.value.isScanning && currentWork.id == scanWorkId && scanReportsNew
                        _uiState.value = _uiState.value.copy(isScanning = false)
                        // The final count rides on outputData; progress is already cleared.
                        if (justFinished) showScanResult(currentWork.outputData.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, 0))
                    }
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

    private var scanWorkId: java.util.UUID? = null
    private var scanReportsNew = true
    private var scanResultJob: kotlinx.coroutines.Job? = null

    /**
     * The user's scan landed: the docked Sync button shows "+N" for a beat, then
     * its icon again. Nothing new skips straight back to the icon, no wait.
     */
    private fun showScanResult(newCount: Int) {
        if (newCount <= 0) return
        _uiState.value = _uiState.value.copy(scanNewCount = newCount)
        scanResultJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SCAN_RESULT_HOLD_MILLIS)
            _uiState.value = _uiState.value.copy(scanNewCount = null)
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
            val rawRegularAccounts = spendableAccounts(balances)
            spendableRaw.value = rawRegularAccounts
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

            // Only balances already in selectedCurrency are totalled; anything else (native
            // mode, or no rate yet) is left out and flagged, never added raw (₹ + $ ≠ anything).
            val totals = balanceTotals(regularAccounts, creditCards, selectedCurrency)
            val totalBalanceInSelectedCurrency = totals.balance
            val totalAvailableCreditInSelectedCurrency = totals.availableCredit
            val hasApproximateBalance = totals.hasOtherCurrencies

            _uiState.value = _uiState.value.copy(
                accountBalances = regularAccounts,
                creditCards = creditCards,
                totalBalance = totalBalanceInSelectedCurrency,
                totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                isBalanceReady = true,
                isApproximateBalance = hasApproximateBalance
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
            deleteTransactionUseCase(transaction)
            com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(context)
        }
    }

    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            viewModelScope.launch {
                restoreTransactionUseCase(transaction)
                _deletedTransaction.value = null
                com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(context)
            }
        }
    }

    fun undoDeleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            restoreTransactionUseCase(transaction)
            com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(context)
        }
    }
    
    fun clearDeletedTransaction() {
        _deletedTransaction.value = null
    }
    
    /**
     * Checks if eligible for in-app review and shows if appropriate.
     * Should be called with the current activity context.
     */
    /**
     * Which review to offer on Home, if any.
     *
     * Checked on composition rather than scheduled through WorkManager: the OEM battery
     * managers that dominate this app's install base (Xiaomi, Oppo, Vivo, Realme) kill
     * background work aggressively, so a scheduled job would fire late, or never. Users
     * open their expense tracker anyway, which makes the scheduler unnecessary.
     *
     * Follows the date, the cycle and the rows in scope. The handled keys are read, not
     * watched: ✕ on January's year banner shouldn't swap in the cycle's banner on the
     * spot, so that one waits until something else changes or the next launch.
     */
    private fun observeReviewPrompt() {
        viewModelScope.launch {
            combine(
                today,
                userPreferencesRepository.budgetCycleStartDay.distinctUntilChanged(),
                periodReviewLoader.rowCountChanges(),
            ) { day, cycleStartDay, _ -> day to cycleStartDay }
                .collectLatest { (day, cycleStartDay) ->
                    _reviewPrompt.value = reviewPromptFor(
                        today = day,
                        cycleStartDay = cycleStartDay,
                        handledYear = userPreferencesRepository.reviewPromptHandledYear.first(),
                        handledMonth = userPreferencesRepository.sharePromptHandledMonth.first(),
                        rowCount = periodReviewLoader::rowCount,
                    )
                }
        }
    }

    /** Called when the user opens or dismisses the prompt — either way it's handled. */
    fun markReviewPromptHandled() {
        val prompt = _reviewPrompt.value ?: return
        _reviewPrompt.value = null
        viewModelScope.launch {
            if (prompt.kind == ReviewKind.YEAR) {
                userPreferencesRepository.markReviewPromptHandledYear(prompt.key)
            } else {
                userPreferencesRepository.markSharePromptHandled(prompt.key)
            }
        }
    }

    /** "Start on day N" on the payday card: the budget cycle moves to payday, and the card never comes back. */
    fun acceptPaydaySuggestion(day: Int) {
        viewModelScope.launch {
            userPreferencesRepository.markPaydaySuggestionHandled()
            userPreferencesRepository.updateBudgetCycleStartDay(day)
        }
    }

    /** ✕ on the payday card. The day stays settable in Settings → Budget cycle. */
    fun dismissPaydaySuggestion() {
        viewModelScope.launch { userPreferencesRepository.markPaydaySuggestionHandled() }
    }

    /** "See Pro" or ✕ on the Pro card — either way it never comes back. */
    fun markProHomeCardHandled() {
        viewModelScope.launch { userPreferencesRepository.markProHomeCardHandled() }
    }

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
            val (cycleStart, cycleEnd) = _currentCycleWindow.value

            val allTransactions = transactionRepository.getTransactionsBetweenDates(
                startDate = cycleStart,
                endDate = cycleEnd
            ).first()
            val transactions = filterTransactions(allTransactions)
            updateTransactionTypeTotals(transactions)
        }
    }

    private fun updateTransactionTypeTotals(transactions: List<TransactionEntity>) {
        val selectedCurrency = _uiState.value.selectedCurrency
        val isUnified = _uiState.value.isUnifiedMode
        // Drop excluded-from-analytics rows here too, so the home CREDIT/TRANSFER/
        // INVESTMENT totals stay consistent with the income/spend breakdown (#451).
        val nonLoanTransactions = transactions.forTotals()

        if (isUnified) {
            // Convert all transactions to display currency
            viewModelScope.launch {
                val motion = moneyInMotion(nonLoanTransactions) { tx ->
                    convert(tx.amount, tx.currency, selectedCurrency)
                }
                _uiState.value = _uiState.value.copy(
                    currentMonthCreditCard = motion.credit,
                    currentMonthTransfer = motion.transfer,
                    currentMonthInvestment = motion.investment
                )
            }
        } else {
            // Filter transactions by selected currency
            val motion = moneyInMotion(nonLoanTransactions.filter { it.currency == selectedCurrency }) { it.amount }
            _uiState.value = _uiState.value.copy(
                currentMonthCreditCard = motion.credit,
                currentMonthTransfer = motion.transfer,
                currentMonthInvestment = motion.investment
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
                val currentBreakdown = combinedBreakdown(currentMonthBreakdownMap, selectedCurrency, convert)
                val lastBreakdown = combinedBreakdown(lastMonthBreakdownMap, selectedCurrency, convert)
                val lastSamePoint = combinedBreakdown(lastMonthSamePointBreakdownMap, selectedCurrency, convert).expenses
                val loanLoss = combinedTotal(currentMonthLoanLossByCurrency, selectedCurrency, convert)
                val lent = combinedTotal(currentMonthLentByCurrency, selectedCurrency, convert)

                _uiState.value = _uiState.value.copy(
                    currentMonthTotal = currentBreakdown.total - loanLoss,
                    currentMonthIncome = currentBreakdown.income,
                    currentMonthExpenses = currentBreakdown.expenses + loanLoss,
                    currentMonthLent = lent,
                    lastMonthTotal = lastBreakdown.total,
                    lastMonthIncome = lastBreakdown.income,
                    lastMonthExpenses = lastBreakdown.expenses,
                    lastMonthSamePointExpenses = lastSamePoint,
                    selectedCurrency = selectedCurrency,
                    availableCurrencies = availableCurrencies,
                    spendForecast = forecastFor(
                        currentBreakdown.expenses + loanLoss,
                        selectedCurrency,
                        previousCycleBreakdownMaps.mapIndexed { i, map ->
                            val loss = previousCycleLoanLoss.getOrNull(i).orEmpty()
                            // A cycle with only a settlement loss still counts as history.
                            if (map.isEmpty() && loss.isEmpty()) null
                            else combinedBreakdown(map, selectedCurrency, convert).expenses +
                                combinedTotal(loss, selectedCurrency, convert)
                        }
                    )
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
                lastMonthSamePointExpenses = lastMonthSamePointBreakdownMap[selectedCurrency]?.expenses ?: BigDecimal.ZERO,
                selectedCurrency = selectedCurrency,
                availableCurrencies = availableCurrencies,
                spendForecast = forecastFor(
                    currentBreakdown.expenses + loanLoss,
                    selectedCurrency,
                    previousCycleBreakdownMaps.mapIndexed { i, map ->
                        val spent = map[selectedCurrency]?.expenses
                        val loss = previousCycleLoanLoss.getOrNull(i)?.get(selectedCurrency)
                        // A cycle with only a settlement loss still counts as history.
                        if (spent == null && loss == null) null
                        else (spent ?: BigDecimal.ZERO) + (loss ?: BigDecimal.ZERO)
                    }
                )
            )
            calculateMonthlyChange()
        }
    }

    /** Month-end forecast for the "Spent this month" figure; null until day 7 of the cycle. */
    private fun forecastFor(spent: BigDecimal, currency: String, previous: List<BigDecimal?>): SpendForecast? {
        val (start, end) = _currentCycleWindow.value
        return SpendForecast.of(spent, currency, start, end, LocalDate.now(), previous)
    }

    private suspend fun convertAccountEntities(
        entities: List<AccountBalanceEntity>,
        targetCurrency: String,
        isUnifiedMode: Boolean
    ): List<AccountBalanceEntity> = convertAccounts(entities, targetCurrency, isUnifiedMode) { from, to ->
        currencyConversionService.getExchangeRate(from, to)
    }

    override fun onCleared() {
        super.onCleared()
        inAppUpdateManager.cleanup()
    }

    private companion object {
        const val RECURRING_CARD_QUIET_MILLIS = 30L * 24 * 60 * 60 * 1000
        /** How long a finished scan's "+N" holds on the Sync button; load-bearing, so not a motion spec. */
        const val SCAN_RESULT_HOLD_MILLIS = 1_600L
    }

}

internal const val PRO_CARD_MIN_INSTALL_DAYS = 14L
internal const val PRO_CARD_MIN_TRANSACTIONS = 30

/**
 * Eligibility for the one-time Home Pro card (#945): a free user (a null [isPro] means
 * entitlement is still loading, so stay hidden) who installed 14+ days ago, has 30+
 * transactions, hasn't tapped or dismissed it, and isn't looking at the share banner.
 */
internal fun shouldShowProHomeCard(
    isPro: Boolean?,
    handled: Boolean,
    installedAtMillis: Long,
    nowMillis: Long,
    transactionCount: Int,
    reviewPromptShowing: Boolean,
): Boolean = isPro == false &&
    !handled &&
    !reviewPromptShowing &&
    transactionCount >= PRO_CARD_MIN_TRANSACTIONS &&
    nowMillis - installedAtMillis >= java.util.concurrent.TimeUnit.DAYS.toMillis(PRO_CARD_MIN_INSTALL_DAYS)

data class HomeUiState(
    /** A scan was asked for without SMS access; Home asks for it, then clears this. */
    val smsAccessNeeded: Boolean = false,
    val userName: String = "User",
    val profileImageUri: String? = null,
    val profileBackgroundColor: Int = 0,
    val currentMonthTotal: BigDecimal = BigDecimal.ZERO,
    val currentMonthIncome: BigDecimal = BigDecimal.ZERO,
    val currentMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val currentMonthLent: BigDecimal = BigDecimal.ZERO,
    val currentMonthCreditCard: BigDecimal = BigDecimal.ZERO,
    // When true, credit-card spend is folded into "Spent this month" (#705), so
    // the "Money in motion" card hides its Credit chip to avoid showing the same
    // amount both inside and "outside" the cash flow.
    val countCreditCardAsExpense: Boolean = false,
    val currentMonthTransfer: BigDecimal = BigDecimal.ZERO,
    val currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    val lastMonthTotal: BigDecimal = BigDecimal.ZERO,
    val lastMonthIncome: BigDecimal = BigDecimal.ZERO,
    val lastMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthlyChange: BigDecimal = BigDecimal.ZERO,
    val lastMonthSamePointExpenses: BigDecimal = BigDecimal.ZERO,
    /** Null when last month had no spending: there is nothing to compare with. */
    val monthlyChangePercent: Int? = null,
    val recentItems: List<HomeRecentItem> = emptyList(),
    val upcomingSubscriptions: List<SubscriptionEntity> = emptyList(),
    /** Unified-mode total, converted to the display currency. */
    val upcomingSubscriptionsTotal: BigDecimal = BigDecimal.ZERO,
    /** Native-mode totals, kept per-currency so a ₹ + $ mix isn't summed. */
    val upcomingSubscriptionsByCurrency: Map<String, Money> = emptyMap(),
    /** The soonest-due active EXPENSE subscription, for the card's "Next:" line. */
    val nextDueSubscription: SubscriptionEntity? = null,
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
    /** New transactions the user's just-finished scan saved, shown briefly on the Sync button. */
    val scanNewCount: Int? = null,
    val showBreakdownDialog: Boolean = false,
    val isUnifiedMode: Boolean = false,
    val transactionHeatmap: Map<Long, Int> = emptyMap(),
    val isBalanceHidden: Boolean = true,
    val isBalanceReady: Boolean = false,
    val isApproximateBalance: Boolean = false,
    val lastMonthSpendingHistory: List<BigDecimal> = emptyList(),
    val loanSummary: LoanSummary? = null,
    val spendForecast: SpendForecast? = null,
    val selectedProfileId: Long? = null,
    val profiles: List<ProfileEntity> = emptyList()
)

/** Pref key for an account in the Safe to spend selection. */
internal fun safeToSpendKey(account: AccountBalanceEntity) = "${account.bankName}_${account.accountLast4}"

/**
 * Cash or a mobile wallet below zero: money can't really go negative there, so it's a
 * tracking gap, not debt, and stays out of Safe to spend. An overdrawn bank account
 * is real debt and still counts.
 */
internal fun isUncountedNegative(account: AccountBalanceEntity): Boolean =
    account.balance.signum() < 0 &&
        (account.accountType.equals("CASH", ignoreCase = true) ||
            account.accountLast4 == AccountBalanceEntity.WALLET_ACCOUNT_MARKER)

/** The accounts Safe to spend adds up: not left out by the user, not [isUncountedNegative]. */
internal fun countedInSafeToSpend(accounts: List<AccountBalanceEntity>, excluded: Set<String>) =
    accounts.filter { safeToSpendKey(it) !in excluded && !isUncountedNegative(it) }

/**
 * What's left in the overall budget for Safe to spend: a spending limit that tracks
 * all expenses and runs today, with its "remaining" exactly as the Home budget card
 * shows it. Several such budgets (say weekly and monthly): the tightest one wins.
 */
internal fun BudgetOverallSummary.safeToSpendCap(today: LocalDate): Money? =
    groups.filter {
        it.isTrackingAllExpenses && it.group.budget.groupType == BudgetGroupType.LIMIT &&
            it.windowDays > 0 && !today.isBefore(it.windowStart) && !today.isAfter(it.windowEnd)
    }.minOfOrNull { it.remaining }?.let { Money(it, currency) }

internal data class BalanceTotals(
    val balance: BigDecimal,
    val availableCredit: BigDecimal,
    /** Some account or card is in another currency and was left out of both totals. */
    val hasOtherCurrencies: Boolean,
)

internal fun balanceTotals(
    accounts: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    currency: String,
): BalanceTotals {
    val (sameAccounts, otherAccounts) = accounts.partition { it.currency == currency }
    val (sameCards, otherCards) = creditCards.partition { it.currency == currency }
    return BalanceTotals(
        balance = sameAccounts.fold(BigDecimal.ZERO) { a, acc -> a + acc.balance },
        availableCredit = sameCards.fold(BigDecimal.ZERO) { a, c -> a + (c.creditLimit ?: BigDecimal.ZERO) - c.balance },
        hasOtherCurrencies = otherAccounts.isNotEmpty() || otherCards.isNotEmpty(),
    )
}

internal fun monthlyChangePercent(current: BigDecimal, last: BigDecimal): Int? =
    if (last.signum() == 0) null
    else (current - last).multiply(BigDecimal(100)).divide(last, 0, RoundingMode.HALF_UP).toInt()

data class SafeToSpendUi(
    /** The headline figure, in the display (unified) or selected currency. */
    val primary: SafeToSpend,
    /** Figures in other currencies, never added to [primary]. */
    val others: List<SafeToSpend>,
    val periodEnd: LocalDate,
    /** Every visible spendable account, each in its own currency. */
    val accounts: List<AccountBalanceEntity>,
    val excludedKeys: Set<String>,
    /** Bills due today..[periodEnd], each in its own currency. */
    val bills: List<Bill>,
)

data class LoanSummary(
    val activeLoans: List<LoanEntity>,
    val totalLentRemaining: BigDecimal,
    val totalBorrowedRemaining: BigDecimal
)

private data class Quad<A, B, C, D>(
    val a: A,
    val b: B,
    val c: C,
    val d: D
)