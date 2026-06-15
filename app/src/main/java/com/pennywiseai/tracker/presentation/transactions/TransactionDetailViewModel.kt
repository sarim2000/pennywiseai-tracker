package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.core.net.toUri
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.utils.SmsReportUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val loanRepository: LoanRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val transactionGroupRepository: TransactionGroupRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val receiptManager: ReceiptManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _transaction = MutableStateFlow<TransactionEntity?>(null)
    val transaction: StateFlow<TransactionEntity?> = _transaction.asStateFlow()

    private val _primaryCurrency = MutableStateFlow("INR")
    val primaryCurrency: StateFlow<String> = _primaryCurrency.asStateFlow()

    private val _convertedAmount = MutableStateFlow<BigDecimal?>(null)
    val convertedAmount: StateFlow<BigDecimal?> = _convertedAmount.asStateFlow()

    private val _accountProfileId = MutableStateFlow<Long?>(null)
    val accountProfileId: StateFlow<Long?> = _accountProfileId.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()
    
    private val _editableTransaction = MutableStateFlow<TransactionEntity?>(null)
    val editableTransaction: StateFlow<TransactionEntity?> = _editableTransaction.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _applyToAllFromMerchant = MutableStateFlow(false)
    val applyToAllFromMerchant: StateFlow<Boolean> = _applyToAllFromMerchant.asStateFlow()
    
    private val _updateExistingTransactions = MutableStateFlow(false)
    val updateExistingTransactions: StateFlow<Boolean> = _updateExistingTransactions.asStateFlow()
    
    private val _existingTransactionCount = MutableStateFlow(0)
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()
    val existingTransactionCount: StateFlow<Int> = _existingTransactionCount.asStateFlow()

    // Budget impact state (for INCOME transactions only)
    private val _budgetImpactType = MutableStateFlow<BudgetImpactType?>(null)
    val budgetImpactType: StateFlow<BudgetImpactType?> = _budgetImpactType.asStateFlow()

    private val _budgetCategory = MutableStateFlow<String?>(null)
    val budgetCategory: StateFlow<String?> = _budgetCategory.asStateFlow()

    val activeBudgetCategories: StateFlow<List<String>> = budgetGroupRepository.getActiveGroups()
        .map { groups ->
            groups.map { it.categories.map { cat -> cat.categoryName } }.flatten().distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Transaction group state
    val availableGroups: StateFlow<List<TransactionGroupEntity>> = transactionGroupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentGroup: StateFlow<TransactionGroupEntity?> = _transaction
        .flatMapLatest { tx ->
            val groupId = tx?.groupId ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            transactionGroupRepository.getAllGroups().map { groups -> groups.firstOrNull { it.id == groupId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showGroupSheet = MutableStateFlow(false)
    val showGroupSheet: StateFlow<Boolean> = _showGroupSheet.asStateFlow()

    fun showGroupSheet() { _showGroupSheet.value = true }
    fun hideGroupSheet() { _showGroupSheet.value = false }

    fun addToGroup(groupId: Long) {
        viewModelScope.launch {
            val txId = _transaction.value?.id ?: return@launch
            transactionGroupRepository.addTransactionToGroup(txId, groupId)
            _showGroupSheet.value = false
        }
    }

    fun removeFromGroup() {
        viewModelScope.launch {
            val txId = _transaction.value?.id ?: return@launch
            transactionGroupRepository.removeTransactionFromGroup(txId)
        }
    }

    fun createGroupAndAdd(name: String, note: String?) {
        viewModelScope.launch {
            val txId = _transaction.value?.id ?: return@launch
            transactionGroupRepository.createGroupWithTransaction(name, note, txId)
            _showGroupSheet.value = false
        }
    }

    /**
     * One-tap toggle for "exclude from analytics" (#451). Persists immediately —
     * the transaction stays in history and counts toward the account balance, but
     * spending trends, averages, category/budget breakdowns and AI summaries
     * ignore it.
     */
    fun setExcludedFromAnalytics(excluded: Boolean) {
        viewModelScope.launch {
            val txn = _transaction.value ?: return@launch
            if (txn.excludedFromAnalytics == excluded) return@launch
            transactionRepository.updateTransaction(
                txn.copy(excludedFromAnalytics = excluded, updatedAt = LocalDateTime.now())
            )
            _transaction.value = transactionRepository.getTransactionById(txn.id)
        }
    }

    // Split-related state
    private val _splits = MutableStateFlow<List<SplitItem>>(emptyList())
    val splits: StateFlow<List<SplitItem>> = _splits.asStateFlow()

    private val _originalSplits = MutableStateFlow<List<SplitItem>>(emptyList())

    private val _showSplitEditor = MutableStateFlow(false)
    val showSplitEditor: StateFlow<Boolean> = _showSplitEditor.asStateFlow()

    private val _hasSplits = MutableStateFlow(false)
    val hasSplits: StateFlow<Boolean> = _hasSplits.asStateFlow()
    
    // Categories should be based on transaction type
    val categories: StateFlow<List<CategoryEntity>> = combine(
        _editableTransaction,
        _transaction
    ) { editable, original ->
        val transaction = editable ?: original
        transaction?.transactionType == TransactionType.INCOME
    }.flatMapLatest { isIncome ->
        if (isIncome) {
            categoryRepository.getIncomeCategories()
        } else {
            categoryRepository.getExpenseCategories()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Available accounts for linking (excluding hidden accounts)
    private val sharedPrefs = context.getSharedPreferences("account_prefs", android.content.Context.MODE_PRIVATE)

    val availableAccounts = accountBalanceRepository.getAllLatestBalances()
        .map { balances ->
            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
            balances
                .filter { balance ->
                    val key = "${balance.bankName}_${balance.accountLast4}"
                    !hiddenAccounts.contains(key)
                }
                .map { balance ->
                    AccountInfo(
                        bankName = balance.bankName,
                        accountLast4 = balance.accountLast4,
                        displayName = "${balance.bankName} ••••${balance.accountLast4}",
                        isCreditCard = balance.isCreditCard
                    )
                }
                .distinctBy { "${it.bankName}_${it.accountLast4}" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    data class AccountInfo(
        val bankName: String,
        val accountLast4: String,
        val displayName: String,
        val isCreditCard: Boolean
    )
    
    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            _transaction.value = transaction
            transaction?.let {
                determinePrimaryCurrency(it)
                calculateConvertedAmount(it)
                loadSplits(transactionId)
                loadReceiptUri(it)
                // Clear stale loan state when the (re)loaded transaction has no
                // loan — e.g. the loan was deleted elsewhere and we reload on
                // resume (#444). Otherwise the loan chip would persist.
                val loanId = it.loanId
                if (loanId != null) loadLoan(loanId) else _loan.value = null
                _budgetImpactType.value = it.budgetImpactType
                _budgetCategory.value = it.budgetCategory
                loadAccountProfileId(it)
            }
        }
    }

    private suspend fun loadAccountProfileId(transaction: TransactionEntity) {
        val bankName = transaction.bankName ?: return
        val accountLast4 = transaction.accountNumber ?: return
        val balance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
        _accountProfileId.value = balance?.profileId
    }

    private suspend fun loadSplits(transactionId: Long) {
        val hasSplits = transactionRepository.hasSplits(transactionId)
        _hasSplits.value = hasSplits

        if (hasSplits) {
            transactionRepository.getSplitsForTransaction(transactionId)
                .collect { splitEntities ->
                    val splitItems = splitEntities.map { entity ->
                        SplitItem(
                            id = entity.id,
                            category = entity.category,
                            amount = entity.amount
                        )
                    }
                    _splits.value = splitItems
                    _originalSplits.value = splitItems
                    _showSplitEditor.value = true
                }
        } else {
            _splits.value = emptyList()
            _originalSplits.value = emptyList()
            _showSplitEditor.value = false
        }
    }

    private suspend fun determinePrimaryCurrency(transaction: TransactionEntity) {
        val isUnified = userPreferencesRepository.unifiedCurrencyMode.first()
        val primaryCurrency = if (isUnified) {
            userPreferencesRepository.displayCurrency.first()
        } else {
            val bankName = transaction.bankName
            if (!bankName.isNullOrEmpty()) {
                com.pennywiseai.tracker.utils.CurrencyFormatter.getBankBaseCurrency(bankName)
            } else {
                transaction.currency.takeIf { it.isNotEmpty() } ?: "INR"
            }
        }
        _primaryCurrency.value = primaryCurrency
    }

    private suspend fun calculateConvertedAmount(transaction: TransactionEntity) {
        val primaryCurrency = _primaryCurrency.value
        if (transaction.currency.isNotEmpty() && !transaction.currency.equals(primaryCurrency, ignoreCase = true)) {
            // Convert the amount to the primary currency
            val converted = currencyConversionService.convertAmount(
                amount = transaction.amount,
                fromCurrency = transaction.currency,
                toCurrency = primaryCurrency
            )
            _convertedAmount.value = converted
        } else {
            // No conversion needed if currencies are the same
            _convertedAmount.value = null
        }
    }

    fun enterEditMode() {
        _editableTransaction.value = _transaction.value?.copy()
        _isEditMode.value = true
        _errorMessage.value = null
        _pendingReceiptUri.value = null
        _receiptRemoved.value = false

        // Restore split state from original splits
        if (_hasSplits.value) {
            _splits.value = _originalSplits.value
            _showSplitEditor.value = true
        }

        // Load count of other transactions from same merchant
        _transaction.value?.let { txn ->
            viewModelScope.launch {
                val count = transactionRepository.getOtherTransactionCountForMerchant(
                    txn.merchantName,
                    txn.id
                )
                _existingTransactionCount.value = count
            }
        }
    }
    
    fun exitEditMode() {
        _editableTransaction.value = null
        _isEditMode.value = false
        _errorMessage.value = null
        _applyToAllFromMerchant.value = false
        _updateExistingTransactions.value = false
        _existingTransactionCount.value = 0
        _pendingReceiptUri.value = null
        _receiptRemoved.value = false

        // Reset split state to original values
        _splits.value = _originalSplits.value
        _showSplitEditor.value = _hasSplits.value
    }
    
    fun toggleApplyToAllFromMerchant() {
        _applyToAllFromMerchant.value = !_applyToAllFromMerchant.value
    }
    
    fun toggleUpdateExistingTransactions() {
        _updateExistingTransactions.value = !_updateExistingTransactions.value
    }
    
    fun updateMerchantName(name: String) {
        _editableTransaction.update { current ->
            current?.copy(merchantName = name)
        }
        validateMerchantName(name)
    }
    
    fun updateAmount(amountStr: String) {
        val amount = amountStr.toBigDecimalOrNull()
        if (amount != null && amount > BigDecimal.ZERO) {
            _editableTransaction.update { current ->
                current?.copy(amount = amount)
            }
            _errorMessage.value = null
        } else if (amountStr.isNotEmpty()) {
            _errorMessage.value = "Amount must be a positive number"
        }
    }
    
    fun updateTransactionType(type: TransactionType) {
        if (type != TransactionType.INCOME) {
            _budgetImpactType.value = null
            _budgetCategory.value = null
            _editableTransaction.update { current ->
                current?.copy(transactionType = type, budgetImpactType = null, budgetCategory = null)
            }
        } else {
            _editableTransaction.update { current ->
                current?.copy(transactionType = type)
            }
        }
    }
    
    fun updateCategory(category: String) {
        _editableTransaction.update { current ->
            current?.copy(category = category.ifEmpty { "Others" })
        }
    }
    
    fun updateDateTime(dateTime: LocalDateTime) {
        _editableTransaction.update { current ->
            current?.copy(dateTime = dateTime)
        }
    }
    
    fun updateDescription(description: String?) {
        _editableTransaction.update { current ->
            current?.copy(description = if (description.isNullOrEmpty()) null else description)
        }
    }
    
    fun updateRecurringStatus(isRecurring: Boolean) {
        _editableTransaction.update { current ->
            current?.copy(isRecurring = isRecurring)
        }
    }
    
    fun updateAccountNumber(accountNumber: String?) {
        _editableTransaction.update { current ->
            current?.copy(accountNumber = if (accountNumber.isNullOrEmpty()) null else accountNumber)
        }
    }

    fun updateFromAccount(account: String?) {
        _editableTransaction.update { current ->
            current?.copy(fromAccount = if (account.isNullOrEmpty()) null else account)
        }
    }

    fun updateToAccount(account: String?) {
        _editableTransaction.update { current ->
            current?.copy(toAccount = if (account.isNullOrEmpty()) null else account)
        }
    }

    fun updateProfileId(profileId: Long?) {
        _editableTransaction.update { current ->
            current?.copy(profileId = profileId)
        }
    }

    fun updateBudgetImpactType(type: BudgetImpactType?) {
        _budgetImpactType.value = type
        if (type == null) _budgetCategory.value = null
        _editableTransaction.update { it?.copy(budgetImpactType = type, budgetCategory = if (type == null) null else it.budgetCategory) }
    }

    fun updateBudgetCategory(category: String?) {
        _budgetCategory.value = category
        _editableTransaction.update { it?.copy(budgetCategory = category) }
    }

    fun updateCurrency(currency: String) {
        _editableTransaction.update { current ->
            current?.copy(currency = currency)
        }
        // Recalculate converted amount when currency changes
        _editableTransaction.value?.let { transaction ->
            viewModelScope.launch {
                calculateConvertedAmount(transaction)
            }
        }
    }

    // ========== Split Management Methods ==========

    /**
     * Enables split mode for the current transaction.
     * Creates two initial splits: one with the current category and half the amount,
     * and another with "Others" and the remaining amount.
     */
    fun enableSplitMode() {
        val transaction = _editableTransaction.value ?: _transaction.value ?: return

        // Only allow splits for expenses
        if (transaction.transactionType != TransactionType.EXPENSE) {
            _errorMessage.value = "Splits are only available for expenses"
            return
        }

        val currentCategory = transaction.category
        val totalAmount = transaction.amount
        val halfAmount = totalAmount.divide(BigDecimal(2), 2, java.math.RoundingMode.HALF_UP)
        val remainingAmount = totalAmount - halfAmount

        val initialSplits = listOf(
            SplitItem(id = 0, category = currentCategory, amount = halfAmount),
            SplitItem(id = 0, category = "Others", amount = remainingAmount)
        )

        _splits.value = initialSplits
        _showSplitEditor.value = true
    }

    /**
     * Updates the splits list.
     */
    fun updateSplits(newSplits: List<SplitItem>) {
        _splits.value = newSplits
    }

    /**
     * Removes all splits from the transaction, reverting to single category.
     */
    fun removeSplits() {
        _splits.value = emptyList()
        _showSplitEditor.value = false
        _hasSplits.value = false
    }

    /**
     * Validates that splits sum equals the transaction total (within tolerance).
     * @return true if splits are valid, false otherwise
     */
    fun validateSplits(): Boolean {
        val transaction = _editableTransaction.value ?: _transaction.value ?: return true
        val currentSplits = _splits.value

        if (currentSplits.isEmpty()) return true

        // Minimum 2 splits required
        if (currentSplits.size < 2) {
            _errorMessage.value = "At least 2 splits are required"
            return false
        }

        // All splits must have positive amounts
        if (currentSplits.any { it.amount <= BigDecimal.ZERO }) {
            _errorMessage.value = "All split amounts must be positive"
            return false
        }

        // Splits must sum to transaction total (within 0.01 tolerance)
        val splitsTotal = currentSplits.fold(BigDecimal.ZERO) { acc, split -> acc + split.amount }
        val difference = (transaction.amount - splitsTotal).abs()
        val tolerance = BigDecimal("0.01")

        if (difference > tolerance) {
            _errorMessage.value = "Split amounts must equal the transaction total"
            return false
        }

        return true
    }

    /**
     * Checks if the amount field should be editable.
     * Amount is locked when splits exist.
     */
    fun isAmountEditable(): Boolean {
        return !_showSplitEditor.value || _splits.value.isEmpty()
    }

    fun saveChanges() {
        val toSave = _editableTransaction.value ?: return

        // Validate before saving
        if (toSave.merchantName.isBlank()) {
            _errorMessage.value = "Merchant name is required"
            return
        }

        if (toSave.amount <= BigDecimal.ZERO) {
            _errorMessage.value = "Amount must be positive"
            return
        }

        // Validate splits if present
        if (_showSplitEditor.value && _splits.value.isNotEmpty()) {
            if (!validateSplits()) {
                return
            }
        }

        // Validate self-transfer for TRANSFER transactions
        if (toSave.transactionType == TransactionType.TRANSFER &&
            toSave.fromAccount != null &&
            toSave.toAccount != null &&
            toSave.fromAccount == toSave.toAccount) {
            _errorMessage.value = "Source and destination accounts must be different"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Handle receipt changes
                var newReceiptPath = toSave.receiptPath
                val pendingUri = _pendingReceiptUri.value

                if (_receiptRemoved.value || pendingUri != null) {
                    // Delete old receipt file if it exists
                    toSave.receiptPath?.let { receiptManager.deleteReceipt(it) }
                    newReceiptPath = null
                }

                if (pendingUri != null) {
                    newReceiptPath = receiptManager.saveReceipt(pendingUri)
                }

                // Normalize merchant name before saving
                val normalizedTransaction = toSave.copy(
                    merchantName = normalizeMerchantName(toSave.merchantName),
                    receiptPath = newReceiptPath
                )

                // Pin opening anchors from the PRE-update snapshot for the affected
                // manual accounts, so the recompute after the edit reflects the change
                // (amount/date/account). No-op for SMS accounts.
                _transaction.value?.let { pre ->
                    val pb = pre.bankName; val pa = pre.accountNumber
                    if (pb != null && pa != null) accountBalanceRepository.ensureManualOpening(pb, pa)
                }
                normalizedTransaction.bankName?.let { nb ->
                    normalizedTransaction.accountNumber?.let { na ->
                        accountBalanceRepository.ensureManualOpening(nb, na)
                    }
                }

                transactionRepository.updateTransaction(normalizedTransaction)

                // Update account balance if account was changed or added
                val originalTxn = _transaction.value
                val oldBank = originalTxn?.bankName
                val oldAccount = originalTxn?.accountNumber
                val newBank = normalizedTransaction.bankName
                val newAccount = normalizedTransaction.accountNumber
                val accountChanged = oldBank != newBank || oldAccount != newAccount

                val isTransfer = normalizedTransaction.transactionType == TransactionType.TRANSFER
                val wasTransfer = originalTxn?.transactionType == TransactionType.TRANSFER
                val transferFieldsChanged = (isTransfer || wasTransfer) && (
                    isTransfer != wasTransfer ||
                    originalTxn?.fromAccount != normalizedTransaction.fromAccount ||
                    originalTxn?.toAccount != normalizedTransaction.toAccount ||
                    originalTxn?.amount != normalizedTransaction.amount
                )

                if (transferFieldsChanged) {
                    // Atomically revert the old transfer pair and apply the new
                    // one. When the user converts FROM a TRANSFER to another
                    // type, the new type's single-account effect still needs to
                    // land — handled by the branch below.
                    accountBalanceRepository.applyTransferBalanceShift(
                        original = originalTxn,
                        updated = normalizedTransaction
                    )
                }

                // Single-account update fires for non-TRANSFER edits when the
                // account changed, OR when this save converted a TRANSFER into a
                // non-TRANSFER (so the new type still moves the new account's
                // balance). Skipped entirely when the saved type is TRANSFER —
                // that path is handled atomically above.
                val convertedFromTransfer = wasTransfer && !isTransfer
                val shouldRunSingleAccount = !isTransfer && newBank != null && newAccount != null &&
                    (accountChanged || convertedFromTransfer)

                // Skip the incremental snapshot for manual accounts — the recompute
                // below derives their balance, so this row would be redundant clutter.
                if (shouldRunSingleAccount && !accountBalanceRepository.isManualAccount(newBank!!, newAccount!!)) {
                    val currentBalance = accountBalanceRepository.getLatestBalance(newBank, newAccount)
                    if (currentBalance != null) {
                        val balanceChange = when (normalizedTransaction.transactionType) {
                            TransactionType.INCOME -> normalizedTransaction.amount
                            TransactionType.EXPENSE, TransactionType.CREDIT -> -normalizedTransaction.amount
                            TransactionType.TRANSFER -> BigDecimal.ZERO // handled above
                            TransactionType.INVESTMENT -> -normalizedTransaction.amount
                        }
                        accountBalanceRepository.insertBalance(
                            currentBalance.copy(
                                id = 0,
                                balance = currentBalance.balance + balanceChange,
                                timestamp = normalizedTransaction.dateTime,
                                transactionId = normalizedTransaction.id,
                                sourceType = "TRANSACTION",
                                smsSource = null
                            )
                        )
                    }
                }

                // Manual/cash accounts derive their balance from their transactions, so
                // recompute the affected account(s) — this also covers amount/date edits
                // that don't change the account (the incremental paths above only fire on
                // an account change). No-op for SMS accounts.
                if (newBank != null && newAccount != null) {
                    accountBalanceRepository.recomputeManualBalance(newBank, newAccount)
                }
                if (accountChanged && oldBank != null && oldAccount != null) {
                    accountBalanceRepository.recomputeManualBalance(oldBank, oldAccount)
                }

                // Save or remove splits
                val currentSplits = _splits.value
                if (_showSplitEditor.value && currentSplits.isNotEmpty()) {
                    // Convert SplitItems to entities and save
                    val splitEntities = currentSplits.map { item ->
                        TransactionSplitEntity(
                            id = item.id,
                            transactionId = normalizedTransaction.id,
                            category = item.category,
                            amount = item.amount
                        )
                    }
                    transactionRepository.saveSplits(normalizedTransaction.id, splitEntities)
                    _hasSplits.value = true
                    _originalSplits.value = currentSplits
                } else if (_originalSplits.value.isNotEmpty()) {
                    // Splits were removed, delete them from database
                    transactionRepository.removeSplits(normalizedTransaction.id)
                    _hasSplits.value = false
                    _originalSplits.value = emptyList()
                }

                // Save merchant mapping if checkbox is checked
                if (_applyToAllFromMerchant.value) {
                    merchantMappingRepository.setMapping(
                        normalizedTransaction.merchantName,
                        normalizedTransaction.category
                    )
                }

                // Update existing transactions if checkbox is checked
                if (_updateExistingTransactions.value) {
                    transactionRepository.updateCategoryForMerchant(
                        normalizedTransaction.merchantName,
                        normalizedTransaction.category
                    )
                }

                _transaction.value = normalizedTransaction
                loadReceiptUri(normalizedTransaction)
                _pendingReceiptUri.value = null
                _receiptRemoved.value = false
                _saveSuccess.value = true
                _isEditMode.value = false
                _editableTransaction.value = null
                _errorMessage.value = null
                _applyToAllFromMerchant.value = false
                _updateExistingTransactions.value = false
                _existingTransactionCount.value = 0
                _budgetImpactType.value = normalizedTransaction.budgetImpactType
                _budgetCategory.value = normalizedTransaction.budgetCategory
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save changes: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    fun cancelEdit() {
        exitEditMode()
    }
    
    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
    
    private fun validateMerchantName(name: String) {
        if (name.isBlank()) {
            _errorMessage.value = "Merchant name is required"
        } else {
            _errorMessage.value = null
        }
    }
    
    /**
     * Normalizes merchant name to consistent format.
     * Converts all-caps to proper case, preserves already mixed case.
     */
    private fun normalizeMerchantName(name: String): String {
        val trimmed = name.trim()
        
        // If it's all uppercase, convert to proper case
        return if (trimmed == trimmed.uppercase()) {
            trimmed.lowercase().split(" ").joinToString(" ") { word ->
                if (word.isEmpty()) word else word.substring(0, 1).uppercase() + word.substring(1)
            }
        } else {
            // Already has mixed case, keep as is
            trimmed
        }
    }
    
    fun getReportUrl(): String {
        val txn = _transaction.value ?: return ""
        val smsBody = txn.smsBody ?: "Transaction: ${txn.merchantName} - ${txn.amount}"
        android.util.Log.d("TransactionDetailVM", "Generating report URL for transaction")
        val url = SmsReportUrlBuilder.buildUrl(context, smsBody, txn.smsSender)
        android.util.Log.d("TransactionDetailVM", "Report URL: ${url.take(200)}...")
        return url
    }
    
    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }
    
    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun deleteTransaction() {
        viewModelScope.launch {
            _transaction.value?.let { txn ->
                _isDeleting.value = true
                _showDeleteDialog.value = false

                try {
                    txn.receiptPath?.let { receiptManager.deleteReceipt(it) }
                    // Manual/cash accounts derive their balance from their transactions, so
                    // deleting one must update the balance. Pin the opening from the
                    // pre-delete snapshot, then recompute after. No-op for SMS accounts. (#470)
                    val bank = txn.bankName
                    val acct = txn.accountNumber
                    if (bank != null && acct != null) {
                        accountBalanceRepository.ensureManualOpening(bank, acct)
                    }
                    transactionRepository.deleteTransaction(txn)
                    if (bank != null && acct != null) {
                        accountBalanceRepository.recomputeManualBalance(bank, acct)
                    }
                    _deleteSuccess.value = true
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to delete transaction"
                } finally {
                    _isDeleting.value = false
                }
            }
        }
    }

    // ========== Receipt Management ==========

    private val _receiptUri = MutableStateFlow<Uri?>(null)
    val receiptUri: StateFlow<Uri?> = _receiptUri.asStateFlow()

    private val _pendingReceiptUri = MutableStateFlow<Uri?>(null)
    val pendingReceiptUri: StateFlow<Uri?> = _pendingReceiptUri.asStateFlow()

    private val _receiptRemoved = MutableStateFlow(false)
    val receiptRemoved: StateFlow<Boolean> = _receiptRemoved.asStateFlow()

    private val _showFullScreenReceipt = MutableStateFlow(false)
    val showFullScreenReceipt: StateFlow<Boolean> = _showFullScreenReceipt.asStateFlow()

    fun showFullScreenReceipt() { _showFullScreenReceipt.value = true }
    fun hideFullScreenReceipt() { _showFullScreenReceipt.value = false }

    fun updatePendingReceiptUri(uri: Uri?) {
        _pendingReceiptUri.value = uri
        _receiptRemoved.value = false
    }

    fun removeReceipt() {
        _pendingReceiptUri.value = null
        _receiptRemoved.value = true
    }

    fun createCameraUri(): Uri = receiptManager.createCameraUri()

    private fun loadReceiptUri(transaction: TransactionEntity) {
        transaction.receiptPath?.let { path ->
            val file = receiptManager.getReceiptFile(path)
            if (file.exists()) {
                _receiptUri.value = file.toUri()
            }
        }
    }

    // ========== Loan Management ==========

    private val _loan = MutableStateFlow<LoanEntity?>(null)
    val loan: StateFlow<LoanEntity?> = _loan.asStateFlow()

    private val _showMarkAsLoanSheet = MutableStateFlow(false)
    val showMarkAsLoanSheet: StateFlow<Boolean> = _showMarkAsLoanSheet.asStateFlow()

    val recentPersonNames: StateFlow<List<String>> = loanRepository.getRecentPersonNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun showMarkAsLoanSheet() { _showMarkAsLoanSheet.value = true }
    fun hideMarkAsLoanSheet() { _showMarkAsLoanSheet.value = false }

    private fun loadLoan(loanId: Long) {
        viewModelScope.launch {
            _loan.value = loanRepository.getLoanById(loanId)
        }
    }

    fun createLoanFromTransaction(
        personName: String,
        direction: LoanDirection,
        note: String?,
        loanAmount: BigDecimal? = null
    ) {
        val txn = _transaction.value ?: return
        // Clamp the user-supplied loan amount to (0, txn.amount]; fall back to the
        // full transaction amount when blank or out of range.
        val contribution = (loanAmount ?: txn.amount).let { input ->
            when {
                input <= BigDecimal.ZERO -> txn.amount
                input > txn.amount -> txn.amount
                else -> input
            }
        }
        viewModelScope.launch {
            try {
                // Check for existing loan in the OPPOSITE direction first (this is a repayment)
                val oppositeDirection = if (direction == LoanDirection.LENT) LoanDirection.BORROWED else LoanDirection.LENT
                val oppositeLoan = loanRepository.findActiveLoanForPerson(personName, oppositeDirection)

                if (oppositeLoan != null) {
                    // Record as repayment on the opposite loan, threading the
                    // user-chosen partial amount so only that portion counts
                    // toward the loan's remaining balance.
                    loanRepository.recordRepayment(oppositeLoan.id, txn.id, contribution)
                    _transaction.value = transactionRepository.getTransactionById(txn.id)
                    _loan.value = loanRepository.getLoanById(oppositeLoan.id)
                    _showMarkAsLoanSheet.value = false
                    return@launch
                }

                // Check if an active loan already exists for this person + same direction
                val existingLoan = loanRepository.findActiveLoanForPerson(personName, direction)
                val loanId = if (existingLoan != null) {
                    // Merge into existing loan with the user-chosen contribution.
                    loanRepository.addToExistingLoan(existingLoan.id, contribution, txn.id)
                    existingLoan.id
                } else {
                    // Create new loan; principal = user-chosen contribution.
                    loanRepository.createLoan(
                        personName = personName,
                        direction = direction,
                        amount = contribution,
                        currency = txn.currency,
                        note = note,
                        sourceTransactionId = txn.id
                    )
                }
                _transaction.value = transactionRepository.getTransactionById(txn.id)
                _loan.value = loanRepository.getLoanById(loanId)
                _showMarkAsLoanSheet.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create loan: ${e.message}"
            }
        }
    }

    fun unlinkLoan() {
        val txn = _transaction.value ?: return
        val loanId = txn.loanId ?: return
        viewModelScope.launch {
            try {
                loanRepository.unlinkTransaction(txn.id, loanId)
                _transaction.value = transactionRepository.getTransactionById(txn.id)
                _loan.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to unlink loan: ${e.message}"
            }
        }
    }

}
