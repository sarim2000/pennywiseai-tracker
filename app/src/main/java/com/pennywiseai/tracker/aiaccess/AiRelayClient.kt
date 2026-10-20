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

    // Device secret: app-private, outside PennyWise backups and Android backup (allowBackup=false).
    // ponytail: plain private prefs, wrap with a Keystore key if the threat model grows.
    private val prefs = context.getSharedPreferences("ai_access", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val registerLock = Mutex()
    private val http = HttpClient(OkHttp) {
        install(WebSockets) // no protocol pings: the relay answers our text "ping" without waking up
        install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 20_000 }
    }

    private val _device = MutableStateFlow(load())
    val device: StateFlow<Device?> = _device.asStateFlow()

    private fun load(): Device? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val secret = prefs.getString(KEY_SECRET, null) ?: return null
        return Device(id, secret)
    }

    private suspend fun ensureDevice(): Device = registerLock.withLock {
        _device.value ?: run {
            val res = http.post("$RELAY_URL/device")
            val d = json.decodeFromString<Device>(res.checked().bodyAsText())
            prefs.edit { putString(KEY_ID, d.deviceId); putString(KEY_SECRET, d.secret) }
            _device.value = d
            d
        }
    }

    private suspend fun auth(): String = ensureDevice().let { "Device ${it.deviceId}.${it.secret}" }

    suspend fun newPairingCode(): PairingCode =
        json.decodeFromString(http.post("$RELAY_URL/device/pair") { header(HttpHeaders.Authorization, auth()) }.checked().bodyAsText())

    suspend fun connections(): List<Connection> {
        if (_device.value == null) return emptyList()
        val res = http.get("$RELAY_URL/device/connections") { header(HttpHeaders.Authorization, auth()) }
        return json.decodeFromString<Connections>(res.checked().bodyAsText()).connections
    }

    suspend fun revoke(connectionId: String) {
        http.delete("$RELAY_URL/device/connections/$connectionId") { header(HttpHeaders.Authorization, auth()) }.checked()
    }

    /** Revokes every AI connection and forgets this phone; the next pairing registers afresh. */
    suspend fun reset() {
        if (_device.value != null) {
            http.delete("$RELAY_URL/device") { header(HttpHeaders.Authorization, auth()) }.checked()
        }
        prefs.edit { clear() }
        _device.value = null
    }

    /** Opens the AI-session line and runs [block] until it closes. */
    suspend fun openLine(block: suspend DefaultClientWebSocketSession.() -> Unit) {
        val auth = auth()
        http.webSocket("${RELAY_URL.replaceFirst("https", "wss")}/device/connect", request = {
            header(HttpHeaders.Authorization, auth)
        }, block = block)
    }

    private fun HttpResponse.checked(): HttpResponse {
        if (!status.isSuccess()) throw RelayException("Relay answered ${status.value}")
        return this
    }

    companion object {
        const val RELAY_URL = "https://ai.pennywise.zynth.dev"
        const val CONNECTOR_URL = "$RELAY_URL/mcp"
        private const val KEY_ID = "device_id"
        private const val KEY_SECRET = "device_secret"
    }
}
