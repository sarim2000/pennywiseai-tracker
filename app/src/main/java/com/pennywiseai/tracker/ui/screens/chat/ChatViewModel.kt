package com.pennywiseai.tracker.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.pennywiseai.tracker.data.manager.ModelDownloader
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.utils.TokenUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmRepository: LlmRepository,
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val addTransactionUseCase: com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase,
    private val deleteTransactionUseCase: com.pennywiseai.tracker.domain.usecase.DeleteTransactionUseCase,
    private val transactionRepository: com.pennywiseai.tracker.data.repository.TransactionRepository,
    private val modelDownloader: ModelDownloader
) : ViewModel() {


    val downloadProgress: StateFlow<Int> = modelDownloader.progress
        .map { (it.downloadedBytes * 100 / it.totalBytes.coerceAtLeast(1)).toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedMB: StateFlow<Long> = modelDownloader.progress
        .map { it.downloadedBytes / BYTES_PER_MB }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalMB: StateFlow<Long> = modelDownloader.progress
        .map { it.totalBytes / BYTES_PER_MB }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Constants.ModelDownload.MODEL_SIZE_MB)

    
    private val _contextMessage = MutableStateFlow<ChatMessage?>(null)
    
    val messages: StateFlow<List<ChatMessage>> = combine(
        llmRepository.getAllMessages(),
        _contextMessage
    ) { dbMessages, contextMsg ->
        if (dbMessages.isEmpty() && contextMsg != null) {
            // Show context message only when chat is empty
            listOf(contextMsg)
        } else {
            // Show actual chat messages
            dbMessages
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val modelState: StateFlow<ModelState> = modelRepository.modelState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = when {
                modelRepository.isModelVerified() -> ModelState.READY
                modelRepository.isModelDownloaded() -> ModelState.LOADING // present, pending re-verify
                else -> ModelState.NOT_DOWNLOADED
            }
        )
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()
    
    val isDeveloperModeEnabled = userPreferencesRepository.isDeveloperModeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    // Get all messages including system for accurate token count
    private val allMessagesIncludingSystem = llmRepository.getAllMessagesIncludingSystem()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Chat statistics for developer mode
    val chatStats = allMessagesIncludingSystem.combine(currentResponse) { allMsgs, current ->
        // Calculate system prompt tokens separately
        val systemPromptText = allMsgs.filter { it.isSystemPrompt }.joinToString(" ") { it.message }
        val systemPromptTokens = if (systemPromptText.isNotEmpty()) {
            TokenUtils.estimateTokens(systemPromptText)
        } else {
            0
        }
        
        // Calculate total tokens
        val allText = allMsgs.joinToString(" ") { it.message } + " " + current
        val totalChars = allText.length
        val estimatedTokens = TokenUtils.estimateTokens(allText)
        val maxTokens = 4096
        
        // Count only visible messages for UI
        val visibleCount = allMsgs.count { !it.isSystemPrompt }
        
        ChatStats(
            messageCount = visibleCount,
            totalCharacters = totalChars,
            estimatedTokens = estimatedTokens,
            systemPromptTokens = systemPromptTokens,
            maxTokens = maxTokens,
            contextUsagePercent = TokenUtils.calculateContextUsagePercent(estimatedTokens, maxTokens)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatStats()
    )
    
    init {
        // Load initial context message for display
        viewModelScope.launch {
            loadContextMessage()
        }

        // Resume an in-progress download, or (when idle) resolve + re-verify the model.
        checkAndResumeDownload()

        viewModelScope.launch {
            modelDownloader.progress.map { it.failure }.distinctUntilChanged().collect { failure ->
                val message = when (failure) {
                    ModelDownloader.Failure.NO_SPACE -> R.string.chat_error_no_storage
                    ModelDownloader.Failure.NETWORK -> R.string.chat_error_download_failed
                    ModelDownloader.Failure.INTEGRITY -> R.string.chat_error_integrity_failed
                    null -> return@collect
                }
                _uiState.value = _uiState.value.copy(error = UiText.Res(message))
            }
        }
    }
    
    private suspend fun loadContextMessage() {
        val contextMessage = llmRepository.getFormattedContextForDisplay()
        _contextMessage.value = ChatMessage(
            message = contextMessage,
            isUser = false,
            isSystemPrompt = false
        )
    }
    
    // Transaction the model proposed; shown as a confirm card (#170). Nothing
    // is written until the user taps Add.
    val pendingAction: StateFlow<com.pennywiseai.tracker.data.model.PendingChatAction?> = llmRepository.pendingAction

    val baseCurrency: StateFlow<String> = userPreferencesRepository.baseCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")

    private val _isConfirming = MutableStateFlow(false)
    val isConfirming: StateFlow<Boolean> = _isConfirming.asStateFlow()

    fun dismissPendingAction() = llmRepository.clearPendingAction()

    /** A card belongs to the conversation that produced it: leaving the screen drops it. */
    override fun onCleared() {
        llmRepository.clearPendingAction()
        super.onCleared()
    }

    fun confirmPendingAction() {
        val action = pendingAction.value ?: return
        // One write per card: the flag disables the buttons, and a racing second
        // tap returns here before it can launch a second write.
        if (!_isConfirming.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val currency = userPreferencesRepository.baseCurrency.first()
                val fmt = { a: java.math.BigDecimal -> com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(a, currency) }
                when (action) {
                    is com.pennywiseai.tracker.data.model.PendingChatAction.Delete -> {
                        deleteTransactionUseCase(action.transaction)
                        llmRepository.clearPendingAction()
                        val t = action.transaction
                        llmRepository.appendAssistantMessage("Deleted ${com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(t.amount, t.currency)} at ${t.merchantName}.")
                        return@launch
                    }
                    is com.pennywiseai.tracker.data.model.PendingChatAction.Update -> {
                        action.newCategory?.let { transactionRepository.updateCategory(action.transaction.id, it) }
                        action.newMerchant?.let { m ->
                            val current = transactionRepository.getTransactionById(action.transaction.id) ?: action.transaction
                            transactionRepository.updateTransaction(current.copy(merchantName = m, updatedAt = java.time.LocalDateTime.now()))
                        }
                        llmRepository.clearPendingAction()
                        val t = action.transaction
                        llmRepository.appendAssistantMessage(
                            "Updated ${com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(t.amount, t.currency)} at ${action.newMerchant ?: t.merchantName}" +
                                (action.newCategory?.let { " → $it" } ?: "") + "."
                        )
                        return@launch
                    }
                    is com.pennywiseai.tracker.data.model.PendingChatAction.Add -> Unit
                }
                val draft = action.draft
                addTransactionUseCase.execute(
                    amount = draft.amount,
                    merchant = draft.merchant,
                    category = draft.category,
                    type = draft.type,
                    date = java.time.LocalDateTime.now(),
                    notes = "Added from chat: \"${draft.sourceText}\"",
                    bankName = draft.bankName,
                    accountLast4 = draft.accountLast4,
                    currency = currency
                )
                llmRepository.clearPendingAction()
                llmRepository.appendAssistantMessage(
                    "Added ${com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(draft.amount, currency)} " +
                        "${if (draft.type == com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME) "from" else "at"} ${draft.merchant} (${draft.category})."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.chat_error_apply_failed, listOf(e.message.orEmpty())))
            } finally {
                _isConfirming.value = false
            }
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank() || _uiState.value.isLoading) return
        // A new message supersedes any card still waiting from the last one.
        llmRepository.clearPendingAction()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            _currentResponse.value = ""
            
            try {
                // Use streaming for better UX
                llmRepository.sendMessageStream(message)
                    .catch { error ->
                        val errorMessage = sendErrorText(error, R.string.chat_error_generate_failed)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }
                    .collect { partialResponse ->
                        _currentResponse.value += partialResponse
                    }
                
                _uiState.value = _uiState.value.copy(isLoading = false)
                _currentResponse.value = ""
            } catch (e: Exception) {
                val errorMessage = sendErrorText(e, R.string.chat_error_send_failed)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMessage
                )
                _currentResponse.value = ""
            }
        }
    }
    
    fun clearChat() {
        llmRepository.clearPendingAction()
        viewModelScope.launch {
            llmRepository.deleteAllMessages()
            _uiState.value = _uiState.value.copy(
                error = null
            )
            // Reload context message after clearing chat
            loadContextMessage()
        }
    }
    
    // The checks match LlmRepository's own (untranslated) exception messages.
    private fun sendErrorText(error: Throwable, @androidx.annotation.StringRes fallback: Int): UiText = when {
        error.message?.contains("memory is full") == true -> UiText.Res(R.string.chat_error_memory_full)
        error.message?.contains("downloading") == true -> UiText.Res(R.string.chat_error_model_downloading)
        error.message?.contains("not downloaded") == true -> UiText.Res(R.string.chat_error_model_not_downloaded)
        else -> error.message?.let { UiText.Plain(it) } ?: UiText.Res(fallback)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun startModelDownload() = modelDownloader.start()

    fun checkAndResumeDownload() {
        if (modelDownloader.isRunning || modelDownloader.resumeIfInterrupted()) return
        // No download to resume → resolve the model state, re-verifying a
        // present-but-unverified file before it is shown as READY.
        viewModelScope.launch { modelRepository.refreshModelState() }
    }

    fun cancelDownload() = modelDownloader.cancel()

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null
)

data class ChatStats(
    val messageCount: Int = 0,
    val totalCharacters: Int = 0,
    val estimatedTokens: Int = 0,
    val systemPromptTokens: Int = 0,
    val maxTokens: Int = 4096,
    val contextUsagePercent: Int = 0
)