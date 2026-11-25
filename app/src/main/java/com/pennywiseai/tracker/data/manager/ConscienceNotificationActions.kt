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

    /** Opens the app: the tap on the Conscience screen's sample. */
    fun openApp(): PendingIntent

    /** "Liked it" ([liked]) or "Not this one" on remark [remarkId], whose line is [lineKey]. */
    fun react(remarkId: Long, lineKey: String, liked: Boolean): PendingIntent
}
