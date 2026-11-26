package com.pennywiseai.tracker.presentation.add

import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.navigation.AddTransaction
import com.pennywiseai.tracker.utils.CurrencyFormatter

/**
 * The documented way for automation apps (Tasker, MacroDroid, `adb`) to open Add
 * Transaction pre-filled: [ACTION_ADD_TRANSACTION] with the short extras below, or
 * the same keys as query parameters on `pennywise://add`. See docs/automation.md.
 *
 * Nothing is saved here: the form opens for the user to review and tap Save. The
 * values come from any app, so each one is checked and a bad one is dropped (the
 * field stays empty) rather than refused or trusted.
 */
object AddAutomation {
    const val ACTION_ADD_TRANSACTION = "com.pennywiseai.tracker.action.ADD_TRANSACTION"

    // Public, stable keys: renaming one breaks users' automations.
    const val KEY_AMOUNT = "amount"
    const val KEY_CURRENCY = "currency"
    const val KEY_MERCHANT = "merchant"
    const val KEY_CATEGORY = "category"
    const val KEY_TYPE = "type"
    const val KEY_NOTE = "note"
    const val KEY_ACCOUNT_LAST4 = "account_last4"
    const val KEY_BANK = "bank"

    internal const val MAX_TEXT = 200
    private const val MAX_AMOUNT_CHARS = 32
    private const val MAX_DIGITS = 15
    private val TYPES = setOf("EXPENSE", "INCOME")

    /**
     * The form [read] asks for. [read] returns the value under a key, or null; a
     * number (Tasker types its extras) is taken as its text, any other type is
     * ignored. The long `EXTRA_ADD_*` names are accepted too.
     */
    fun prefill(read: (String) -> Any?): AddTransaction {
        fun text(key: String, legacy: String? = null): String? =
            when (val value = read(key) ?: legacy?.let(read)) {
                is String, is Number -> value.toString().trim().take(MAX_TEXT).ifEmpty { null }
                else -> null
            }
        return AddTransaction(
            amount = text(KEY_AMOUNT, MainActivity.EXTRA_ADD_AMOUNT)?.let(::amount),
            currency = text(KEY_CURRENCY, MainActivity.EXTRA_ADD_CURRENCY)?.uppercase()
                ?.takeIf { it in CurrencyFormatter.getSupportedCurrencies() },
            merchant = text(KEY_MERCHANT, MainActivity.EXTRA_ADD_MERCHANT),
            category = text(KEY_CATEGORY, MainActivity.EXTRA_ADD_CATEGORY),
            type = text(KEY_TYPE, MainActivity.EXTRA_ADD_TYPE)?.uppercase()?.takeIf { it in TYPES },
            note = text(KEY_NOTE),
            accountLast4 = text(KEY_ACCOUNT_LAST4, MainActivity.EXTRA_ADD_LAST4),
            bankName = text(KEY_BANK, MainActivity.EXTRA_ADD_BANK),
        )
    }

    /**
     * A positive decimal as plain text ("12.50"; a number extra's "1.0E7" becomes
     * "10000000"). Bounded, so a huge exponent can't expand into a giant string.
     */
    private fun amount(raw: String): String? = raw.takeIf { it.length <= MAX_AMOUNT_CHARS }
        ?.toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 && it.precision() - it.scale() <= MAX_DIGITS && it.scale() <= MAX_DIGITS }
        ?.toPlainString()
}
