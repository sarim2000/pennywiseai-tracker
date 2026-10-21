package com.pennywiseai.tracker.aiaccess

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * A user-started, timed AI session (ADR 0002): keeps the phone's line to the
 * relay open and answers tool calls from Room. Never restarts on its own.
 */
@AndroidEntryPoint
class AiSessionService : Service() {

    @Inject lateinit var relay: AiRelayClient
    @Inject lateinit var tools: AiTools
    @Inject lateinit var state: AiSessionState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lineJob: Job? = null
    private var timerJob: Job? = null
    private var endsAt = 0L
    private var lastNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Wi-Fi ↔ mobile data: drop the old line at once instead of waiting for it to time out.
            if (lastNetwork != null && lastNetwork != network) reconnectNow()
            lastNetwork = network
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_EXTEND -> endsAt = maxOf(endsAt, System.currentTimeMillis()) + EXTEND_MS
            else -> endsAt = System.currentTimeMillis() + (intent?.getIntExtra(EXTRA_MINUTES, 30) ?: 30) * 60_000L
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )
        publish(lineUp = state.session.value?.lineUp == true)
        restartTimer()
        if (lineJob == null) {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
            lineJob = scope.launch { lineLoop() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        scope.cancel()
        state.set(null)
        super.onDestroy()
    }

    private fun restartTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
            stopSelf()
        }
    }

    private suspend fun lineLoop() {
        var backoff = 1_000L
        while (scope.isActive) {
            try {
                relay.openLine {
                    publish(lineUp = true)
                    backoff = 1_000L
                    val pinger = launch { while (isActive) { delay(PING_MS); send(Frame.Text("ping")) } }
                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        if (text == "pong") continue
                        val call = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                        if (call["type"]?.jsonPrimitive?.contentOrNull != "call") continue
                        launch {
                            val result = tools.call(
                                call["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                call["arguments"] as? JsonObject ?: JsonObject(emptyMap()),
                            )
                            send(Frame.Text(buildJsonObject {
                                put("type", "result")
                                put("id", call["id"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                put("result", result)
                            }.toString()))
                        }
                    }
                    pinger.cancel()
                }
            } catch (e: Exception) {
                // Our own job cancelled (reconnect or stop): leave without publishing a dropped line.
                // A CancellationException from the socket itself is just a drop.
                if (e is kotlinx.coroutines.CancellationException) currentCoroutineContext().ensureActive()
                Log.w(TAG, "AI line dropped: ${e.javaClass.simpleName}")
            }
            publish(lineUp = false)
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private fun reconnectNow() {
        lineJob?.cancel()
        lineJob = scope.launch { lineLoop() }
    }

    private fun publish(lineUp: Boolean) {
        state.set(AiSessionState.Session(endsAt, lineUp))
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification() = run {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.ai_session_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.ai_session_channel_description)
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ai_session)
            .setContentTitle(getString(R.string.ai_session_notification_title))
            .setContentText(getString(if (state.session.value?.lineUp == true) R.string.ai_session_notification_connected else R.string.ai_session_notification_connecting))
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.ai_access_extend), serviceIntent(ACTION_EXTEND, 1))
            .addAction(0, getString(R.string.ai_access_stop), serviceIntent(ACTION_STOP, 2))
            .build()
    }

    private fun serviceIntent(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, AiSessionService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val TAG = "AiSession"
        private const val CHANNEL_ID = "ai_session"
        private const val NOTIFICATION_ID = 4310
        private const val ACTION_STOP = "com.pennywiseai.tracker.aiaccess.STOP"
        private const val ACTION_EXTEND = "com.pennywiseai.tracker.aiaccess.EXTEND"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTEND_MS = 15 * 60_000L
        private const val PING_MS = 25_000L

        fun start(context: Context, minutes: Int) = ContextCompat.startForegroundService(
            context, Intent(context, AiSessionService::class.java).putExtra(EXTRA_MINUTES, minutes)
        )

        fun extend(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, AiSessionService::class.java).setAction(ACTION_EXTEND)
        )

        fun stop(context: Context) = context.stopService(Intent(context, AiSessionService::class.java))
    }
}
