package com.pennywiseai.tracker.ui.screens.chat

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.utils.TokenUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmRepository: LlmRepository,
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadedMB = MutableStateFlow(0L)
    val downloadedMB: StateFlow<Long> = _downloadedMB.asStateFlow()

    private val _totalMB = MutableStateFlow(Constants.ModelDownload.MODEL_SIZE_MB)
    val totalMB: StateFlow<Long> = _totalMB.asStateFlow()

    private var currentDownloadId: Long? = null
    
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
            initialValue = if (modelRepository.isModelDownloaded()) ModelState.READY else ModelState.NOT_DOWNLOADED
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
        modelRepository.checkModelState()

        // Load initial context message for display
        viewModelScope.launch {
            loadContextMessage()
        }

        // Pick up any in-progress download
        checkAndResumeDownload()
    }
    
    private suspend fun loadContextMessage() {
        val contextMessage = llmRepository.getFormattedContextForDisplay()
        _contextMessage.value = ChatMessage(
            message = contextMessage,
            isUser = false,
            isSystemPrompt = false
        )
    }
    
    fun sendMessage(message: String) {
        if (message.isBlank() || _uiState.value.isLoading) return
        
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
                        val errorMessage = when {
                            error.message?.contains("memory is full") == true -> 
                                "Chat memory is full. Please clear the chat to continue."
                            error.message?.contains("downloading") == true ->
                                "Model is downloading. Please wait."
                            error.message?.contains("not downloaded") == true ->
                                "AI model not downloaded. Go to Settings to download."
                            else -> error.message ?: "Failed to generate response"
                        }
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
                val errorMessage = when {
                    e.message?.contains("memory is full") == true -> 
                        "Chat memory is full. Please clear the chat to continue."
                    e.message?.contains("downloading") == true ->
                        "Model is downloading. Please wait."
                    e.message?.contains("not downloaded") == true ->
                        "AI model not downloaded. Go to Settings to download."
                    else -> e.message ?: "Failed to send message"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMessage
                )
                _currentResponse.value = ""
            }
        }
    }
    
    fun clearChat() {
        viewModelScope.launch {
            llmRepository.deleteAllMessages()
            _uiState.value = _uiState.value.copy(
                error = null
            )
            // Reload context message after clearing chat
            loadContextMessage()
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun startModelDownload() {
        viewModelScope.launch {
            val existingDownloadId = userPreferencesRepository.getActiveDownloadId()
            if (existingDownloadId != null) {
                val query = DownloadManager.Query().setFilterById(existingDownloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_RUNNING ||
                            status == DownloadManager.STATUS_PENDING ||
                            status == DownloadManager.STATUS_PAUSED
                        ) {
                            cursor.close()
                            currentDownloadId = existingDownloadId
                            modelRepository.updateModelState(ModelState.DOWNLOADING)
                            monitorDownload(existingDownloadId)
                            return@launch
                        }
                    }
                    cursor.close()
                }
            }

            val availableSpace = context.filesDir.usableSpace
            if (availableSpace < Constants.ModelDownload.REQUIRED_SPACE_BYTES) {
                _uiState.value = _uiState.value.copy(error = "Not enough storage space for download")
                return@launch
            }

            // Validate model URL before attempting download
            val modelUrl = Constants.ModelDownload.MODEL_URL
            if (modelUrl.isBlank() || !modelUrl.startsWith("http")) {
                Log.e("ChatViewModel", "Invalid MODEL_URL: '$modelUrl'")
                modelRepository.updateModelState(ModelState.ERROR)
                _uiState.value = _uiState.value.copy(error = "AI model download is not available in this build.")
                return@launch
            }

            // Clean up any stale partial file — DownloadManager stays PENDING if destination exists
            val existingFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Constants.ModelDownload.MODEL_FILE_NAME)
            if (existingFile.exists()) {
                existingFile.delete()
            }

            try {
                val request = DownloadManager.Request(Uri.parse(modelUrl))
                    .setTitle("AI Chat Model")
                    .setDescription("Downloading AI chat assistant for PennyWise")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, Constants.ModelDownload.MODEL_FILE_NAME)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)

                currentDownloadId = downloadManager.enqueue(request)
                modelRepository.updateModelState(ModelState.DOWNLOADING)
                userPreferencesRepository.saveActiveDownloadId(currentDownloadId!!)
                monitorDownload(currentDownloadId!!)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to start download", e)
                modelRepository.updateModelState(ModelState.ERROR)
                _uiState.value = _uiState.value.copy(error = "Failed to start download. Please try again.")
            }
        }
    }

    private fun monitorDownload(downloadId: Long) {
        viewModelScope.launch {
            while (isActive && modelState.value == ModelState.DOWNLOADING) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor != null && cursor.moveToFirst()) {
                    val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (bytesCol != -1 && totalCol != -1) {
                        val bytesDownloaded = cursor.getLong(bytesCol)
                        var bytesTotal = cursor.getLong(totalCol)
                        if (bytesTotal <= 0) {
                            bytesTotal = Constants.ModelDownload.MODEL_SIZE_BYTES
                        }
                        _downloadProgress.value = (bytesDownloaded * 100 / bytesTotal).toInt()
                        _downloadedMB.value = bytesDownloaded / (1024 * 1024)
                        _totalMB.value = bytesTotal / (1024 * 1024)
                    }

                    if (statusCol != -1) {
                        when (cursor.getInt(statusCol)) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                _downloadProgress.value = 100
                                userPreferencesRepository.clearActiveDownloadId()
                                modelRepository.updateModelState(ModelState.READY)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                userPreferencesRepository.clearActiveDownloadId()
                                modelRepository.updateModelState(ModelState.ERROR)
                                _uiState.value = _uiState.value.copy(error = "Download failed. Please try again.")
                            }
                        }
                    }
                }
                cursor?.close()
                delay(1000)
            }
        }
    }

    fun checkAndResumeDownload() {
        viewModelScope.launch {
            val savedDownloadId = userPreferencesRepository.getActiveDownloadId() ?: return@launch
            val query = DownloadManager.Query().setFilterById(savedDownloadId)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex != -1) {
                    val status = cursor.getInt(statusIndex)
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                        currentDownloadId = savedDownloadId
                        modelRepository.updateModelState(ModelState.DOWNLOADING)
                        val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        if (bytesCol != -1 && totalCol != -1) {
                            val bytes = cursor.getLong(bytesCol)
                            var total = cursor.getLong(totalCol)
                            if (total <= 0) total = Constants.ModelDownload.MODEL_SIZE_BYTES
                            _downloadedMB.value = bytes / (1024 * 1024)
                            _totalMB.value = total / (1024 * 1024)
                            if (total > 0) _downloadProgress.value = (bytes * 100 / total).toInt()
                        }
                        cursor.close()
                        monitorDownload(savedDownloadId)
                        return@launch
                    }
                }
                cursor.close()
            }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            currentDownloadId?.let {
                downloadManager.remove(it)
                modelRepository.updateModelState(ModelState.NOT_DOWNLOADED)
                _downloadProgress.value = 0
                _downloadedMB.value = 0
                _totalMB.value = Constants.ModelDownload.MODEL_SIZE_MB

                userPreferencesRepository.clearActiveDownloadId()

                val modelFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Constants.ModelDownload.MODEL_FILE_NAME)
                if (modelFile.exists()) {
                    modelFile.delete()
                }
            }
        }
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ChatStats(
    val messageCount: Int = 0,
    val totalCharacters: Int = 0,
    val estimatedTokens: Int = 0,
    val systemPromptTokens: Int = 0,
    val maxTokens: Int = 4096,
    val contextUsagePercent: Int = 0
)