package com.pennywiseai.tracker.data.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pennywiseai.tracker.R

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
    private const val EXTRA_REMARK_ID = "conscience_remark_id"

    /** The remark id the screen's sample carries: its reactions are acknowledged and change nothing. */
    const val SAMPLE_REMARK_ID = -1L

    /** A tap from a remark posted before remarks had ids; it matches a shown remark that has none. */
    const val NO_REMARK_ID = Long.MIN_VALUE

    /** Whether a line would be seen: the app's notifications and this channel are on. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return ensureChannel(context).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Posts remark [remarkId]. "Liked it" only shows where the language has the full
     * set ([ConscienceText.full]); "Not this one" always does.
     */
    fun post(
        context: Context,
        text: ConscienceText,
        line: String,
        remarkId: Long,
        lineKey: String,
        contentIntent: PendingIntent,
        actions: ConscienceNotificationActions,
    ) = show(context, null, text, line, remarkId, lineKey, contentIntent, actions)

    /** The screen's "Send me one": claims no slot, and its reactions change nothing. */
    fun postSample(context: Context, text: ConscienceText, line: String, actions: ConscienceNotificationActions) =
        show(context, SAMPLE_TAG, text, line, SAMPLE_REMARK_ID, "", actions.openApp(), actions)

    /**
     * Replaces the remark's notification once one of its actions landed, or just
     * clears it when [message] is null (the reaction changed nothing). Only while
     * that remark is still the one shown: a late tap must not overwrite a newer one.
     */
    fun showConfirmation(context: Context, remarkId: Long, message: Int?) {
        val text = ConscienceText(context)
        val manager = ensureChannel(context, text)
        val shown = manager.activeNotifications.firstOrNull {
            it.id == NOTIFICATION_ID && it.notification.extras.getLong(EXTRA_REMARK_ID, NO_REMARK_ID) == remarkId
        } ?: return
        if (message == null) {
            manager.cancel(shown.tag, NOTIFICATION_ID)
            return
        }
        manager.notify(
            shown.tag,
            NOTIFICATION_ID,
            baseBuilder(context, text, text.string(message)).setTimeoutAfter(CONFIRMATION_TIMEOUT_MS).build()
        )
    }

    private fun show(
        context: Context,
        tag: String?,
        text: ConscienceText,
        line: String,
        remarkId: Long,
        lineKey: String,
        contentIntent: PendingIntent,
        actions: ConscienceNotificationActions,
    ) {
        val builder = baseBuilder(context, text, line)
            .addExtras(Bundle().apply { putLong(EXTRA_REMARK_ID, remarkId) })
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                baseBuilder(context, text, text.string(R.string.conscience_lock_screen_text))
                    .setContentIntent(contentIntent)
                    .build()
            )
        if (text.full) {
            builder.addAction(0, text.string(R.string.conscience_action_like), actions.react(remarkId, lineKey, liked = true))
        }
        builder.addAction(0, text.string(R.string.conscience_action_dislike), actions.react(remarkId, lineKey, liked = false))
        ensureChannel(context, text).notify(tag, NOTIFICATION_ID, builder.build())
    }

    private fun baseBuilder(context: Context, text: ConscienceText, message: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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
