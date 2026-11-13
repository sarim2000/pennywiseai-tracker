package com.pennywiseai.tracker.data.repository

/**
 * Takes down the "add this?" alert posted for an unrecognized message once the
 * message is handled or deleted, so the alert can't reopen it (#994). `receiver`
 * posts those alerts and implements this (`receiver/ReceiverUnrecognizedSmsAlerts.kt`,
 * bound in `di/NotificationContractsModule.kt`).
 */
interface UnrecognizedSmsAlerts {
    fun cancel(unrecognizedSmsId: Long)

    fun cancelAll()
}
