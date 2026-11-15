package com.pennywiseai.tracker.data.manager

import android.app.PendingIntent

/**
 * The taps on a conscience remark. `receiver` implements it
 * (`receiver/ReceiverConscienceNotificationActions.kt`, bound in
 * `di/NotificationContractsModule.kt`), since the intents target its receiver and
 * share its request-code space, so `data` builds the notification without
 * importing `receiver`.
 */
interface ConscienceNotificationActions {
    /** Opens the transaction the remark is about. */
    fun openTransaction(transactionId: Long): PendingIntent

    /** "Not this one": retires the line so it isn't said again. */
    fun retireLine(lineKey: String): PendingIntent
}
