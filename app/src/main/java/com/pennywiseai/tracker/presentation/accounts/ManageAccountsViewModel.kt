package com.pennywiseai.tracker.presentation.accounts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.domain.model.toDatabaseString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

data class ManageAccountsUiState(
    val accounts: List<AccountBalanceEntity> = emptyList(),
    val hiddenAccounts: Set<String> = emptySet(),
    val balanceHistory: List<AccountBalanceEntity> = emptyList(),
    val linkedCards: Map<String, List<CardEntity>> = emptyMap(), // accountLast4 -> List of cards
    val orphanedCards: List<CardEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class AccountFormState(
    val bankName: String = "",
    val accountLast4: String = "",
    val balance: String = "",
    val creditLimit: String = "",
    val accountType: AccountType = AccountType.SAVINGS,
    val currency: String = "INR",
    val isValid: Boolean = false,
    val errorMessage: String? = null
)

enum class AccountType {
    SAVINGS,
    CURRENT,
    CREDIT,
    CASH
}

data class PendingProfileReassign(
    val bankName: String,
    val accountLast4: String,
    val profileId: Long,
    val transactionCount: Int
)

@HiltViewModel
class ManageAccountsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val transactionRepository: com.pennywiseai.tracker.data.repository.TransactionRepository,
    private val database: com.pennywiseai.tracker.data.database.PennyWiseDatabase,
    private val userPreferencesRepository: com.pennywiseai.tracker.data.preferences.UserPreferencesRepository,
    entitlementGate: com.pennywiseai.tracker.billing.EntitlementGate,
) : ViewModel() {

    /**
     * Drives the Merge-accounts action — Pro-only gate. Free users still
     * see the icon (so they discover the feature) but tapping it opens
     * the paywall instead of the merge sheet.
     */
    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled
    
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ManageAccountsUiState())
    val uiState: StateFlow<ManageAccountsUiState> = _uiState.asStateFlow()
    
    private val _formState = MutableStateFlow(AccountFormState())
    val formState: StateFlow<AccountFormState> = _formState.asStateFlow()

    private val _pendingProfileReassign = MutableStateFlow<PendingProfileReassign?>(null)
    val pendingProfileReassign: StateFlow<PendingProfileReassign?> = _pendingProfileReassign.asStateFlow()
    
    /** User's base currency — the default for a new manual account. */
    private var baseCurrency: String = "INR"

    init {
        loadAccounts()
        loadHiddenAccounts()
        loadCards()
        viewModelScope.launch {
            baseCurrency = userPreferencesRepository.baseCurrency.first()
            // Seed the (still-empty) add form with the base currency.
            _formState.update { if (it.bankName.isBlank()) it.copy(currency = baseCurrency) else it }
        }
    }

    fun updateCurrency(currency: String) {
        _formState.update { it.copy(currency = currency) }
    }
    
    private fun loadAccounts() {
        viewModelScope.launch {
            accountBalanceRepository.getAllLatestBalances()
                .collect { accounts ->
                    _uiState.update { it.copy(accounts = accounts) }
                }
        }
    }
    
    private fun loadCards() {
        viewModelScope.launch {
            cardRepository.getAllCards().collect { allCards ->
                // Group cards by linked account
                val linkedCardsMap = mutableMapOf<String, MutableList<CardEntity>>()
                val orphaned = mutableListOf<CardEntity>()
                
                for (card in allCards) {
                    when {
                        card.cardType == CardType.DEBIT && card.accountLast4 != null -> {
                            linkedCardsMap.getOrPut(card.accountLast4) { mutableListOf() }.add(card)
                        }
                        card.cardType == CardType.DEBIT && card.accountLast4 == null -> {
                            orphaned.add(card)
                        }
                        // Credit cards are not orphaned, they're standalone
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        linkedCards = linkedCardsMap,
                        orphanedCards = orphaned
                    )
                }
            }
        }
    }
    
    private fun loadHiddenAccounts() {
        val hidden = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
        _uiState.update { it.copy(hiddenAccounts = hidden) }
    }
    
    fun updateBankName(name: String) {
        _formState.update {
            it.copy(
                bankName = name,
                isValid = validateForm(name, it.accountLast4, it.balance, it.accountType)
            )
        }
    }
    
    fun updateAccountLast4(last4: String) {
        val isCash = _formState.value.accountType == AccountType.CASH
        // Allow empty for CASH, or up to 4 chars for others
        if (isCash || last4.length <= 4) {
            _formState.update {
                it.copy(
                    accountLast4 = if (isCash && last4.isEmpty()) "CASH" else last4,
                    isValid = validateForm(it.bankName, if (isCash && last4.isEmpty()) "CASH" else last4, it.balance, it.accountType)
                )
            }
        }
    }
    
    fun updateBalance(balance: String) {
        // Only allow valid numeric input
        if (balance.isEmpty() || balance.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update {
                it.copy(
                    balance = balance,
                    isValid = validateForm(it.bankName, it.accountLast4, balance, it.accountType)
                )
            }
        }
    }
    
    fun updateCreditLimit(limit: String) {
        // Only allow valid numeric input
        if (limit.isEmpty() || limit.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { it.copy(creditLimit = limit) }
        }
    }
    
    fun updateAccountType(type: AccountType) {
        _formState.update { it.copy(accountType = type) }
    }
    
    private fun validateForm(bankName: String, last4: String, balance: String, accountType: AccountType = AccountType.SAVINGS): Boolean {
        val isCash = accountType == AccountType.CASH
        return bankName.isNotBlank() &&
               (isCash || last4.length == 4) &&  // CASH accounts: last4 can be "CASH" default
               balance.isNotBlank() &&
               balance.toDoubleOrNull() != null
    }
    
    fun addAccount() {
        val state = _formState.value
        if (!state.isValid) return
        
        viewModelScope.launch {
            // Check for duplicates
            val existingAccount = accountBalanceRepository.getLatestBalance(
                state.bankName, 
                state.accountLast4
            )
            
            if (existingAccount != null) {
                _formState.update { it.copy(errorMessage = "Account already exists") }
                return@launch
            }
            
            // Add the account
            val creditLimit = if (state.accountType == AccountType.CREDIT && state.creditLimit.isNotBlank()) {
                BigDecimal(state.creditLimit)
            } else null
            
            val isCredit = state.accountType == AccountType.CREDIT
            val template = AccountBalanceEntity(
                bankName = state.bankName,
                accountLast4 = state.accountLast4,
                balance = BigDecimal(state.balance),
                creditLimit = creditLimit,
                timestamp = LocalDateTime.now(),
                isCreditCard = isCredit,
                accountType = state.accountType.toDatabaseString(),
                currency = state.currency,
                sourceType = "MANUAL"
            )
            if (isCredit) {
                // Credit cards aren't recompute-managed — single snapshot as before.
                accountBalanceRepository.insertBalance(template)
            } else {
                // Cash/regular manual account: seed an OPENING anchor + current row so
                // its balance derives from opening + Σ(transactions) going forward.
                accountBalanceRepository.seedManualAccount(template, BigDecimal(state.balance))
            }

            // Clear form (keep the base currency as the next default)
            _formState.value = AccountFormState(currency = baseCurrency)
        }
    }
    
    fun updateAccountBalance(bankName: String, accountLast4: String, newBalance: BigDecimal) {
        viewModelScope.launch {
            // For a manual/cash account the balance is derived (opening + Σtxns). The
            // user types their *current* balance, so back-solve the opening (option b)
            // — future transactions still adjust from there.
            if (accountBalanceRepository.isManualAccount(bankName, accountLast4)) {
                accountBalanceRepository.setManualCurrentBalance(bankName, accountLast4, newBalance)
                return@launch
            }

            // Get the latest balance to preserve existing account properties
            val latestBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)

            accountBalanceRepository.insertBalance(
                AccountBalanceEntity(
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    balance = newBalance,
                    creditLimit = latestBalance?.creditLimit,
                    isCreditCard = latestBalance?.isCreditCard ?: false,
                    // Reinserted rows are stamped MANUAL (so a rescan won't purge a
                    // user-edited balance), but that makes resolveAccountCurrency trust
                    // the stored value. Resolve it first so an SMS-tracked non-INR
                    // account keeps its parser currency instead of flipping to the
                    // stored INR default. See [CurrencyFormatter.resolveAccountCurrency].
                    currency = CurrencyFormatter.resolveAccountCurrency(
                        sourceType = latestBalance?.sourceType,
                        storedCurrency = latestBalance?.currency ?: "INR",
                        bankName = bankName
                    ),
                    accountType = latestBalance?.accountType,
                    profileId = latestBalance?.profileId ?: ProfileEntity.PERSONAL_ID,
                    alias = latestBalance?.alias,
                    sourceType = "MANUAL",
                    timestamp = LocalDateTime.now()
                )
            )
        }
    }
    
    fun updateCreditCard(bankName: String, accountLast4: String, newBalance: BigDecimal, newLimit: BigDecimal) {
        viewModelScope.launch {
            val latestBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)

            accountBalanceRepository.insertBalance(
                AccountBalanceEntity(
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    balance = newBalance,
                    creditLimit = newLimit,
                    timestamp = LocalDateTime.now(),
                    isCreditCard = true,
                    // Keep the resolved currency (see updateAccountBalance) so stamping
                    // MANUAL doesn't flip an SMS-tracked non-INR card to stored INR.
                    currency = CurrencyFormatter.resolveAccountCurrency(
                        sourceType = latestBalance?.sourceType,
                        storedCurrency = latestBalance?.currency ?: "INR",
                        bankName = bankName
                    ),
                    accountType = latestBalance?.accountType,
                    profileId = latestBalance?.profileId ?: ProfileEntity.PERSONAL_ID,
                    alias = latestBalance?.alias,
                    sourceType = "MANUAL"
                )
            )
        }
    }
    
    fun setStatementDay(bankName: String, accountLast4: String, day: Int?) {
        viewModelScope.launch {
            accountBalanceRepository.updateStatementDay(bankName, accountLast4, day)
        }
    }

    fun toggleAccountVisibility(bankName: String, accountLast4: String) {
        val key = "${bankName}_${accountLast4}"
        val hidden = _uiState.value.hiddenAccounts.toMutableSet()
        
        if (hidden.contains(key)) {
            hidden.remove(key)
        } else {
            hidden.add(key)
        }
        
        // Save to SharedPreferences
        sharedPrefs.edit().putStringSet("hidden_accounts", hidden).apply()
        
        // Update UI state
        _uiState.update { it.copy(hiddenAccounts = hidden) }
    }
    
    fun isAccountHidden(bankName: String, accountLast4: String): Boolean {
        val key = "${bankName}_${accountLast4}"
        return _uiState.value.hiddenAccounts.contains(key)
    }
    
    fun clearError() {
        _formState.update { it.copy(errorMessage = null) }
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun loadBalanceHistory(bankName: String, accountLast4: String) {
        viewModelScope.launch {
            val history = accountBalanceRepository.getBalanceHistoryForAccount(bankName, accountLast4)
            _uiState.update { it.copy(balanceHistory = history) }
        }
    }
    
    fun deleteBalanceRecord(id: Long, bankName: String, accountLast4: String) {
        viewModelScope.launch {
            // Check if this is the only record
            val count = accountBalanceRepository.getBalanceCountForAccount(bankName, accountLast4)
            if (count > 1) {
                accountBalanceRepository.deleteBalanceById(id)
                // Reload history and accounts
                loadBalanceHistory(bankName, accountLast4)
                loadAccounts()
            }
        }
    }
    
    fun updateBalanceRecord(id: Long, newBalance: BigDecimal, bankName: String, accountLast4: String) {
        viewModelScope.launch {
            accountBalanceRepository.updateBalanceById(id, newBalance)
            // Reload history and accounts
            loadBalanceHistory(bankName, accountLast4)
            loadAccounts()
        }
    }
    
    fun clearBalanceHistory() {
        _uiState.update { it.copy(balanceHistory = emptyList()) }
    }
    
    fun linkCardToAccount(cardId: Long, accountLast4: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ManageAccountsViewModel", "Starting to link card $cardId to account $accountLast4")
                
                // Get card info first to know if there's a balance to copy
                val card = cardRepository.getCardById(cardId)
                val hasBalance = card?.lastBalance != null
                
                // Link the card to the account
                cardRepository.linkCardToAccount(cardId, accountLast4)
                
                // If card had a balance, copy it to the account
                if (card != null && hasBalance) {
                    try {
                        val insertedId = accountBalanceRepository.insertBalanceUpdate(
                            bankName = card.bankName,
                            accountLast4 = accountLast4,
                            balance = card.lastBalance!!,
                            timestamp = card.lastBalanceDate ?: LocalDateTime.now(),
                            smsSource = card.lastBalanceSource,
                            sourceType = "CARD_LINK"
                        )
                        android.util.Log.d("ManageAccountsViewModel", "Balance copied to account. Insert ID: $insertedId")
                        
                        // Show success message with balance
                        val message = "Card linked successfully. Balance updated to ${CurrencyFormatter.formatCurrency(card.lastBalance, card.currency)}"
                        _uiState.update { it.copy(successMessage = message) }
                    } catch (e: Exception) {
                        android.util.Log.e("ManageAccountsViewModel", "Failed to copy balance: ${e.message}", e)
                        // Still show success for linking, but note the balance issue
                        _uiState.update { it.copy(successMessage = "Card linked successfully (balance update failed)") }
                    }
                } else {
                    // No balance to copy, just show link success
                    _uiState.update { it.copy(successMessage = "Card linked successfully") }
                }
                
                // Clear message after delay
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }
                
                loadCards() // Reload cards to update UI
                loadAccounts() // Reload accounts to show new balance
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to link card", e)
                _uiState.update { 
                    it.copy(errorMessage = "Failed to link card: ${e.message}")
                }
            }
        }
    }
    
    fun unlinkCard(cardId: Long) {
        viewModelScope.launch {
            cardRepository.unlinkCard(cardId)
            loadCards() // Reload cards to update UI
        }
    }
    
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ManageAccountsViewModel", "Deleting card with ID: $cardId")
                cardRepository.deleteCard(cardId)
                _uiState.update { it.copy(successMessage = "Card deleted successfully") }
                
                // Clear message after delay
                delay(2000)
                _uiState.update { it.copy(successMessage = null) }
                
                loadCards() // Reload cards to update UI
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to delete card", e)
                _uiState.update { 
                    it.copy(errorMessage = "Failed to delete card: ${e.message}")
                }
            }
        }
    }
    
    fun setCardActive(cardId: Long, isActive: Boolean) {
        viewModelScope.launch {
            cardRepository.setCardActive(cardId, isActive)
            loadCards() // Reload cards to update UI
        }
    }

    /**
     * Overrides the parser's auto-detected card metadata. Used when a card
     * has been wrongly classified (e.g. detected as DEBIT when it's really
     * a credit card, or filed under the wrong bank). Nickname is purely
     * cosmetic but lives on the same dialog because the user already has
     * the edit sheet open.
     *
     * Trimming + null-on-blank for nickname keeps the entity column tidy
     * (empty strings would otherwise leak through to the UI).
     */
    fun updateCardDetails(
        cardId: Long,
        bankName: String,
        cardType: CardType,
        nickname: String?
    ) {
        viewModelScope.launch {
            try {
                val existing = cardRepository.getCardById(cardId)
                if (existing == null) {
                    // The card was removed from another session between the
                    // dialog opening and Save. Surface it so the user isn't
                    // left wondering why nothing happened.
                    _uiState.update {
                        it.copy(errorMessage = "Card no longer exists — it may have been deleted.")
                    }
                    return@launch
                }
                // updatedAt is stamped inside CardRepository.updateCard, so
                // we leave it alone here.
                cardRepository.updateCard(
                    existing.copy(
                        bankName = bankName.trim(),
                        cardType = cardType,
                        nickname = nickname?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
                _uiState.update { it.copy(successMessage = "Card updated") }
                delay(2000)
                _uiState.update { it.copy(successMessage = null) }
                loadCards()
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to update card", e)
                _uiState.update {
                    it.copy(errorMessage = "Failed to update card: ${e.message}")
                }
            }
        }
    }

    fun deleteAccount(bankName: String, accountLast4: String) {
        viewModelScope.launch {
            try {
                // Unlink any cards linked to this account
                val linkedCards = _uiState.value.linkedCards[accountLast4] ?: emptyList()
                linkedCards.forEach { card ->
                    cardRepository.unlinkCard(card.id)
                }

                // Delete all balance records for this account
                val deletedCount = accountBalanceRepository.deleteAccount(bankName, accountLast4)

                // Remove from hidden accounts if present
                val key = "${bankName}_${accountLast4}"
                val hidden = _uiState.value.hiddenAccounts.toMutableSet()
                hidden.remove(key)
                sharedPrefs.edit().putStringSet("hidden_accounts", hidden).apply()

                _uiState.update {
                    it.copy(
                        hiddenAccounts = hidden,
                        successMessage = "Account deleted successfully ($deletedCount balance records removed)"
                    )
                }

                // Clear message after delay
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }

                loadCards() // Reload cards to update UI
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to delete account: ${e.message}")
                }
            }
        }
    }

    /**
     * Number of (non-deleted) transactions currently on [bankName]/[accountLast4].
     * Used to populate the merge confirmation dialog ("Move N transactions to …").
     */
    suspend fun countTransactionsOn(bankName: String, accountLast4: String): Int =
        transactionRepository.countByAccount(bankName, accountLast4)

    /**
     * Merge [source] into [target] (#368): re-target every transaction from
     * source to target, then drop the source's balance rows. Validates first
     * that the two accounts are compatible (same currency, same `isCreditCard`)
     * and that they aren't the same account. Caller should pre-call
     * [countTransactionsOn] for the confirmation dialog.
     *
     * One-way for v1 — no undo. Run inside a single coroutine so the
     * partial-merge window is short.
     */
    fun mergeAccounts(
        source: AccountBalanceEntity,
        target: AccountBalanceEntity
    ) {
        viewModelScope.launch {
            try {
                val sameAccount = source.bankName.equals(target.bankName, ignoreCase = true) &&
                    source.accountLast4 == target.accountLast4
                if (sameAccount) {
                    _uiState.update { it.copy(errorMessage = "Source and target are the same account") }
                    return@launch
                }
                if (!source.currency.equals(target.currency, ignoreCase = true)) {
                    _uiState.update {
                        it.copy(errorMessage = "Currencies don't match (${source.currency} vs ${target.currency})")
                    }
                    return@launch
                }
                if (source.isCreditCard != target.isCreditCard) {
                    _uiState.update {
                        it.copy(errorMessage = "Can't merge a credit card with a regular account")
                    }
                    return@launch
                }

                // Run the full merge under a single Room transaction so a
                // crash mid-flight can't leave partially-merged state (e.g.
                // transactions retargeted but source balances still around,
                // or vice versa). The source account effectively ceases to
                // exist after this block commits.
                val moved = database.withTransaction {
                    val rows = transactionRepository.mergeAccountTransactions(
                        sourceBankName = source.bankName,
                        sourceAccountLast4 = source.accountLast4,
                        targetBankName = target.bankName,
                        targetAccountLast4 = target.accountLast4
                    )
                    // Self-transfer rows (#385) reference accounts via
                    // `fromAccount` / `toAccount`. Re-target any references
                    // to the source so the detail screen's From → To stays
                    // pointing at a live account.
                    transactionRepository.retargetTransferLegRefs(
                        sourceAccountLast4 = source.accountLast4,
                        targetAccountLast4 = target.accountLast4
                    )
                    // Re-link any debit cards bound to the source so they
                    // keep working against the merged-into target. (Unlinking
                    // would silently strip the user's card→account binding.)
                    (_uiState.value.linkedCards[source.accountLast4] ?: emptyList())
                        .forEach { card ->
                            cardRepository.linkCardToAccount(card.id, target.accountLast4)
                        }
                    // Drop the source's balance snapshots. Source's running
                    // balance was for source's standalone account, so
                    // retargeting these snapshots into the target would
                    // invent fictional history. Dropping is the only correct
                    // option; the merged transactions appear on the target
                    // without a historical balance trace.
                    accountBalanceRepository.deleteAccount(source.bankName, source.accountLast4)
                    rows
                }

                // Clear any "hidden" preference for the now-gone source.
                val key = "${source.bankName}_${source.accountLast4}"
                val hidden = _uiState.value.hiddenAccounts.toMutableSet()
                if (hidden.remove(key)) {
                    sharedPrefs.edit().putStringSet("hidden_accounts", hidden).apply()
                    _uiState.update { it.copy(hiddenAccounts = hidden) }
                }

                _uiState.update {
                    it.copy(
                        successMessage = "Merged $moved transactions into ${target.bankName} ••${target.accountLast4}"
                    )
                }
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }
                loadCards()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Merge failed: ${e.message}") }
            }
        }
    }

    fun setAccountProfile(bankName: String, accountLast4: String, profileId: Long) {
        viewModelScope.launch {
            try {
                accountBalanceRepository.setAccountProfile(bankName, accountLast4, profileId)

                // Offer to move EXISTING transactions that carry an explicit, mismatched
                // profile (NULL/dynamic ones already follow the account, so they're left alone).
                val count = transactionRepository.countExplicitProfileMismatchForAccount(
                    bankName,
                    accountLast4,
                    profileId
                )
                if (count > 0) {
                    _pendingProfileReassign.value = PendingProfileReassign(
                        bankName = bankName,
                        accountLast4 = accountLast4,
                        profileId = profileId,
                        transactionCount = count
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to set account profile", e)
                _uiState.update { it.copy(errorMessage = "Failed to update account profile: ${e.message}") }
            }
        }
    }

    /**
     * Sets (or clears) the friendly display name for an account. A blank
     * alias is stored as NULL so the UI falls back to the bank/last-4 format.
     */
    fun setAccountAlias(bankName: String, accountLast4: String, alias: String?) {
        viewModelScope.launch {
            try {
                val normalized = alias?.trim()?.takeIf { it.isNotEmpty() }
                accountBalanceRepository.setAccountAlias(bankName, accountLast4, normalized)
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to set account alias", e)
                _uiState.update { it.copy(errorMessage = "Failed to rename account: ${e.message}") }
            }
        }
    }

    /** Sets (or clears, with null) the per-account low-balance alert threshold. */
    fun setLowBalanceThreshold(bankName: String, accountLast4: String, threshold: java.math.BigDecimal?) {
        viewModelScope.launch {
            try {
                accountBalanceRepository.setLowBalanceThreshold(bankName, accountLast4, threshold)
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to set low-balance threshold", e)
                _uiState.update { it.copy(errorMessage = "Failed to set alert: ${e.message}") }
            }
        }
    }

    fun applyPendingProfileReassign() {
        val p = _pendingProfileReassign.value ?: return
        viewModelScope.launch {
            try {
                transactionRepository.setProfileForAccountTransactions(p.bankName, p.accountLast4, p.profileId)
            } catch (e: Exception) {
                android.util.Log.e("ManageAccountsViewModel", "Failed to reassign account transactions", e)
                _uiState.update { it.copy(errorMessage = "Failed to move transactions: ${e.message}") }
            } finally {
                // Always clear the prompt so the dialog can't get stuck open if
                // the update throws.
                _pendingProfileReassign.value = null
            }
        }
    }

    fun dismissPendingProfileReassign() {
        _pendingProfileReassign.value = null
    }

    fun editAccount(
        oldBankName: String,
        accountLast4: String,
        newBankName: String,
        newBalance: BigDecimal,
        newCreditLimit: BigDecimal?,
        isCreditCard: Boolean,
        newCurrency: String? = null
    ) {
        viewModelScope.launch {
            try {
                // Update bank name if changed
                if (newBankName != oldBankName) {
                    accountBalanceRepository.updateAccountBankName(oldBankName, accountLast4, newBankName)

                    // Update hidden accounts preference if bank name changed
                    val oldKey = "${oldBankName}_${accountLast4}"
                    val newKey = "${newBankName}_${accountLast4}"
                    val hidden = _uiState.value.hiddenAccounts.toMutableSet()
                    if (hidden.contains(oldKey)) {
                        hidden.remove(oldKey)
                        hidden.add(newKey)
                        sharedPrefs.edit().putStringSet("hidden_accounts", hidden).apply()
                        _uiState.update { it.copy(hiddenAccounts = hidden) }
                    }
                }

                // Get existing balance to preserve profileId and other properties
                val latestBalance = accountBalanceRepository.getLatestBalance(newBankName, accountLast4)
                val resolvedCurrency = newCurrency ?: CurrencyFormatter.resolveAccountCurrency(
                    sourceType = latestBalance?.sourceType,
                    storedCurrency = latestBalance?.currency ?: "INR",
                    bankName = newBankName
                )

                if (!isCreditCard && accountBalanceRepository.isManualAccount(newBankName, accountLast4)) {
                    // Manual/cash account: balance is derived. Atomically update the
                    // currency on its anchor rows and back-solve the opening from the
                    // typed balance (option b) instead of inserting a snapshot the next
                    // recompute would overwrite.
                    accountBalanceRepository.updateManualBalanceAndCurrency(
                        bankName = newBankName,
                        accountLast4 = accountLast4,
                        currency = resolvedCurrency,
                        targetBalance = newBalance
                    )
                } else {
                    // Insert new balance record with updated values
                    accountBalanceRepository.insertBalance(
                        AccountBalanceEntity(
                            bankName = newBankName,
                            accountLast4 = accountLast4,
                            balance = newBalance,
                            creditLimit = newCreditLimit,
                            timestamp = LocalDateTime.now(),
                            isCreditCard = isCreditCard,
                            // Honor an explicit currency edit; otherwise resolve so stamping
                            // MANUAL doesn't flip an SMS-tracked non-INR account to stored INR.
                            currency = resolvedCurrency,
                            accountType = latestBalance?.accountType,
                            profileId = latestBalance?.profileId ?: ProfileEntity.PERSONAL_ID,
                            alias = latestBalance?.alias,
                            sourceType = "MANUAL"
                        )
                    )
                }

                _uiState.update {
                    it.copy(successMessage = "Account updated successfully")
                }

                // Clear message after delay
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to update account: ${e.message}")
                }
            }
        }
    }
}