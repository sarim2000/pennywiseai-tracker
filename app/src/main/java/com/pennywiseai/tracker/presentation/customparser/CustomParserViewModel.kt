package com.pennywiseai.tracker.presentation.customparser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CustomParserRuleEntity
import com.pennywiseai.tracker.data.repository.CustomParserRuleRepository
import com.pennywiseai.tracker.domain.service.CustomParserRuleBuilder
import com.pennywiseai.tracker.domain.service.CustomParserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomParserViewModel @Inject constructor(
    private val repository: CustomParserRuleRepository,
    private val builder: CustomParserRuleBuilder,
    private val parserService: CustomParserService
) : ViewModel() {

    val rules: StateFlow<List<CustomParserRuleEntity>> = repository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()

    fun loadForEdit(ruleId: Long) {
        if (ruleId <= 0) {
            _editorState.value = EditorState()
            return
        }
        viewModelScope.launch {
            val existing = repository.getRuleById(ruleId) ?: return@launch
            _editorState.value = EditorState(
                editingId = existing.id,
                name = existing.name,
                senderPattern = existing.senderPattern,
                sampleSms = existing.sampleSms,
                bankNameDisplay = existing.bankNameDisplay,
                currency = existing.currency,
                tags = emptyList(),
                amountRegex = existing.amountRegex,
                merchantRegex = existing.merchantRegex,
                accountRegex = existing.accountRegex,
                expenseKeywords = existing.expenseKeywords.split(",")
                    .map { it.trim() }.filter { it.isNotEmpty() },
                incomeKeywords = existing.incomeKeywords.split(",")
                    .map { it.trim() }.filter { it.isNotEmpty() }
            )
        }
    }

    fun updateName(name: String) {
        _editorState.value = _editorState.value.copy(name = name)
    }

    fun updateSenderPattern(value: String) {
        _editorState.value = _editorState.value.copy(senderPattern = value)
    }

    fun updateBankName(value: String) {
        _editorState.value = _editorState.value.copy(bankNameDisplay = value)
    }

    fun updateCurrency(value: String) {
        _editorState.value = _editorState.value.copy(currency = value)
    }

    fun updateSample(sample: String) {
        // Resetting tags when the sample changes — token indices would otherwise
        // refer to a different tokenization.
        _editorState.value = _editorState.value.copy(
            sampleSms = sample,
            tags = emptyList()
        ).recompute()
    }

    fun setTag(tokenIndex: Int, tag: CustomParserRuleBuilder.TokenTag?) {
        val state = _editorState.value
        // Each field tag (Amount/Merchant/Account) can only point to one token.
        // Multiple keyword tokens are allowed.
        val cleaned = state.tags.filterNot { it.tokenIndex == tokenIndex }
        val isUnique = tag == CustomParserRuleBuilder.TokenTag.AMOUNT ||
            tag == CustomParserRuleBuilder.TokenTag.MERCHANT ||
            tag == CustomParserRuleBuilder.TokenTag.ACCOUNT
        val deduped = if (isUnique) cleaned.filterNot { it.tag == tag } else cleaned

        val updated = if (tag == null) deduped else
            deduped + CustomParserRuleBuilder.TaggedToken(tokenIndex, tag)

        _editorState.value = state.copy(tags = updated).recompute()
    }

    fun save(onDone: (Long) -> Unit) {
        val state = _editorState.value
        if (!state.isValid) return
        viewModelScope.launch {
            val now = java.time.LocalDateTime.now()
            val entity = CustomParserRuleEntity(
                id = state.editingId ?: 0,
                name = state.name.trim(),
                senderPattern = state.senderPattern.trim(),
                sampleSms = state.sampleSms,
                amountRegex = state.amountRegex ?: "",
                merchantRegex = state.merchantRegex,
                accountRegex = state.accountRegex,
                expenseKeywords = state.expenseKeywords.joinToString(","),
                incomeKeywords = state.incomeKeywords.joinToString(","),
                currency = state.currency.trim().ifEmpty { "INR" },
                bankNameDisplay = state.bankNameDisplay.trim(),
                priority = 100,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            val saved = if (state.editingId != null) {
                repository.updateRule(entity)
                state.editingId
            } else {
                repository.insertRule(entity)
            }
            onDone(saved)
        }
    }

    fun setActive(id: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setActive(id, isActive) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteRuleById(id) }
    }

    private fun EditorState.recompute(): EditorState {
        if (sampleSms.isBlank()) {
            return copy(
                amountRegex = null,
                merchantRegex = null,
                accountRegex = null,
                expenseKeywords = emptyList(),
                incomeKeywords = emptyList(),
                preview = CustomParserService.PreviewResult(null, null, null, null)
            )
        }
        val patterns = builder.build(sampleSms, tags)
        val tentative = CustomParserRuleEntity(
            id = editingId ?: 0,
            name = name,
            senderPattern = senderPattern,
            sampleSms = sampleSms,
            amountRegex = patterns.amountRegex ?: "",
            merchantRegex = patterns.merchantRegex,
            accountRegex = patterns.accountRegex,
            expenseKeywords = patterns.expenseKeywords.joinToString(","),
            incomeKeywords = patterns.incomeKeywords.joinToString(","),
            currency = currency,
            bankNameDisplay = bankNameDisplay,
            priority = 100,
            isActive = true
        )
        val preview = parserService.extractPreview(tentative, sampleSms)
        return copy(
            amountRegex = patterns.amountRegex,
            merchantRegex = patterns.merchantRegex,
            accountRegex = patterns.accountRegex,
            expenseKeywords = patterns.expenseKeywords,
            incomeKeywords = patterns.incomeKeywords,
            preview = preview
        )
    }

    data class EditorState(
        val editingId: Long? = null,
        val name: String = "",
        val senderPattern: String = "",
        val sampleSms: String = "",
        val bankNameDisplay: String = "",
        val currency: String = "INR",
        val tags: List<CustomParserRuleBuilder.TaggedToken> = emptyList(),
        val amountRegex: String? = null,
        val merchantRegex: String? = null,
        val accountRegex: String? = null,
        val expenseKeywords: List<String> = emptyList(),
        val incomeKeywords: List<String> = emptyList(),
        val preview: CustomParserService.PreviewResult =
            CustomParserService.PreviewResult(null, null, null, null)
    ) {
        val isValid: Boolean get() =
            name.isNotBlank() &&
            senderPattern.isNotBlank() &&
            bankNameDisplay.isNotBlank() &&
            !amountRegex.isNullOrBlank() &&
            (expenseKeywords.isNotEmpty() || incomeKeywords.isNotEmpty())
    }
}
