package com.pennywiseai.tracker.presentation.add

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
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
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
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

    // Transaction Tab Functions
    fun updateSelectedAccount(account: AccountBalanceEntity?) {
        _transactionUiState.update { currentState ->
            currentState.copy(selectedAccount = account)
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
                }
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
    
    fun saveTransaction(onSuccess: () -> Unit) {
        val state = _transactionUiState.value
        
        val amountError = validateAmount(state.amount)
        val merchantError = validateMerchant(state.merchant)
        val categoryError = validateCategory(state.category)
        
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
        
        viewModelScope.launch {
            try {
                _transactionUiState.update { it.copy(isLoading = true) }

                val amount = BigDecimal(state.amount)
                val selectedAccount = state.selectedAccount

                addTransactionUseCase.execute(
                    amount = amount,
                    merchant = state.merchant.trim(),
                    category = state.category,
                    type = state.transactionType,
                    date = state.date,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isRecurring = state.isRecurring,
                    bankName = selectedAccount?.bankName,
                    accountLast4 = selectedAccount?.accountLast4,
                    currency = state.currency
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
                    currency = state.currency
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
    val isRecurring: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAccount: AccountBalanceEntity? = null,
    val currency: String = "INR"
) {
    val isValid: Boolean
        get() = amount.isNotBlank() && 
                amount.toDoubleOrNull() != null && 
                amount.toDouble() > 0 &&
                merchant.isNotBlank() && 
                category.isNotBlank() &&
                amountError == null &&
                merchantError == null &&
                categoryError == null
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
    val currency: String = "INR"
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
