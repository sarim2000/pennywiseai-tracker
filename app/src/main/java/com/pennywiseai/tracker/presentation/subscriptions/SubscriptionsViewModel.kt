package com.pennywiseai.tracker.presentation.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.usecase.MarkSubscriptionPaidUseCase
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val markSubscriptionPaidUseCase: MarkSubscriptionPaidUseCase,
    private val transactionRepository: com.pennywiseai.tracker.data.repository.TransactionRepository,
    accountBalanceRepository: AccountBalanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    /** Accounts offered as the funding source in the edit dialog (#570). */
    val accounts: StateFlow<List<AccountBalanceEntity>> =
        accountBalanceRepository.getAllLatestBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        viewModelScope.launch {
            combine(
                subscriptionRepository.getActiveSubscriptions(),
                subscriptionRepository.getEndedSubscriptions(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                userPreferencesRepository.baseCurrency
            ) { subscriptions, ended, isUnified, displayCurrency, baseCurrency ->
                arrayOf(subscriptions, ended, isUnified, displayCurrency, baseCurrency)
            }.collect { values ->
                @Suppress("UNCHECKED_CAST")
                val subscriptions = values[0] as List<SubscriptionEntity>
                @Suppress("UNCHECKED_CAST")
                val endedSubscriptions = values[1] as List<SubscriptionEntity>
                val isUnified = values[2] as Boolean
                val displayCurrency = values[3] as String
                val baseCurrency = values[4] as String
                // Unified mode collapses everything into displayCurrency, so a
                // single figure is honest. Native mode keeps each currency apart —
                // a ₹ + $ mix can't be one figure — so we expose a per-currency map
                // the header renders as "₹399 · $30" (no sub silently dropped).
                val totalMonthlyAmount = if (isUnified) {
                    var total = BigDecimal.ZERO
                    for (sub in subscriptions) {
                        total += currencyConversionService.convertAmount(
                            sub.amount, sub.currency, displayCurrency
                        )
                    }
                    total
                } else {
                    BigDecimal.ZERO
                }
                val totalByCurrency: Map<String, Money> = if (isUnified) {
                    emptyMap()
                } else {
                    subscriptions.sumByCurrency({ it.currency }, { it.amount })
                }

                val convertedAmounts = if (isUnified) {
                    val map = mutableMapOf<Long, BigDecimal>()
                    for (sub in subscriptions) {
                        if (!sub.currency.equals(displayCurrency, ignoreCase = true)) {
                            map[sub.id] = currencyConversionService.convertAmount(
                                sub.amount, sub.currency, displayCurrency
                            )
                        }
                    }
                    map
                } else {
                    emptyMap()
                }

                // Order the active list so "needs attention" rises to the
                // top: unpaid-this-cycle subs first (sorted by next-payment
                // date ascending, nulls last), recently-paid subs at the
                // bottom (sorted by last-paid descending).
                //
                // Today-anchored cycle check (NOT schedule-anchored — the
                // schedule slides forward every time we advance, which used
                // to break this guard once any sub had been marked once).
                // Semantic: a sub is "paid this cycle" if today is still
                // within one billing cycle of lastPaidAt.
                val today = java.time.LocalDate.now()
                val isPaidThisCycle: (com.pennywiseai.tracker.data.database.entity.SubscriptionEntity) -> Boolean = { sub ->
                    sub.lastPaidAt?.let { lastPaid ->
                        val nextLegitMark = subscriptionRepository.advance(lastPaid, sub.billingCycle)
                        today.isBefore(nextLegitMark)
                    } ?: false
                }
                val (paid, unpaid) = subscriptions.partition(isPaidThisCycle)
                val ordered = unpaid.sortedWith(
                    compareBy(nullsLast()) { it.nextPaymentDate },
                ) + paid.sortedByDescending { it.lastPaidAt }
                val paidIds = paid.map { it.id }.toSet()

                _uiState.value = _uiState.value.copy(
                    activeSubscriptions = ordered,
                    paidThisCycleIds = paidIds,
                    paidThisCycleCount = paid.size,
                    endedSubscriptions = endedSubscriptions,
                    totalMonthlyAmount = totalMonthlyAmount,
                    totalByCurrency = totalByCurrency,
                    convertedAmounts = convertedAmounts,
                    displayCurrency = if (isUnified) displayCurrency else baseCurrency,
                    isUnifiedMode = isUnified,
                    isLoading = false
                )
            }
        }
    }
    
    fun hideSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.hideSubscription(subscriptionId)
            _uiState.value = _uiState.value.copy(
                lastHiddenSubscription = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }
            )
        }
    }

    fun undoHide() {
        _uiState.value.lastHiddenSubscription?.let { subscription ->
            viewModelScope.launch {
                subscriptionRepository.unhideSubscription(subscription.id)
                _uiState.value = _uiState.value.copy(lastHiddenSubscription = null)
            }
        }
    }

    fun markAsEnded(subscriptionId: Long) {
        viewModelScope.launch {
            val snapshot = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }
            subscriptionRepository.markAsEnded(subscriptionId)
            _uiState.value = _uiState.value.copy(lastEndedSubscription = snapshot)
        }
    }

    fun undoEnd() {
        _uiState.value.lastEndedSubscription?.let { subscription ->
            viewModelScope.launch {
                subscriptionRepository.reactivateSubscription(subscription.id)
                _uiState.value = _uiState.value.copy(lastEndedSubscription = null)
            }
        }
    }

    fun reactivateSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.reactivateSubscription(subscriptionId)
        }
    }

    fun updateSubscription(
        id: Long,
        merchantName: String,
        amount: BigDecimal,
        nextPaymentDate: java.time.LocalDate?,
        category: String?,
        account: AccountBalanceEntity?,
    ) {
        viewModelScope.launch {
            val existing = subscriptionRepository.getSubscriptionById(id) ?: return@launch
            subscriptionRepository.updateSubscription(
                existing.copy(
                    merchantName = merchantName.trim(),
                    amount = amount,
                    nextPaymentDate = nextPaymentDate,
                    category = category?.trim()?.takeIf { it.isNotEmpty() },
                    // Re-key the funding account. Clearing it (null) reverts to the
                    // unlinked "Manual Entry" placeholder so mark-as-paid stops
                    // touching a balance. (#570)
                    bankName = account?.bankName ?: "Manual Entry",
                    accountLast4 = account?.accountLast4,
                    updatedAt = java.time.LocalDateTime.now()
                )
            )
        }
    }

    fun deleteSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(subscriptionId)
        }
    }

    /**
     * #412 — user-initiated "mark as paid" (or "mark as received" for
     * income-direction subscriptions). Creates the transaction + advances
     * the schedule, then publishes a snackbar message the UI consumes.
     */
    fun markAsPaid(subscriptionId: Long, paymentDate: java.time.LocalDate) {
        viewModelScope.launch {
            val result = markSubscriptionPaidUseCase.execute(subscriptionId, paymentDate)
            val merchant = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }?.merchantName
                ?: "Subscription"
            val message = when (result) {
                is MarkSubscriptionPaidUseCase.Result.Created ->
                    "$merchant marked paid · next cycle on ${result.nextPaymentDate}"
                is MarkSubscriptionPaidUseCase.Result.Linked ->
                    "$merchant linked · next cycle on ${result.nextPaymentDate}"
                is MarkSubscriptionPaidUseCase.Result.AlreadyMarked ->
                    "$merchant already marked this cycle · advanced to ${result.nextPaymentDate}"
                MarkSubscriptionPaidUseCase.Result.NoScheduledDate ->
                    "Set a next-payment date on $merchant first"
                MarkSubscriptionPaidUseCase.Result.SubscriptionNotFound ->
                    "Couldn't find that subscription"
            }
            _uiState.value = _uiState.value.copy(markPaidMessage = message)
        }
    }

    /** UI calls this after consuming the snackbar message. */
    fun clearMarkPaidMessage() {
        _uiState.value = _uiState.value.copy(markPaidMessage = null)
    }

    /**
     * Suggested-payment candidates for the mark-as-paid sheet (#412
     * follow-up). When an EXPENSE subscription is on auto-pay, the bank
     * SMS already created a transaction — we surface those here so the
     * user can link instead of creating a duplicate phantom.
     *
     * Matching: case-insensitive merchant exact + amount exact (±₹0.01)
     * over the last 30 days, EXPENSE only, excluding phantoms we've
     * created ourselves. Amount match is required — a one-off ₹789
     * Netflix purchase shouldn't surface as a candidate for a ₹499 sub.
     */
    suspend fun candidatesFor(
        sub: SubscriptionEntity,
    ): List<com.pennywiseai.tracker.data.database.entity.TransactionEntity> {
        val cutoff = java.time.LocalDate.now().minusDays(30).atStartOfDay()
        return transactionRepository.findRecentExpensesByMerchantAndAmount(
            merchant = sub.merchantName,
            amount = sub.amount,
            since = cutoff,
            limit = 5,
        )
    }

    /**
     * Link an existing bank-derived transaction as this subscription's
     * current-cycle payment. Advances the schedule; doesn't duplicate.
     */
    fun linkExistingTransaction(subscriptionId: Long, transactionId: Long) {
        viewModelScope.launch {
            val result = markSubscriptionPaidUseCase.linkExisting(subscriptionId, transactionId)
            val merchant = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }?.merchantName
                ?: "Subscription"
            val message = when (result) {
                is MarkSubscriptionPaidUseCase.Result.Linked ->
                    "$merchant linked to existing payment · next cycle on ${result.nextPaymentDate}"
                MarkSubscriptionPaidUseCase.Result.NoScheduledDate ->
                    "Set a next-payment date on $merchant first"
                MarkSubscriptionPaidUseCase.Result.SubscriptionNotFound ->
                    "Couldn't find that subscription"
                else -> "Linked"
            }
            _uiState.value = _uiState.value.copy(markPaidMessage = message)
        }
    }
}

data class SubscriptionsUiState(
    val activeSubscriptions: List<SubscriptionEntity> = emptyList(),
    /** IDs of subs in [activeSubscriptions] whose current cycle is paid — used to drive the row badge. */
    val paidThisCycleIds: Set<Long> = emptySet(),
    /** Count of subs in [activeSubscriptions] whose current cycle is paid. */
    val paidThisCycleCount: Int = 0,
    val endedSubscriptions: List<SubscriptionEntity> = emptyList(),
    /** Unified-mode header total, already converted to [displayCurrency]. */
    val totalMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    /** Native-mode header totals, kept per-currency so no sub is dropped. */
    val totalByCurrency: Map<String, Money> = emptyMap(),
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val displayCurrency: String? = null,
    val isUnifiedMode: Boolean = false,
    val isLoading: Boolean = true,
    val lastHiddenSubscription: SubscriptionEntity? = null,
    val lastEndedSubscription: SubscriptionEntity? = null,
    /** Snackbar text after a mark-as-paid action; cleared by [SubscriptionsViewModel.clearMarkPaidMessage]. */
    val markPaidMessage: String? = null,
)