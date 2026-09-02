package com.pennywiseai.tracker.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.billing.FreeTierLimits
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.rules.RuleSharingCodec
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleTemplateService
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.domain.usecase.DryRunResult
import com.pennywiseai.tracker.domain.usecase.InitializeRuleTemplatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ruleRepository: RuleRepository,
    private val ruleTemplateService: RuleTemplateService,
    private val initializeRuleTemplatesUseCase: InitializeRuleTemplatesUseCase,
    private val applyRulesToPastTransactionsUseCase: ApplyRulesToPastTransactionsUseCase,
    private val accountBalanceRepository: AccountBalanceRepository,
    entitlementGate: EntitlementGate,
) : ViewModel() {

    /**
     * Pro entitlement. Drives both the "create another rule" gate below
     * and the inline quota caption on the Rules screen.
     */
    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _batchApplyProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchApplyProgress: StateFlow<Pair<Int, Int>?> = _batchApplyProgress.asStateFlow()

    private val _batchApplyResult = MutableStateFlow<BatchApplyResult?>(null)
    val batchApplyResult: StateFlow<BatchApplyResult?> = _batchApplyResult.asStateFlow()

    private val _dryRunResult = MutableStateFlow<DryRunResult?>(null)
    val dryRunResult: StateFlow<DryRunResult?> = _dryRunResult.asStateFlow()

    // Outcome of the last export/import, shown once and then cleared (#741).
    private val _sharingMessage = MutableStateFlow<String?>(null)
    val sharingMessage: StateFlow<String?> = _sharingMessage.asStateFlow()

    fun clearSharingMessage() {
        _sharingMessage.value = null
    }

    /**
     * Says so up-front when there is nothing to export, so the file picker
     * never opens just to leave an empty file behind.
     */
    fun reportNothingToExport() {
        _sharingMessage.value = "You don't have any custom rules to export yet."
    }

    val rules: StateFlow<List<TransactionRule>> = ruleRepository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * True when the user is allowed to create another rule — either Pro
     * (unlimited) or under [FreeTierLimits.MAX_RULES]. The Rules screen's
     * FAB checks this to decide between opening the create flow and
     * triggering the paywall.
     */
    val canCreateMoreRules: StateFlow<Boolean> = combine(rules, isProEntitled) { all, pro ->
        pro || all.size < FreeTierLimits.MAX_RULES
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    /**
     * Non-hidden accounts from `hidden_accounts` SharedPreferences key.
     */
    val accounts: StateFlow<List<AccountInfo>> = accountBalanceRepository.getAllLatestBalances()
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
                        displayName = balance.displayLabel,
                        isCreditCard = balance.isCreditCard,
                        accountType = balance.accountType
                    )
                }
                .distinctBy { "${it.bankName}_${it.accountLast4}" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        initializeRules()
    }

    private fun initializeRules() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Initialize default rule templates if none exist
                initializeRuleTemplatesUseCase()
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleRule(ruleId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                ruleRepository.setRuleActive(ruleId, isActive)
            } catch (e: Exception) {
                // Log error
                e.printStackTrace()
            }
        }
    }

    fun createRule(rule: TransactionRule) {
        viewModelScope.launch {
            try {
                ruleRepository.insertRule(rule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            try {
                ruleRepository.deleteRule(ruleId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateRule(rule: TransactionRule) {
        viewModelScope.launch {
            try {
                ruleRepository.updateRule(rule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reads [uri] as text, refusing anything past [RuleSharingCodec.MAX_FILE_BYTES].
     *
     * The import picker has to accept every MIME type (some file managers
     * won't surface JSON under the strict one), so a mis-picked video could otherwise be
     * pulled into memory whole. The size is checked up-front where the provider
     * reports one, and the read is capped regardless for providers that don't.
     */
    private fun readBoundedText(uri: Uri): String {
        val limit = RuleSharingCodec.MAX_FILE_BYTES
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    require(cursor.getLong(sizeIndex) <= limit) {
                        "That file is too large to be a rules file."
                    }
                }
            }

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Couldn't open the file.")
        return input.use { stream ->
            // One byte past the limit so an over-long stream is detected rather
            // than silently truncated into an unparseable fragment.
            val bytes = ByteArray((limit + 1).toInt())
            var read = 0
            while (read < bytes.size) {
                val n = stream.read(bytes, read, bytes.size - read)
                if (n < 0) break
                read += n
            }
            require(read <= limit) { "That file is too large to be a rules file." }
            String(bytes, 0, read, Charsets.UTF_8)
        }
    }

    /**
     * Writes the user's own rules to [uri] as a shareable JSON file. Built-in
     * templates are left out — see [RuleSharingCodec.exportable].
     */
    fun exportRules(uri: Uri) {
        viewModelScope.launch {
            try {
                val all = ruleRepository.getAllRules().first()
                val exportable = RuleSharingCodec.exportable(all)
                if (exportable.isEmpty()) {
                    _sharingMessage.value = "You don't have any custom rules to export yet."
                    return@launch
                }
                val text = RuleSharingCodec.encode(all)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray())
                    } ?: throw IllegalStateException("Couldn't open the file for writing.")
                }
                _sharingMessage.value = if (exportable.size == 1) {
                    "Exported 1 rule."
                } else {
                    "Exported ${exportable.size} rules."
                }
            } catch (e: Exception) {
                _sharingMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    /**
     * Adds the rules in [uri] to this install. Imported rules get fresh ids, so
     * a file can be applied more than once without colliding; a rule whose name
     * already exists is skipped rather than duplicated.
     *
     * The free-tier rule cap still applies — importing must not be a way around
     * the paywall the create screen enforces.
     */
    fun importRules(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val text = withContext(Dispatchers.IO) { readBoundedText(uri) }
                val decoded = RuleSharingCodec.decode(text)

                val existing = ruleRepository.getAllRules().first()
                val existingNames = existing.map { it.name.trim().lowercase() }.toSet()
                val fresh = decoded.rules.filterNot { it.name.trim().lowercase() in existingNames }
                val duplicates = decoded.rules.size - fresh.size

                val allowance = if (isProEntitled.value) {
                    fresh.size
                } else {
                    (FreeTierLimits.MAX_RULES - existing.size).coerceAtLeast(0)
                }
                val toImport = fresh.take(allowance)
                // One insert, so a failure can't leave the set half-applied.
                ruleRepository.insertRules(toImport)

                val blocked = fresh.size - toImport.size
                val skipped = decoded.duplicatedInFile
                _sharingMessage.value = buildString {
                    append(
                        if (toImport.size == 1) "Imported 1 rule."
                        else "Imported ${toImport.size} rules."
                    )
                    if (duplicates > 0) append(" $duplicates already existed.")
                    if (skipped > 0) {
                        append(
                            if (skipped == 1) " 1 repeated name in the file was collapsed."
                            else " $skipped repeated names in the file were collapsed."
                        )
                    }
                    if (blocked > 0) {
                        append(" $blocked more need Pro — you're at the free limit of ${FreeTierLimits.MAX_RULES}.")
                    }
                }
            } catch (e: Exception) {
                _sharingMessage.value = e.message ?: "Import failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getRuleApplicationCount(ruleId: String): Flow<Int> = flow {
        emit(ruleRepository.getRuleApplicationCount(ruleId))
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Force reset to default templates
                initializeRuleTemplatesUseCase(forceReset = true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun applyRuleToPastTransactions(
        rule: TransactionRule,
        applyToUncategorizedOnly: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _batchApplyProgress.value = 0 to 0
            _batchApplyResult.value = null

            try {
                val result = if (applyToUncategorizedOnly) {
                    applyRulesToPastTransactionsUseCase.applyRuleToUncategorizedTransactions(
                        rule = rule,
                        onProgress = { processed, total ->
                            _batchApplyProgress.value = processed to total
                        }
                    )
                } else {
                    applyRulesToPastTransactionsUseCase.applyRuleToAllTransactions(
                        rule = rule,
                        onProgress = { processed, total ->
                            _batchApplyProgress.value = processed to total
                        }
                    )
                }
                _batchApplyResult.value = result
            } catch (e: Exception) {
                e.printStackTrace()
                _batchApplyResult.value = BatchApplyResult(
                    totalProcessed = 0,
                    totalUpdated = 0,
                    errors = listOf("Error: ${e.message}")
                )
            } finally {
                _isLoading.value = false
                _batchApplyProgress.value = null
            }
        }
    }

    fun applyAllRulesToPastTransactions() {
        viewModelScope.launch {
            _isLoading.value = true
            _batchApplyProgress.value = 0 to 0
            _batchApplyResult.value = null

            try {
                val result = applyRulesToPastTransactionsUseCase.applyAllActiveRulesToTransactions(
                    onProgress = { processed, total ->
                        _batchApplyProgress.value = processed to total
                    }
                )
                _batchApplyResult.value = result
            } catch (e: Exception) {
                e.printStackTrace()
                _batchApplyResult.value = BatchApplyResult(
                    totalProcessed = 0,
                    totalUpdated = 0,
                    errors = listOf("Error: ${e.message}")
                )
            } finally {
                _isLoading.value = false
                _batchApplyProgress.value = null
            }
        }
    }

    fun clearBatchApplyResult() {
        _batchApplyResult.value = null
        _dryRunResult.value = null
    }

    fun previewRule(rule: TransactionRule) {
        viewModelScope.launch {
            _isLoading.value = true
            _dryRunResult.value = null
            try {
                _dryRunResult.value = applyRulesToPastTransactionsUseCase.previewRuleApplication(rule)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * UI model for an account in the rule condition picker
     */
    data class AccountInfo(
        val bankName: String,
        val accountLast4: String,
        val displayName: String,
        val isCreditCard: Boolean,
        val accountType: String?
    )
}