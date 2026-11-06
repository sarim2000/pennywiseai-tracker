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
import com.pennywiseai.tracker.domain.conscience.ConscienceAddress
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceLines
import com.pennywiseai.tracker.domain.conscience.ConscienceVoice
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

    fun post(context: Context, transactionId: Long, voice: ConscienceVoice, text: String, frequency: ConscienceFrequency) {
        show(context, voice, text, frequency, SmsBroadcastReceiver.openTransactionIntent(context, transactionId))
    }

    /** The settings "Try it": claims no slot, and has no transaction to open. */
    fun postSample(context: Context, voice: ConscienceVoice, address: ConscienceAddress, frequency: ConscienceFrequency) {
        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        show(context, voice, ConscienceLines.samples(voice, address).first(), frequency, openApp)
    }

    /** Replaces the line after one of its actions landed. */
    fun showConfirmation(context: Context, voice: ConscienceVoice, text: String) {
        ensureChannel(context).notify(
            NOTIFICATION_ID,
            baseBuilder(context, voice, text).setTimeoutAfter(CONFIRMATION_TIMEOUT_MS).build()
        )
    }

    private fun show(
        context: Context,
        voice: ConscienceVoice,
        text: String,
        frequency: ConscienceFrequency,
        contentIntent: PendingIntent
    ) {
        val builder = baseBuilder(context, voice, text)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                baseBuilder(context, voice, ConscienceLines.lockScreenText(voice))
                    .setContentIntent(contentIntent)
                    .build()
            )
        if (frequency != ConscienceFrequency.LESS) {
            builder.addAction(
                0,
                ConscienceLines.lessOftenLabel(voice),
                NotificationActionReceiver.conscienceIntent(context, NotificationActionReceiver.ACTION_CONSCIENCE_LESS_OFTEN)
            )
        }
        builder.addAction(
            0,
            ConscienceLines.pauseLabel(voice),
            NotificationActionReceiver.conscienceIntent(context, NotificationActionReceiver.ACTION_CONSCIENCE_PAUSE)
        )
        ensureChannel(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun baseBuilder(context: Context, voice: ConscienceVoice, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ConscienceLines.title(voice))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    private fun ensureChannel(context: Context): NotificationManager {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.conscience_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.conscience_channel_description)
                enableVibration(false)
            }
        )
        return notificationManager
    }
}
