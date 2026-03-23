package com.pennywiseai.tracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleTemplateService
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.domain.usecase.DryRunResult
import com.pennywiseai.tracker.domain.usecase.InitializeRuleTemplatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val ruleTemplateService: RuleTemplateService,
    private val initializeRuleTemplatesUseCase: InitializeRuleTemplatesUseCase,
    private val applyRulesToPastTransactionsUseCase: ApplyRulesToPastTransactionsUseCase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _batchApplyProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchApplyProgress: StateFlow<Pair<Int, Int>?> = _batchApplyProgress.asStateFlow()

    private val _batchApplyResult = MutableStateFlow<BatchApplyResult?>(null)
    val batchApplyResult: StateFlow<BatchApplyResult?> = _batchApplyResult.asStateFlow()

    private val _dryRunResult = MutableStateFlow<DryRunResult?>(null)
    val dryRunResult: StateFlow<DryRunResult?> = _dryRunResult.asStateFlow()

    val rules: StateFlow<List<TransactionRule>> = ruleRepository.getAllRules()
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
}