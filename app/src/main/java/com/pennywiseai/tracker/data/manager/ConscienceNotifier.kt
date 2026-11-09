package com.pennywiseai.tracker.data.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.receiver.NotificationActionReceiver
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver

/**
 * Silent, on its own channel so it can be muted alone, and deliberately without
 * setGroup: the transaction stack's header counts every child of its group.
 */
object ConscienceNotifier {
    private const val CHANNEL_ID = "conscience"

    // Transaction alerts use the transaction id (never negative); the recap is -2, the stack's header -3.
    private const val NOTIFICATION_ID = -4

    private const val CONFIRMATION_TIMEOUT_MS = 6_000L

    /** Whether a line would be seen: the app's notifications and this channel are on. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return ensureChannel(context).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun post(context: Context, text: ConscienceText, line: String, lineKey: String, transactionId: Long) {
        val openTransaction = SmsBroadcastReceiver.openTransactionIntent(context, transactionId)
        val builder = baseBuilder(context, text, line)
            .setContentIntent(openTransaction)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                baseBuilder(context, text, text.string(R.string.conscience_lock_screen_text))
                    .setContentIntent(openTransaction)
                    .build()
            )
            .addAction(
                0,
                text.string(R.string.conscience_action_dislike),
                NotificationActionReceiver.conscienceRetireIntent(context, lineKey)
            )
        ensureChannel(context, text).notify(NOTIFICATION_ID, builder.build())
    }

    /** Replaces the remark once "Not this one" landed. At most one remark a day, so it can't be a newer one. */
    fun showRetired(context: Context) {
        val text = ConscienceText(context)
        ensureChannel(context, text).notify(
            NOTIFICATION_ID,
            baseBuilder(context, text, text.string(R.string.conscience_confirm_dislike))
                .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
                .build()
        )
    }

    private fun baseBuilder(context: Context, text: ConscienceText, message: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(text.string(R.string.conscience_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSilent(true)
            .setAutoCancel(true)

    private fun ensureChannel(context: Context, text: ConscienceText = ConscienceText(context)): NotificationManager {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                text.string(R.string.conscience_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = text.string(R.string.conscience_channel_description)
                enableVibration(false)
                setSound(null, null)
            }
        )
        return notificationManager
    }
}
