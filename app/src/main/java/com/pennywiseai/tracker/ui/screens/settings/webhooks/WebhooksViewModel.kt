package com.pennywiseai.tracker.ui.screens.settings.webhooks

import com.pennywiseai.tracker.billing.EntitlementGate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.data.webhook.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WebhookEditorState(
    val id: String? = null,
    val name: String = "",
    val url: String = "",
    val currency: String = "INR",
    val enabled: Boolean = true,
    val types: Set<WebhookDataType> = WebhookDataType.entries.toSet(),
    val range: WebhookRangePreset = WebhookRangePreset.SINCE_LAST_SUCCESS,
    val start: String = "",
    val end: String = "",
    val headers: List<WebhookHeader> = emptyList()
)

data class WebhooksUiState(
    val profiles: List<WebhookProfileEntity> = emptyList(),
    val editor: WebhookEditorState? = null,
    val historyId: String? = null,
    val logs: List<WebhookLogEntity> = emptyList(),
    val interval: String = "6",
    val intervalHours: Int = 6,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class WebhooksViewModel @Inject constructor(
    private val repository: WebhookRepository,
    private val scheduler: WebhookSyncScheduler,
    private val preferences: WebhookPreferences,
    private val entitlementGate: EntitlementGate
) : ViewModel() {
    val isProEntitled = entitlementGate.isProEntitled
    private val _state = MutableStateFlow(WebhooksUiState())
    val state = _state.asStateFlow()
    private var historyJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeProfiles().collect { profiles -> _state.update { it.copy(profiles = profiles) } }
        }
        viewModelScope.launch {
            preferences.intervalHours.collect { hours -> _state.update { it.copy(interval = hours.toString(), intervalHours = hours) } }
        }
    }

    fun newProfile() {
        if (!isProEntitled.value) return
        _state.update { it.copy(editor = WebhookEditorState(), message = null) }
    }
    fun closeEditor() = _state.update { it.copy(editor = null, message = null) }
    fun updateEditor(change: (WebhookEditorState) -> WebhookEditorState) =
        _state.update { it.copy(editor = it.editor?.let(change), message = null) }

    fun edit(profile: WebhookProfileEntity) = _state.update {
        if (!isProEntitled.value) return@update it
        it.copy(editor = WebhookEditorState(profile.id, profile.name, profile.url, profile.currency,
            profile.enabled, repository.decodeDataTypes(profile.dataTypes), profile.rangePreset,
            profile.customStart?.toLocalDate()?.toString().orEmpty(),
            profile.customEnd?.toLocalDate()?.toString().orEmpty(), repository.decodeHeaders(profile.headersJson)), message = null)
    }

    fun save() = action {
        val editor = _state.value.editor ?: return@action
        val draft = WebhookProfileDraft(editor.id, editor.name, editor.url, editor.enabled, editor.types,
            editor.range, runCatching { LocalDate.parse(editor.start).atStartOfDay() }.getOrNull(),
            runCatching { LocalDate.parse(editor.end).atTime(java.time.LocalTime.MAX) }.getOrNull(),
            editor.currency, editor.headers)
        val error = WebhookValidation.validateDraft(draft)
        if (error != null) { message(error); return@action }
        repository.save(draft)
        _state.update { it.copy(editor = null, message = "Webhook saved") }
    }

    fun setInterval(value: String) = _state.update { it.copy(interval = value) }
    fun saveInterval() = action {
        val error = WebhookValidation.validateIntervalHours(_state.value.interval)
        if (error != null) { message(error); return@action }
        preferences.setInterval(_state.value.interval.toInt())
        message("Sync interval saved")
    }

    fun sendTest(id: String) = action {
        scheduler.enqueue(WebhookSyncReason.TEST, test = true, profileId = id)
        showHistory(id)
        message("Synthetic test queued. Delivery results appear in history.")
    }
    fun syncNow(id: String? = null) = action {
        scheduler.enqueue(WebhookSyncReason.MANUAL, profileId = id)
        if (id != null) showHistory(id)
        message("Sync queued. Delivery runs when online.")
    }
    fun setEnabled(id: String, enabled: Boolean) = action { repository.setEnabled(id, enabled) }
    fun delete(id: String) = action(requiresPro = false) {
        repository.delete(id)
        if (_state.value.historyId == id) closeHistory()
        message("Webhook deleted")
    }

    fun showHistory(id: String) {
        historyJob?.cancel()
        _state.update { it.copy(historyId = id, logs = emptyList()) }
        historyJob = viewModelScope.launch {
            repository.observeLogs(id).collect { logs -> _state.update { it.copy(logs = logs) } }
        }
    }
    fun closeHistory() {
        historyJob?.cancel()
        _state.update { it.copy(historyId = null, logs = emptyList(), message = null) }
    }
    private fun message(value: String) = _state.update { it.copy(message = value) }
    private fun action(requiresPro: Boolean = true, block: suspend () -> Unit) {
        if (_state.value.busy || (requiresPro && !isProEntitled.value)) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try { if (!requiresPro || isProEntitled.value) block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Could not complete the webhook action. Try again.") }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
