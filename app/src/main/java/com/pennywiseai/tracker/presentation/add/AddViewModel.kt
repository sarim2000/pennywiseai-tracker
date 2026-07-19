package com.pennywiseai.tracker.presentation.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.domain.usecase.AddSubscriptionUseCase
import com.pennywiseai.tracker.domain.usecase.GetCategoriesUseCase
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class AddViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val receiptManager: ReceiptManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** All known tag names for autocomplete in the add form. */
    val allTagNames: StateFlow<List<String>> = tagRepository.observeAllTagNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // When launched via "Duplicate", this holds the id of the source transaction
    // whose values should pre-fill the form. Null for a fresh add.
    private val sourceTransactionId: Long? = savedStateHandle.get<Long>("sourceTransactionId")
    
    // General UI State
    private val _uiState = MutableStateFlow(AddUiState())
    val uiState: StateFlow<AddUiState> = _uiState.asStateFlow()
    
    // Transaction Tab State
    private val _transactionUiState = MutableStateFlow(TransactionUiState())
    val transactionUiState: StateFlow<TransactionUiState> = _transactionUiState.asStateFlow()
    
    // Subscription Tab State
    private val _subscriptionUiState = MutableStateFlow(SubscriptionUiState())
    val subscriptionUiState: StateFlow<SubscriptionUiState> = _subscriptionUiState.asStateFlow()
    
    
    init {
        // Load base currency and set as default for both transaction and subscription
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            _transactionUiState.update { it.copy(currency = baseCurrency) }
            _subscriptionUiState.update { it.copy(currency = baseCurrency) }

            // If launched via "Duplicate", prefill the transaction form from the
            // source. Done after the default currency so the source's currency wins.
            sourceTransactionId?.let { prefillFromTransaction(it) }
        }
    }

    /**
     * Loads the given transaction and copies its editable values into the
     * transaction form as a brand-new draft. The original is never modified;
     * id/hash/receipt are intentionally not copied so saving creates a new
     * transaction. The date defaults to now (template behaviour) while all
     * other fields are copied verbatim.
     */
    private suspend fun prefillFromTransaction(transactionId: Long) {
        val source = transactionRepository.getTransactionById(transactionId) ?: return

        _transactionUiState.update { currentState ->
            currentState.copy(
                amount = source.amount.stripTrailingZeros().toPlainString(),
                amountError = null,
                transactionType = source.transactionType,
                merchant = source.merchantName,
                merchantError = null,
                category = source.category,
                categoryError = null,
                date = LocalDateTime.now(),
                notes = source.description ?: "",
                tags = tagRepository.getTagNamesForTransaction(source.id),
                isRecurring = source.isRecurring,
                currency = source.currency,
                budgetImpactType = source.budgetImpactType,
                budgetCategory = source.budgetCategory
            )
        }

        // Best-effort match of the source account to an existing account. `accounts`
        // is a WhileSubscribed StateFlow whose value is still empty at init time, so
        // we wait for the first non-empty emission instead of reading .value once
        // (which always returned emptyList() and left the selector blank).
        val last4 = source.accountNumber?.takeLast(4)
        if (source.bankName != null || last4 != null) {
            viewModelScope.launch {
                val matched = accounts.first { it.isNotEmpty() }
                    .firstOrNull { it.bankName == source.bankName && it.accountLast4 == last4 }
                    ?: return@launch
                // Don't override a selection the user may have already made.
                _transactionUiState.update { state ->
                    if (state.selectedAccount == null) state.copy(selectedAccount = matched) else state
                }
            }
        }
    }

    // Categories for dropdowns
    val categories = getCategoriesUseCase.execute()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All accounts for selection in manual transaction entry
    val accounts = accountBalanceRepository.getAllLatestBalances()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeBudgetCategories = budgetGroupRepository.getActiveGroups()
        .map { groups ->
            groups.map { it.categories.map { cat -> cat.categoryName } }.flatten().distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Transaction Tab Functions
    fun updateSelectedAccount(account: AccountBalanceEntity?) {
        _transactionUiState.update { currentState ->
            // For a TRANSFER, the FROM account drives the entry currency so the
            // two figures can't silently disagree (we never sum across currencies).
            val currency = if (currentState.transactionType == TransactionType.TRANSFER && account != null) {
                account.currency
            } else {
                currentState.currency
            }
            currentState.copy(selectedAccount = account, currency = currency)
        }
    }

    /** The TO account for a TRANSFER (see [updateSelectedAccount] for FROM). */
    fun updateToAccount(account: AccountBalanceEntity?) {
        _transactionUiState.update { currentState ->
            currentState.copy(toAccount = account)
        }
    }

    // Transaction Tab Functions
    fun updateTransactionAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val decimalCount = filtered.count { it == '.' }
        val validAmount = if (decimalCount <= 1) filtered else _transactionUiState.value.amount
        
        _transactionUiState.update { currentState ->
            currentState.copy(
                amount = validAmount,
                amountError = validateAmount(validAmount)
            )
        }
    }
    
    fun updateTransactionType(type: TransactionType) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                transactionType = type,
                category = when (type) {
                    TransactionType.INCOME -> "Income"
                    TransactionType.EXPENSE -> "Others"
                    TransactionType.INVESTMENT -> "Investment"
                    TransactionType.CREDIT -> "Shopping"
                    else -> currentState.category
                },
                budgetImpactType = if (type != TransactionType.INCOME) null else currentState.budgetImpactType,
                budgetCategory = if (type != TransactionType.INCOME) null else currentState.budgetCategory,
                // The To account only applies to a transfer — drop it when leaving
                // TRANSFER so a stale selection can't silently pre-fill a later one.
                toAccount = if (type == TransactionType.TRANSFER) currentState.toAccount else null
            )
        }
    }
    
    fun updateTransactionMerchant(merchant: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                merchant = merchant,
                merchantError = validateMerchant(merchant)
            )
        }
    }
    
    fun updateTransactionCategory(category: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                category = category,
                categoryError = validateCategory(category)
            )
        }
    }
    
    fun updateTransactionDate(dateMillis: Long) {
        val instant = Instant.ofEpochMilli(dateMillis)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val currentTime = _transactionUiState.value.date.toLocalTime()
        val newDateTime = LocalDateTime.of(localDate, currentTime)
        
        _transactionUiState.update { currentState ->
            currentState.copy(date = newDateTime)
        }
    }
    
    fun updateTransactionTime(hour: Int, minute: Int) {
        val currentDate = _transactionUiState.value.date.toLocalDate()
        val newDateTime = currentDate.atTime(hour, minute)
        
        _transactionUiState.update { currentState ->
            currentState.copy(date = newDateTime)
        }
    }
    
    fun updateTransactionNotes(notes: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(notes = notes)
        }
    }

    fun addTransactionTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _transactionUiState.update { currentState ->
            if (currentState.tags.any { it.equals(trimmed, ignoreCase = true) }) currentState
            else currentState.copy(tags = currentState.tags + trimmed)
        }
    }

    fun removeTransactionTag(tag: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(tags = currentState.tags.filterNot { it.equals(tag, ignoreCase = true) })
        }
    }
    
    fun updateTransactionRecurring(isRecurring: Boolean) {
        _transactionUiState.update { currentState ->
            currentState.copy(isRecurring = isRecurring)
        }
    }

    fun updateTransactionCurrency(currency: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(currency = currency)
        }
    }

    fun updateReceiptUri(uri: Uri?) {
        _transactionUiState.update { it.copy(receiptUri = uri) }
    }

    fun createCameraUri(): Uri = receiptManager.createCameraUri()
    
    fun updateBudgetImpactType(type: BudgetImpactType?) {
        _transactionUiState.update { it.copy(budgetImpactType = type, budgetCategory = if (type == null) null else it.budgetCategory) }
    }

    fun updateBudgetCategory(category: String?) {
        _transactionUiState.update { it.copy(budgetCategory = category) }
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        val state = _transactionUiState.value
        val isTransfer = state.transactionType == TransactionType.TRANSFER

        val amountError = validateAmount(state.amount)
        // Transfers have no merchant/category to validate.
        val merchantError = if (isTransfer) null else validateMerchant(state.merchant)
        val categoryError = if (isTransfer) null else validateCategory(state.category)

        if (amountError != null || merchantError != null || categoryError != null) {
            _transactionUiState.update { currentState ->
                currentState.copy(
                    amountError = amountError,
                    merchantError = merchantError,
                    categoryError = categoryError
                )
            }
            return
        }

        if (isTransfer) {
            val from = state.selectedAccount
            val to = state.toAccount
            when {
                from == null || to == null ->
                    _transactionUiState.update { it.copy(error = "Select both a From and To account") }
                from.id == to.id ->
                    _transactionUiState.update { it.copy(error = "From and To accounts must be different") }
                from.currency != to.currency ->
                    _transactionUiState.update { it.copy(error = "Both accounts must use the same currency") }
                else -> saveTransferInternal(state, from, to, onSuccess)
            }
            return
        }

        viewModelScope.launch {
            try {
                _transactionUiState.update { it.copy(isLoading = true) }

                val amount = BigDecimal(state.amount)
                val selectedAccount = state.selectedAccount

                val receiptPath = state.receiptUri?.let { receiptManager.saveReceipt(it) }

                addTransactionUseCase.execute(
                    amount = amount,
                    merchant = state.merchant.trim(),
                    category = state.category,
                    type = state.transactionType,
                    date = state.date,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    tags = state.tags,
                    isRecurring = state.isRecurring,
                    bankName = selectedAccount?.bankName,
                    accountLast4 = selectedAccount?.accountLast4,
                    currency = state.currency,
                    receiptPath = receiptPath,
                    budgetCategory = state.budgetCategory,
                    budgetImpactType = state.budgetImpactType
                )

                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(appContext)

                onSuccess()
            } catch (e: Exception) {
                _transactionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save transaction"
                    )
                }
            }
        }
    }

    private fun saveTransferInternal(
        state: TransactionUiState,
        from: AccountBalanceEntity,
        to: AccountBalanceEntity,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                _transactionUiState.update { it.copy(isLoading = true, error = null) }

                addTransactionUseCase.executeTransfer(
                    amount = BigDecimal(state.amount),
                    date = state.date,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    tags = state.tags,
                    currency = from.currency,
                    fromBankName = from.bankName,
                    fromLast4 = from.accountLast4,
                    toBankName = to.bankName,
                    toLast4 = to.accountLast4
                )

                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(appContext)

                onSuccess()
            } catch (e: Exception) {
                _transactionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save transfer"
                    )
                }
            }
        }
    }
    
    // Subscription Tab Functions
    fun updateSubscriptionService(service: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                serviceName = service,
                serviceError = if (service.isBlank()) "Service name is required" else null
            )
        }
    }
    
    fun updateSubscriptionAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val decimalCount = filtered.count { it == '.' }
        val validAmount = if (decimalCount <= 1) filtered else _subscriptionUiState.value.amount
        
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                amount = validAmount,
                amountError = validateAmount(validAmount)
            )
        }
    }
    
    fun updateSubscriptionBillingCycle(cycle: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                billingCycle = cycle,
                billingCycleError = null
            )
        }
    }

    /** Toggle Income / Expense for the recurring entry (#371). */
    fun updateSubscriptionDirection(
        direction: com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
    ) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(direction = direction)
        }
    }
    
    fun updateSubscriptionNextPaymentDate(dateMillis: Long) {
        val instant = Instant.ofEpochMilli(dateMillis)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        
        _subscriptionUiState.update { currentState ->
            currentState.copy(nextPaymentDate = localDate)
        }
    }
    
    fun updateSubscriptionCategory(category: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                category = category,
                categoryError = validateCategory(category)
            )
        }
    }
    
    fun updateSubscriptionNotes(notes: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(notes = notes)
        }
    }

    fun updateSubscriptionCurrency(currency: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(currency = currency)
        }
    }

    fun updateSubscriptionAccount(account: AccountBalanceEntity?) {
        _subscriptionUiState.update { currentState ->
            // Keep the subscription currency aligned with the chosen account so
            // the two figures the user sees can't silently disagree.
            currentState.copy(
                selectedAccount = account,
                currency = account?.currency ?: currentState.currency
            )
        }
    }
    
    fun saveSubscription(onSuccess: () -> Unit) {
        val state = _subscriptionUiState.value
        Log.d("AddViewModel", "saveSubscription called with state: $state")
        
        // Validate all fields
        val serviceError = if (state.serviceName.isBlank()) "Service name is required" else null
        val amountError = validateAmount(state.amount)
        val categoryError = validateCategory(state.category)
        
        Log.d("AddViewModel", "Validation - serviceError: $serviceError, amountError: $amountError, categoryError: $categoryError")
        
        if (serviceError != null || amountError != null || categoryError != null) {
            _subscriptionUiState.update { currentState ->
                currentState.copy(
                    serviceError = serviceError,
                    amountError = amountError,
                    categoryError = categoryError
                )
            }
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("AddViewModel", "Starting to save subscription...")
                _subscriptionUiState.update { it.copy(isLoading = true) }
                
                val amount = BigDecimal(state.amount)
                Log.d("AddViewModel", "Calling addSubscriptionUseCase.execute with: " +
                    "merchantName=${state.serviceName.trim()}, amount=$amount, " +
                    "nextPaymentDate=${state.nextPaymentDate}, billingCycle=${state.billingCycle}, " +
                    "category=${state.category}")
                
                val subscriptionId = addSubscriptionUseCase.execute(
                    merchantName = state.serviceName.trim(),
                    amount = amount,
                    nextPaymentDate = state.nextPaymentDate,
                    billingCycle = state.billingCycle,
                    category = state.category,
                    autoRenewal = false, // Not implemented yet
                    paymentReminder = false, // Not implemented yet
                    notes = state.notes.takeIf { it.isNotBlank() },
                    currency = state.currency,
                    direction = state.direction,
                    bankName = state.selectedAccount?.bankName,
                    accountLast4 = state.selectedAccount?.accountLast4
                )
                
                Log.d("AddViewModel", "Subscription saved successfully with ID: $subscriptionId")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AddViewModel", "Error saving subscription", e)
                e.printStackTrace()
                _subscriptionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save subscription"
                    )
                }
            } finally {
                _subscriptionUiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    
    // Validation helpers
    private fun validateAmount(amount: String): String? {
        return when {
            amount.isBlank() -> "Amount is required"
            amount.toDoubleOrNull() == null -> "Invalid amount"
            amount.toDouble() <= 0 -> "Amount must be greater than 0"
            else -> null
        }
    }
    
    private fun validateMerchant(merchant: String): String? {
        return when {
            merchant.isBlank() -> "Merchant/Description is required"
            merchant.length < 2 -> "Too short"
            else -> null
        }
    }
    
    private fun validateCategory(category: String): String? {
        return when {
            category.isBlank() -> "Category is required"
            else -> null
        }
    }
}

