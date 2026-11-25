package com.pennywiseai.tracker.receiver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.data.manager.ConscienceNotificationActions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [ConscienceNotificationActions] built by the receivers that handle them. */
class ReceiverConscienceNotificationActions @Inject constructor(
    @ApplicationContext private val context: Context
) : ConscienceNotificationActions {

    override fun openTransaction(transactionId: Long): PendingIntent =
        SmsBroadcastReceiver.openTransactionIntent(context, transactionId)

    override fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun react(remarkId: Long, lineKey: String, liked: Boolean): PendingIntent =
        NotificationActionReceiver.conscienceReactionIntent(context, remarkId, lineKey, liked)

    private companion object {
        // Transaction alerts use the transaction id, which is never negative.
        const val OPEN_APP_REQUEST_CODE = -4
    }
}
