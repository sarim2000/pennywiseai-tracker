package com.pennywiseai.tracker.receiver

import android.app.Notification

/**
 * Configuration and helpers for ingesting bank notifications.
 *
 * Only notifications from the allowed package list are processed for
 * transaction parsing to preserve user privacy.
 */
object BankNotificationConfig {

    private val allowedPackages: Map<String, String> = mapOf(
        // Faysal Bank (Pakistan)
        "com.avanza.ambitwizfbl" to "com.avanza.ambitwizfbl"
    )

    fun isAllowed(packageName: String): Boolean =
        allowedPackages.containsKey(packageName.lowercase())

    fun senderAlias(packageName: String): String =
        allowedPackages[packageName.lowercase()] ?: packageName

    fun extractMessage(notification: Notification): String {
        val extras = notification.extras ?: return ""

        val textParts = buildList {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it) }
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { add(it) }
        }

        if (textParts.isNotEmpty()) {
            val merged = textParts.joinToString("\n") { it.toString() }.trim()
            if (merged.isNotBlank()) return merged
        }

        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    }
}
