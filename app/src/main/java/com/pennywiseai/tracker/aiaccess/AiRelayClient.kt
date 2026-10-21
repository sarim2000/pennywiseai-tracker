package com.pennywiseai.tracker.aiaccess

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The relay's phone API (relay/README.md). Holds this install's device credentials. */
@Singleton
class AiRelayClient @Inject constructor(@ApplicationContext context: Context) {

    @Serializable data class Device(val deviceId: String, val secret: String)
    @Serializable data class PairingCode(val code: String, val expiresIn: Int)
    @Serializable data class Connection(val id: String, val clientName: String, val createdAt: Long)
    @Serializable private data class Connections(val connections: List<Connection>)

    class RelayException(message: String) : Exception(message)

    // App-private prefs, outside PennyWise backups and Android backup (allowBackup=false).
    // The device secret is additionally encrypted with a non-exportable Android Keystore key.
    private val prefs = context.getSharedPreferences("ai_access", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val registerLock = Mutex()
    private val http = HttpClient(OkHttp) {
        install(WebSockets) // no protocol pings: the relay answers our text "ping" without waking up
        install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 20_000 }
    }

    private val _relayUrl = MutableStateFlow(prefs.getString(KEY_RELAY, null) ?: DEFAULT_RELAY_URL)
    /** The relay this phone uses: PennyWise's by default, or one the user hosts themselves. */
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()
    private val relay get() = _relayUrl.value

    private val _device = MutableStateFlow(load())
    val device: StateFlow<Device?> = _device.asStateFlow()

    private fun load(): Device? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val sealed = prefs.getString(KEY_SECRET_SEALED, null) ?: return null
        val secret = runCatching { DeviceSecretBox.open(sealed) }.getOrNull() ?: return null // key lost: register again
        return Device(id, secret)
    }

    /**
     * Switches to another relay (for example a self-hosted one). The old relay's registration
     * doesn't carry over, so this phone registers afresh and every AI app must pair again.
     */
    fun setRelayUrl(url: String) {
        val normalized = normalizeRelayUrl(url) ?: throw RelayException("Use an https:// address")
        synchronized(this) {
            if (normalized == relay) return
            prefs.edit {
                remove(KEY_ID); remove(KEY_SECRET_SEALED)
                if (normalized == DEFAULT_RELAY_URL) remove(KEY_RELAY) else putString(KEY_RELAY, normalized)
            }
            _device.value = null
            _relayUrl.value = normalized
        }
    }

    /** This phone's registration and the relay it belongs to, registering first if needed. */
    private suspend fun ensureDevice(): Pair<String, Device> = registerLock.withLock {
        val base = synchronized(this) { _device.value?.let { return@withLock relay to it }; relay }
        val d = json.decodeFromString<Device>(http.post("$base/device").checked().bodyAsText())
        synchronized(this) {
            // The relay was switched while this registered: the device belongs to the old one.
            if (relay != base) throw RelayException("Relay changed")
            prefs.edit { putString(KEY_ID, d.deviceId); putString(KEY_SECRET_SEALED, DeviceSecretBox.seal(d.secret)); remove("device_secret") /* pre-Keystore plaintext */ }
            _device.value = d
        }
        base to d
    }

    /** Relay base URL plus the matching `Authorization` header, read together. */
    private suspend fun signed(): Pair<String, String> = ensureDevice().let { (base, d) -> base to "Device ${d.deviceId}.${d.secret}" }

    suspend fun newPairingCode(): PairingCode {
        val (base, auth) = signed()
        return json.decodeFromString(http.post("$base/device/pair") { header(HttpHeaders.Authorization, auth) }.checked().bodyAsText())
    }

    suspend fun connections(): List<Connection> {
        if (_device.value == null) return emptyList()
        val (base, auth) = signed()
        val res = http.get("$base/device/connections") { header(HttpHeaders.Authorization, auth) }
        return json.decodeFromString<Connections>(res.checked().bodyAsText()).connections
    }

    suspend fun revoke(connectionId: String) {
        val (base, auth) = signed()
        http.delete("$base/device/connections/$connectionId") { header(HttpHeaders.Authorization, auth) }.checked()
    }

    /** Revokes every AI connection and forgets this phone; the next pairing registers afresh. */
    suspend fun reset() {
        if (_device.value == null) return
        val (base, auth) = signed()
        http.delete("$base/device") { header(HttpHeaders.Authorization, auth) }.checked()
        synchronized(this) {
            if (relay != base) return
            prefs.edit { remove(KEY_ID); remove(KEY_SECRET_SEALED) }
            _device.value = null
        }
    }

    /** Opens the AI-session line and runs [block] until it closes. */
    suspend fun openLine(block: suspend DefaultClientWebSocketSession.() -> Unit) {
        val (base, auth) = signed()
        http.webSocket("${base.replaceFirst("https", "wss")}/device/connect", request = {
            header(HttpHeaders.Authorization, auth)
        }, block = block)
    }

    private fun HttpResponse.checked(): HttpResponse {
        if (!status.isSuccess()) throw RelayException("Relay answered ${status.value}")
        return this
    }

    companion object {
        const val DEFAULT_RELAY_URL = "https://ai.pennywise.zynth.dev"
        private const val KEY_ID = "device_id"
        private const val KEY_SECRET_SEALED = "device_secret_sealed"
        private const val KEY_RELAY = "relay_url"

        fun connectorUrl(relay: String) = "$relay/mcp"
        fun dashboardUrl(relay: String) = "$relay/dashboard/"

        /** `https://host[/path]` without a trailing slash or `/mcp`; null if it isn't https. */
        fun normalizeRelayUrl(input: String): String? {
            val u = input.trim().removeSuffix("/").removeSuffix("/mcp").removeSuffix("/")
            val uri = runCatching { java.net.URI(u) }.getOrNull() ?: return null
            return u.takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() }
        }
    }
}
