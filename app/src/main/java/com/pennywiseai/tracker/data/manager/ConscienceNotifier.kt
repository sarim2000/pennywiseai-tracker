package com.pennywiseai.tracker.data.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pennywiseai.tracker.MainActivity
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

    // The sample gets a tag of its own, so trying it out never replaces a real remark.
    private const val SAMPLE_TAG = "sample"
    private const val CONFIRMATION_TIMEOUT_MS = 6_000L

    /** Whether a line would be seen: the app's notifications and this channel are on. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return ensureChannel(context).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun post(context: Context, text: ConscienceText, line: String, remarkId: Long, transactionId: Long) {
        show(context, null, text, line, remarkId, SmsBroadcastReceiver.openTransactionIntent(context, transactionId))
    }

    /** The screen's "Send me one": claims no slot, and its reactions change nothing. */
    fun postSample(context: Context, text: ConscienceText, line: String) {
        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        show(context, SAMPLE_TAG, text, line, NotificationActionReceiver.SAMPLE_REMARK_ID, openApp)
    }

    /** Replaces the line after one of its actions landed. */
    fun showConfirmation(context: Context, sample: Boolean, message: String) {
        val text = ConscienceText(context)
        ensureChannel(context, text).notify(
            if (sample) SAMPLE_TAG else null,
            NOTIFICATION_ID,
            baseBuilder(context, text, message).setTimeoutAfter(CONFIRMATION_TIMEOUT_MS).build()
        )
    }

    private fun show(
        context: Context,
        tag: String?,
        text: ConscienceText,
        line: String,
        remarkId: Long,
        contentIntent: PendingIntent
    ) {
        val builder = baseBuilder(context, text, line)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                baseBuilder(context, text, text.string(R.string.conscience_lock_screen_text))
                    .setContentIntent(contentIntent)
                    .build()
            )
            .addAction(
                0,
                text.string(R.string.conscience_action_like),
                NotificationActionReceiver.conscienceReactionIntent(context, remarkId, liked = true)
            )
            .addAction(
                0,
                text.string(R.string.conscience_action_dislike),
                NotificationActionReceiver.conscienceReactionIntent(context, remarkId, liked = false)
            )
        ensureChannel(context, text).notify(tag, NOTIFICATION_ID, builder.build())
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