// UI State Classes
data class AddUiState(
    val currentTab: Int = 0
)

data class TransactionUiState(
    val amount: String = "",
    val amountError: String? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val merchant: String = "",
    val merchantError: String? = null,
    val category: String = "Others",
    val categoryError: String? = null,
    val date: LocalDateTime = LocalDateTime.now(),
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val isRecurring: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAccount: AccountBalanceEntity? = null,
    // For a TRANSFER, [selectedAccount] is the FROM account and this is the TO
    // account. Unused for all other transaction types.
    val toAccount: AccountBalanceEntity? = null,
    val currency: String = "INR",
    val receiptUri: Uri? = null,
    val budgetImpactType: BudgetImpactType? = null,
    val budgetCategory: String? = null
) {
    val isValid: Boolean
        get() {
            val amountOk = amount.isNotBlank() &&
                    amount.toDoubleOrNull() != null &&
                    amount.toDouble() > 0 &&
                    amountError == null
            if (!amountOk) return false

            if (transactionType == TransactionType.TRANSFER) {
                // Transfers move money between two distinct accounts of the same
                // currency (we never sum across currencies). No merchant needed.
                val from = selectedAccount ?: return false
                val to = toAccount ?: return false
                return from.id != to.id && from.currency == to.currency
            }

            return merchant.isNotBlank() &&
                    category.isNotBlank() &&
                    merchantError == null &&
                    categoryError == null &&
                    (budgetImpactType == null || budgetCategory != null)
        }
}

data class SubscriptionUiState(
    val serviceName: String = "",
    val serviceError: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val billingCycle: String = "Monthly",
    val billingCycleError: String? = null,
    val nextPaymentDate: LocalDate = LocalDate.now().plusMonths(1),
    val category: String = "Subscriptions",
    val categoryError: String? = null,
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val currency: String = "INR",
    /**
     * Income vs Expense (#371). Income subscriptions get phantom-created
     * on schedule (wallet top-ups etc.); expense subscriptions match
     * incoming bank-debit SMS as today.
     */
    val direction: com.pennywiseai.tracker.data.database.entity.SubscriptionDirection =
        com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.EXPENSE,
    /**
     * Optional funding account (#570). When set, mark-as-paid / scheduled
     * income moves this account's balance; null keeps the subscription
     * unlinked ("Manual Entry", no balance tracking).
     */
    val selectedAccount: AccountBalanceEntity? = null
) {
    val isValid: Boolean
        get() = serviceName.isNotBlank() &&
                amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                amount.toDouble() > 0 &&
                billingCycle.isNotBlank() &&
                category.isNotBlank() &&
                serviceError == null &&
                amountError == null &&
                categoryError == null
}
