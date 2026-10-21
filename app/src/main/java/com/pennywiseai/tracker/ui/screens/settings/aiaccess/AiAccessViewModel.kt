package com.pennywiseai.tracker.ui.screens.settings.aiaccess

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.aiaccess.AiRelayClient
import com.pennywiseai.tracker.aiaccess.AiSessionService
import com.pennywiseai.tracker.aiaccess.AiSessionState
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.billing.EntitlementGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiAccessUiState(
    val pairingCode: String? = null,
    val pairingExpiresAtMillis: Long = 0,
    val connections: List<AiRelayClient.Connection> = emptyList(),
    val loadingConnections: Boolean = false,
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class AiAccessViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val relay: AiRelayClient,
    sessionState: AiSessionState,
    entitlementGate: EntitlementGate,
) : ViewModel() {

    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled
    val session = sessionState.session
    val relayUrl: StateFlow<String> = relay.relayUrl

    private val _state = MutableStateFlow(AiAccessUiState())
    val state: StateFlow<AiAccessUiState> = _state.asStateFlow()

    init { refreshConnections() }

    fun startSession(minutes: Int) = AiSessionService.start(context, minutes)
    fun extendSession() = AiSessionService.extend(context)
    fun stopSession() = AiSessionService.stop(context)

    fun newPairingCode() = work {
        val code = relay.newPairingCode()
        _state.update {
            it.copy(pairingCode = code.code, pairingExpiresAtMillis = System.currentTimeMillis() + code.expiresIn * 1000L)
        }
    }

    fun refreshConnections() = viewModelScope.launch {
        _state.update { it.copy(loadingConnections = true) }
        runCatching { relay.connections() }
            .onSuccess { list ->
                _state.update {
                    // A new connection means the shown code was just used; it can't work again.
                    val used = list.size > it.connections.size && it.pairingCode != null
                    it.copy(connections = list, loadingConnections = false, pairingCode = if (used) null else it.pairingCode)
                }
            }
            .onFailure { _state.update { it.copy(loadingConnections = false, message = R.string.ai_access_offline, messageIsError = true) } }
    }

    fun revoke(id: String) = work {
        relay.revoke(id)
        _state.update { s -> s.copy(connections = s.connections.filterNot { it.id == id }, message = R.string.ai_access_disconnected, messageIsError = false) }
    }

    fun disconnectEverything() = work {
        AiSessionService.stop(context)
        relay.reset()
        _state.value = AiAccessUiState(message = R.string.ai_access_all_disconnected)
    }

    /** Switches to a self-hosted relay (or back). Returns false when the address isn't https. */
    fun setRelayUrl(url: String): Boolean {
        if (AiRelayClient.normalizeRelayUrl(url) == null) {
            _state.update { it.copy(message = R.string.ai_access_relay_invalid, messageIsError = true) }
            return false
        }
        relay.setRelayUrl(url)
        _state.value = AiAccessUiState(message = R.string.ai_access_relay_saved)
        return true
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        runCatching { block() }.onFailure { _state.update { it.copy(message = R.string.ai_access_offline, messageIsError = true) } }
        _state.update { it.copy(busy = false) }
    }
}
