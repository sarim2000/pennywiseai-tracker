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
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.navigation.AddTransaction
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import com.pennywiseai.tracker.utils.SmsReportUrlBuilder
import com.pennywiseai.parser.core.GenericTransactionExtractor
import com.pennywiseai.parser.core.ParsedTransaction
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class UnrecognizedSmsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val learnedSendersStore: LearnedSendersStore
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
    
    /**
     * What each message looks like it says (#994), by message id; only the ones worth
     * suggesting. A sender the user has added from before is always worth it.
     */
    val suggestions: StateFlow<Map<Long, Suggestion>> = combine(allMessages, learnedSendersStore.allFlow) { messages, learned ->
            messages.mapNotNull { sms ->
                val key = LearnedSendersStore.normalize(sms.sender)
                GenericTransactionExtractor.extract(sms.sender, sms.smsBody)
                    ?.takeIf { key in learned || it.confidence >= GenericTransactionExtractor.SUGGEST_AT }
                    ?.let { sms.id to Suggestion(sms, it.transaction, learned[key]) }
            }.toMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleShowReported() {
        _showReported.value = !_showReported.value
    }
    
    fun reportMessage(message: UnrecognizedSmsEntity) {
        viewModelScope.launch {
            try {
                val url = SmsReportUrlBuilder.buildUrl(context, message.smsBody, message.sender)
                Log.d("UnrecognizedSmsViewModel", "Full URL length: ${url.length}")
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
}

/** A suggested transaction (#994), with the account last booked from its sender, if any. */
data class Suggestion(
    val sms: UnrecognizedSmsEntity,
    val transaction: ParsedTransaction,
    val learnedAccount: LearnedSendersStore.Account?
) {
    /** The Add form, pre-filled; saving it hides [sms] and teaches its sender. */
    fun draft(): AddTransaction {
        val account = LearnedSendersStore.accountToUse(learnedAccount, transaction.accountLast4)
        return AddTransaction(
            amount = transaction.amount.toPlainString(),
            merchant = transaction.merchant,
            bankName = account?.bankName,
            accountLast4 = account?.accountLast4 ?: transaction.accountLast4,
            currency = transaction.currency,
            type = transaction.type.name,
            dateTime = sms.receivedAt.toString(),
            unrecognizedSmsId = sms.id
        )
    }
}
