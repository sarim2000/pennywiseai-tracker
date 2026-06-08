package com.pennywiseai.tracker.ui.screens.unrecognized

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.core.Constants.Links
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.database.dao.CustomParserRuleDao
import com.pennywiseai.tracker.data.database.entity.CustomParserRuleEntity
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class UnrecognizedSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val customParserRuleDao: CustomParserRuleDao
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _showReported = MutableStateFlow(true)
    val showReported: StateFlow<Boolean> = _showReported.asStateFlow()
    
    private val allMessages = unrecognizedSmsRepository.getAllVisible()
    
    val unrecognizedMessages: StateFlow<List<UnrecognizedSmsEntity>> = 
        combine(allMessages, _showReported) { messages, showReported ->
            if (showReported) {
                messages
            } else {
                messages.filter { !it.reported }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun toggleShowReported() {
        _showReported.value = !_showReported.value
    }
    
    fun reportMessage(message: UnrecognizedSmsEntity) {
        viewModelScope.launch {
            try {
                // URL encode the parameters
                val encodedMessage = URLEncoder.encode(message.smsBody, "UTF-8")
                val encodedSender = URLEncoder.encode(message.sender, "UTF-8")
                
                // Create the report URL using hash fragment for privacy
                val url = "${Constants.Links.WEB_PARSER_URL}/#message=$encodedMessage&sender=$encodedSender&autoparse=true"
                
                // Open in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                
                // Mark as reported
                unrecognizedSmsRepository.markAsReported(listOf(message.id))
                
                Log.d("UnrecognizedSmsViewModel", "Opened report for message from: ${message.sender}")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Error opening report", e)
            }
        }
    }
    
    fun deleteMessage(message: UnrecognizedSmsEntity) {
        viewModelScope.launch {
            try {
                // Delete the specific message
                unrecognizedSmsRepository.deleteMessage(message.id)
                Log.d("UnrecognizedSmsViewModel", "Deleted message from: ${message.sender}")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Error deleting message", e)
            }
        }
    }
    
    fun deleteAllMessages() {
        viewModelScope.launch {
            try {
                unrecognizedSmsRepository.deleteAll()
                Log.d("UnrecognizedSmsViewModel", "Deleted all unrecognized messages")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Error deleting all messages", e)
            }
        }
    }

    /**
     * Saves a user-defined custom parsing rule to the database and marks the source
     * unrecognized message as reported/processed so it drops off the UI checklist.
     */
    fun saveCustomRule(rule: CustomParserRuleEntity, originalMessageId: Long) {
        viewModelScope.launch {
            try {
                // Save custom rule
                customParserRuleDao.insert(rule)
                // Mark original message as reported (so it is processed / no longer listed as raw unrecognized)
                unrecognizedSmsRepository.markAsReported(listOf(originalMessageId))
                Log.d("UnrecognizedSmsViewModel", "Saved custom rule and marked unrecognized SMS $originalMessageId as reported.")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Failed to save custom parser rule", e)
            }
        }
    }
}
