package com.pennywiseai.tracker.receiver

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import com.pennywiseai.parser.core.bank.BankParserFactory

/**
 * Configuration and helpers for ingesting bank notifications.
 *
 * Only notifications from the allowed package list are processed for
 * transaction parsing to preserve user privacy.
 */
object BankNotificationConfig {

    internal val allowedPackages: Map<String, String> = mapOf(
        // Faysal Bank (Pakistan) – alias must match FaysalBankParser.canHandle()
        "com.avanza.ambitwizfbl" to "FaysalBank",
        // Enpara (Turkey) – alias must match EnparaBankParser.canHandle()
        "finansbank.enpara" to "Enpara",      // Enpara.com Cep Şubesi (personal, older brand)
        "com.enparabank.retail" to "Enpara",  // Enpara Bank Cep Şube (personal, post-rebrand)
        // slice (India) – alias must match SliceParser.canHandle()
        "indwin.c3.shareapp" to "slice",
        // Notification-only providers (UK) – aliases must match their parsers
        "com.chase.intl" to "ChaseUK",        // Chase UK — distinct from the US Chase parser
        "com.avuscapital.trading212" to "Trading212",
        // Huntington (USA) – app notifications use the same wording as its texts;
        // alias must match HuntingtonBankParser.canHandle()
        "com.huntington.m" to "Huntington",   // Huntington Mobile Banking
        // Dubai First (UAE credit cards, a FAB brand) – alias must match DubaiFirstParser.canHandle()
        "com.bankfab.pbg.ae.dubaifirst" to "DubaiFirst"
    )

    /** Sender aliases notification-booked transactions are saved under. */
    val notificationAliases: Set<String> by lazy { allowedPackages.values.toSet() }

    /**
     * Banks that can arrive by notification. Only these can have a cross-channel
     * duplicate, so the (unindexed) nearby lookup is skipped for every other bank.
     */
    val notificationBankNames: Set<String> by lazy {
        notificationAliases.mapNotNull { BankParserFactory.getParser(it)?.getBankName() }.toSet()
    }

    /**
     * How to show a suggestion's sender: a tracked app (#994) is stored by package,
     * so show its name ("Robinhood"). An SMS sender isn't a package and stays as is.
     */
    fun displayName(context: Context, sender: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(sender, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        sender
    }

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

        val merged = textParts.joinToString("\n") { it.toString() }.trim()
        return withTitle(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(), merged)
    }

    /**
     * Keeps the title with the body: some apps put the heading ("Card Transaction",
     * "… Refund") in the title and only the fields in the text, and the heading is
     * what tells a purchase from a refund.
     */
    internal fun withTitle(title: String?, body: String): String {
        val t = title?.trim().orEmpty()
        return when {
            body.isBlank() -> t
            t.isEmpty() || body.contains(t) -> body
            else -> "$t\n$body"
        }
    }
}
