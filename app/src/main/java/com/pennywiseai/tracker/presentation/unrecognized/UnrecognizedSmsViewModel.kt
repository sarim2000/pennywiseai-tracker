package com.pennywiseai.tracker.presentation.unrecognized

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.parser.core.AmountFormat
import com.pennywiseai.parser.core.GenericTransactionExtractor
import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.core.Constants.Links
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.manager.TaughtMessageBooker
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.data.preferences.TaughtTemplatesStore
import com.pennywiseai.tracker.data.preferences.TrackedNotificationAppsStore
import com.pennywiseai.tracker.data.repository.SmsInboxReader
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.domain.model.Suggestion
import com.pennywiseai.tracker.data.notification.BankNotificationConfig
import com.pennywiseai.tracker.utils.SmsReportUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UnrecognizedSmsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val learnedSendersStore: LearnedSendersStore,
    private val trackedAppsStore: TrackedNotificationAppsStore,
    private val taughtTemplatesStore: TaughtTemplatesStore,
    private val taughtMessageBooker: TaughtMessageBooker,
    private val smsInboxReader: SmsInboxReader
) : ViewModel() {

    private companion object {
        const val TAG = "UnrecognizedSmsViewModel"
    }

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
     * suggesting. A sender the user has added from before, or an app they chose to
     * track, is always worth it.
     */
    val suggestions: StateFlow<Map<Long, Suggestion>> = combine(
        allMessages, learnedSendersStore.allFlow, trackedAppsStore.allFlow, taughtTemplatesStore.allFlow
    ) { messages, learned, trackedApps, _ ->
        messages.mapNotNull { sms ->
            val key = LearnedSendersStore.normalize(sms.sender)
            taughtMessageBooker.read(sms.sender, sms.smsBody, sms.timestampMillis())?.let { taught ->
                return@mapNotNull sms.id to Suggestion(sms, taught, null, taught = true)
            }
            val trusted = key in learned || sms.sender in trackedApps
            GenericTransactionExtractor.extract(sms.sender, sms.smsBody)
                ?.takeIf { trusted || it.confidence >= GenericTransactionExtractor.SUGGEST_AT }
                ?.let { sms.id to Suggestion(sms, it.transaction, learned[key]) }
        }.toMap()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Messages from a sender the user taught that no template of theirs fits any more. */
    val formatChanged: StateFlow<Set<Long>> = combine(allMessages, taughtTemplatesStore.allFlow) { messages, _ ->
        messages.filter { taughtMessageBooker.formatChanged(it.sender, it.smsBody, it.timestampMillis()) }
            .map { it.id }
            .toSet()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val taughtTemplates: StateFlow<List<MessageTemplate>> = taughtTemplatesStore.allFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _teach = MutableStateFlow<TeachState?>(null)
    val teach: StateFlow<TeachState?> = _teach.asStateFlow()

    /** The inbox picker: null while closed, and messages null while it reads. */
    data class InboxPicker(val messages: List<SmsInboxReader.Message>? = null)

    private val _inboxPicker = MutableStateFlow<InboxPicker?>(null)
    val inboxPicker: StateFlow<InboxPicker?> = _inboxPicker.asStateFlow()

    private val _notices = Channel<Int>(Channel.BUFFERED)
    /** One-off results, as string resources to show in a snackbar. */
    val notices = _notices.receiveAsFlow()

    val canReadInbox: Boolean get() = smsInboxReader.canRead()

    fun openInboxPicker() {
        _inboxPicker.value = InboxPicker()
        viewModelScope.launch {
            val messages = runCatching { smsInboxReader.recentUntracked() }
                .onFailure { Log.e(TAG, "Error reading the inbox", it) }
                .getOrDefault(emptyList())
            // Still open: closing it while it read shouldn't bring it back.
            _inboxPicker.update { picker -> picker?.copy(messages = messages) }
        }
    }

    fun closeInboxPicker() {
        _inboxPicker.value = null
    }

    fun teachWaiting(message: UnrecognizedSmsEntity) {
        _teach.value = TeachState.forMessage(message.sender, message.smsBody, message.timestampMillis())
    }

    fun teachFromInbox(message: SmsInboxReader.Message) {
        _inboxPicker.value = null
        _teach.value = TeachState.forMessage(message.sender, message.body, message.timestamp)
    }

    fun dismissTeach() {
        _teach.value = null
    }

    fun onTeachTap(offset: Int) = _teach.update { it?.tap(offset) }

    fun onTeachField(field: TeachField) = _teach.update { it?.copy(active = field) }

    fun onTeachType(type: TransactionType) = _teach.update { it?.copy(type = type) }

    fun onTeachCurrency(currency: String) = _teach.update {
        it?.copy(currency = currency, amountFormat = null, cannotLearn = false)
    }

    fun onTeachFormat(format: AmountFormat) = _teach.update { it?.copy(amountFormat = format) }

    fun onTeachMerchantTyped(name: String) = _teach.update { it?.copy(merchant = null, merchantTyped = name, cannotLearn = false) }

    /**
     * Learns the shape and books this message through it, so the first booking proves the
     * template reads what's on screen. Teaching a message again replaces what misread it.
     */
    fun saveTeach() {
        val state = _teach.value ?: return
        val selection = state.selection ?: return
        val currency = state.currency ?: return
        val type = state.type ?: return
        if (state.missing != null) return
        val parsers = BankParserFactory.getParsers(state.sender)
        val template = MessageTemplates.build(
            id = UUID.randomUUID().toString(),
            senderKey = LearnedSendersStore.normalize(state.sender),
            body = state.body,
            selection = selection,
            type = type,
            currency = currency,
            bankName = parsers.firstOrNull()?.getBankName() ?: BankNotificationConfig.displayName(context, state.sender),
            amountFormat = state.amountFormat ?: MessageTemplates.inferFormat(state.amountText.orEmpty(), currency),
            fixedMerchant = state.merchantTyped.trim().takeIf { state.merchant == null && it.isNotEmpty() }
        )
        if (template == null) {
            _teach.update { it?.copy(cannotLearn = true) }
            return
        }
        val misread = taughtTemplatesStore.forSender(state.sender)
            .filter { MessageTemplates.match(it, state.body, state.sender, state.timestamp) != null }
            .map { it.id }
        taughtTemplatesStore.save(template, replacing = misread)
        _teach.value = null
        viewModelScope.launch {
            notify(taughtMessageBooker.bookMessage(state.sender, state.body, state.timestamp), saved = R.string.teach_saved)
        }
    }

    /** Add on a message a taught template read: the user's confirmation. */
    fun addTaught(message: UnrecognizedSmsEntity) {
        viewModelScope.launch { notify(taughtMessageBooker.bookWaiting(message.id), saved = R.string.teach_added) }
    }

    fun deleteTemplate(template: MessageTemplate) = taughtTemplatesStore.delete(template.id)

    private suspend fun notify(outcome: TaughtMessageBooker.Outcome, saved: Int) {
        _notices.send(
            when (outcome) {
                TaughtMessageBooker.Outcome.SAVED -> saved
                TaughtMessageBooker.Outcome.SETTLED -> R.string.teach_settled
                TaughtMessageBooker.Outcome.FAILED -> R.string.teach_booking_failed
            }
        )
    }

    private fun UnrecognizedSmsEntity.timestampMillis(): Long =
        receivedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
