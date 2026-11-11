package com.pennywiseai.tracker.receiver

import android.app.PendingIntent
import android.content.Context
import com.pennywiseai.tracker.data.manager.ConscienceNotificationActions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [ConscienceNotificationActions] built by the receivers that handle them. */
class ReceiverConscienceNotificationActions @Inject constructor(
    @ApplicationContext private val context: Context
) : ConscienceNotificationActions {

    override fun openTransaction(transactionId: Long): PendingIntent =
        SmsBroadcastReceiver.openTransactionIntent(context, transactionId)

    override fun retireLine(lineKey: String): PendingIntent =
        NotificationActionReceiver.conscienceRetireIntent(context, lineKey)
}
