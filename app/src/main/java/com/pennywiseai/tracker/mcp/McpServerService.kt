package com.pennywiseai.tracker.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pennywiseai.tracker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : Service() {

    companion object {
        private const val CHANNEL_ID = "mcp_server_channel"
        private const val NOTIFICATION_ID = 9876
        const val EXTRA_PORT = "extra_port"

        fun start(context: Context, port: Int = McpServer.DEFAULT_PORT) {
            val intent = Intent(context, McpServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, McpServerService::class.java))
        }
    }

    @Inject
    lateinit var toolHandler: McpToolHandler

    private var mcpServer: McpServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, McpServer.DEFAULT_PORT) ?: McpServer.DEFAULT_PORT

        startForeground(NOTIFICATION_ID, createNotification(port))

        serviceScope.launch {
            mcpServer = McpServer(toolHandler, port).also { it.start() }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mcpServer?.stop()
        mcpServer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MCP Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the MCP developer server is running"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(port: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server Running")
            .setContentText("Listening on port $port")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
