package com.pennywiseai.tracker.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.pennywiseai.tracker.data.manager.ModelDownloader
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.ui.components.SETTLED_HOLD_MILLIS
import com.pennywiseai.tracker.utils.TokenUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmRepository: LlmRepository,
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val addTransactionUseCase: com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase,
    private val deleteTransactionUseCase: com.pennywiseai.tracker.domain.usecase.DeleteTransactionUseCase,
    categoryRepository: com.pennywiseai.tracker.data.repository.CategoryRepository,
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
    
    // Developer stats: what the model's window holds, as LiteRT-LM counts it.
    val chatStats = combine(llmRepository.getAllMessages(), llmRepository.contextTokens) { visible, tokens ->
        ChatStats(
            messageCount = visible.size,
            totalCharacters = visible.sumOf { it.message.length },
            estimatedTokens = tokens,
            contextUsagePercent = TokenUtils.calculateContextUsagePercent(tokens, TokenUtils.MODEL_CONTEXT_TOKENS)
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

        // Load the model and prefill the chat now, not when the first message is sent. A failure
        // here is left for that send to report.
        viewModelScope.launch {
            modelRepository.modelState.first { it == ModelState.READY }
            runCatching { llmRepository.warmUp() }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else android.util.Log.w(LlmRepository.TIMING, "Warm-up failed (${it.javaClass.simpleName})") }
        }

        viewModelScope.launch {
            modelDownloader.progress.map { it.failure }.distinctUntilChanged().collect { failure ->
                val message = when (failure) {
                    ModelDownloader.Failure.NO_SPACE -> R.string.chat_error_no_storage
                    ModelDownloader.Failure.NETWORK -> R.string.chat_error_download_failed
                    ModelDownloader.Failure.INTEGRITY -> R.string.chat_error_integrity_failed
                    ModelDownloader.Failure.ROAMING -> R.string.chat_error_download_roaming
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

    private val _isConfirming = llmRepository.isConfirming
    val isConfirming: StateFlow<Boolean> = _isConfirming.asStateFlow()

    val categories = categoryRepository.getVisibleCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismissPendingAction() {
        if (!_isConfirming.value) llmRepository.clearPendingAction()
    }

    fun toggleRow(id: Int) {
        if (_isConfirming.value) return
        llmRepository.updateRows { rows -> rows.map { if (it.id == id) it.copy(selected = !it.selected) else it } }
    }

    /** Having reviewed the row, the user has answered whatever its check asked. */
    fun editRow(id: Int, draft: com.pennywiseai.tracker.data.model.TransactionDraft) {
        if (_isConfirming.value) return
        llmRepository.updateRows { rows -> rows.map { if (it.id == id) it.copy(draft = draft, check = null, selected = true) else it } }
    }

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
                when (action) {
                    is com.pennywiseai.tracker.data.model.PendingChatAction.Delete -> {
                        val t = action.transaction
                        deleteTransactionUseCase(t)
                        llmRepository.completePendingAction(
                            context.getString(R.string.chat_done_deleted, llmRepository.amountLine(t.amount, t.currency, t.transactionType, t.merchantName))
                        )
                    }
                    is com.pennywiseai.tracker.data.model.PendingChatAction.Update -> {
                        action.newCategory?.let { transactionRepository.updateCategory(action.transaction.id, it) }
                        action.newMerchant?.let { m ->
                            val current = transactionRepository.getTransactionById(action.transaction.id) ?: action.transaction
                            transactionRepository.updateTransaction(current.copy(merchantName = m, updatedAt = java.time.LocalDateTime.now()))
                        }
                        val t = action.transaction
                        val line = llmRepository.amountLine(t.amount, t.currency, t.transactionType, action.newMerchant ?: t.merchantName)
                        llmRepository.completePendingAction(
                            context.getString(R.string.chat_done_updated, line + (action.newCategory?.let { " → $it" } ?: ""))
                        )
                    }
                    is com.pennywiseai.tracker.data.model.PendingChatAction.Add -> {
                        val drafts = action.rows.filter { it.selected }.map { it.draft }
                        // The user tapped Add: leaving the screen now can't undo that, or lose what was saved.
                        if (drafts.isNotEmpty()) withContext(NonCancellable) {
                            addTransactionUseCase.executeAll(drafts)
                            com.pennywiseai.tracker.worker.MoneyAlertWorker.enqueue(context)
                            // Saved: the card stays drawn with its check for a beat. Its line is
                            // written first, so dying in that beat can't leave a saved transaction
                            // with no acknowledgement in the chat (and a retry that doubles it).
                            _uiState.value = _uiState.value.copy(settledCard = action, adds = _uiState.value.adds + 1)
                            llmRepository.completePendingAction(
                                drafts.joinToString("\n") { draft ->
                                    context.getString(
                                        R.string.chat_done_added,
                                        llmRepository.amountLine(draft.amount, draft.currency, draft.type, draft.merchant),
                                        draft.category
                                    )
                                }
                            )
                            // "Remove animations": no beat to hold the check for.
                            if (animationsEnabled()) delay(SETTLED_HOLD_MILLIS)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.chat_error_apply_failed, listOf(e.message.orEmpty())))
            } finally {
                _uiState.value = _uiState.value.copy(settledCard = null)
                _isConfirming.value = false
            }
        }
    }

    // The reply being written, so clearing the chat can stop it instead of leaving it behind.
    private var sendJob: kotlinx.coroutines.Job? = null

    fun sendMessage(message: String) {
        // A write in flight must finish first: its outcome is what the model hears next.
        if (message.isBlank() || _uiState.value.isLoading || _isConfirming.value) return
        // A new message supersedes any card still waiting from the last one.
        llmRepository.clearPendingAction()

        sendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            _currentResponse.value = ""
            
            val tap = System.nanoTime()
            var firstShown = false
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
                        if (!firstShown) {
                            firstShown = true
                            android.util.Log.i(LlmRepository.TIMING, "first=${(System.nanoTime() - tap) / 1_000_000}ms")
                        }
                        _currentResponse.value = if (partialResponse == LlmRepository.RESET_REPLY) "" else _currentResponse.value + partialResponse
                    }
                android.util.Log.i(LlmRepository.TIMING, "reply=${(System.nanoTime() - tap) / 1_000_000}ms")
                
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
        if (_isConfirming.value) return
        // Same thread as the send's own code, so it can't propose a card after this line.
        sendJob?.cancel()
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
    
    // The checks match LlmRepository's own (untranslated) exception messages. Anything else
    // is LiteRT-LM's raw text, which can quote the prompt: never shown, class logged only.
    private fun sendErrorText(error: Throwable, @androidx.annotation.StringRes fallback: Int): UiText? {
        val message = error.message.orEmpty()
        return when {
            // The reply was stopped on purpose: the chat was cleared, or the screen left.
            error is kotlinx.coroutines.CancellationException -> null
            LlmRepository.MESSAGE_TOO_LONG in message -> UiText.Res(R.string.chat_error_message_too_long)
            LlmRepository.INTEGRITY_FAILED in message -> UiText.Res(R.string.chat_error_integrity_failed)
            "downloading" in message -> UiText.Res(R.string.chat_error_model_downloading)
            "not downloaded" in message -> UiText.Res(R.string.chat_error_model_not_downloaded)
            else -> {
                android.util.Log.w(LlmRepository.TIMING, "Chat send failed (${error.javaClass.simpleName})")
                UiText.Res(fallback)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun startModelDownload() = modelDownloader.start()

    fun checkAndResumeDownload() {
        viewModelScope.launch {
            if (modelDownloader.isRunning ||
                modelDownloader.adoptLegacyDownload() ||
                modelDownloader.resumeIfInterrupted()
            ) return@launch
            // No download to resume → resolve the model state, re-verifying a
            // present-but-unverified file before it is shown as READY.
            modelRepository.refreshModelState()
        }
    }

    fun cancelDownload() = modelDownloader.cancel()

    // False under "Remove animations"; a seam for tests, where the Android stub always says false.
    internal var animationsEnabled: () -> Boolean = { android.animation.ValueAnimator.areAnimatorsEnabled() }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
    /** An Add card whose rows were just saved: still drawn, with its check, for a beat. */
    val settledCard: com.pennywiseai.tracker.data.model.PendingChatAction.Add? = null,
    /** Adds saved so far; the Confirm haptic keys on it, since [settledCard] may never reach a frame. */
    val adds: Int = 0
) {
    val cardSaved: Boolean get() = settledCard != null
}

data class ChatStats(
    val messageCount: Int = 0,
    val totalCharacters: Int = 0,
    val estimatedTokens: Int = 0,
    val maxTokens: Int = TokenUtils.MODEL_CONTEXT_TOKENS,
    val contextUsagePercent: Int = 0
)