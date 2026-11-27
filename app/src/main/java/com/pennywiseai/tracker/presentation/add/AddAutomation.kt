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
        // Given at all, even blank or of the wrong type: then only a currency we can use keeps the amount.
        val currencyGiven = (read(KEY_CURRENCY) ?: read(MainActivity.EXTRA_ADD_CURRENCY)) != null
        val currency = text(KEY_CURRENCY, MainActivity.EXTRA_ADD_CURRENCY)?.uppercase()
            ?.takeIf { it in CurrencyFormatter.getSupportedCurrencies() }
        return AddTransaction(
            // An amount in a currency we can't use isn't kept: the form would show it in the base currency.
            amount = text(KEY_AMOUNT, MainActivity.EXTRA_ADD_AMOUNT)?.let(::amount)
                ?.takeIf { !currencyGiven || currency != null },
            currency = currency,
            merchant = text(KEY_MERCHANT, MainActivity.EXTRA_ADD_MERCHANT),
            category = text(KEY_CATEGORY, MainActivity.EXTRA_ADD_CATEGORY),
            type = text(KEY_TYPE, MainActivity.EXTRA_ADD_TYPE)?.uppercase()?.takeIf { it in TYPES },
            note = text(KEY_NOTE),
            accountLast4 = text(KEY_ACCOUNT_LAST4, MainActivity.EXTRA_ADD_LAST4),
            bankName = text(KEY_BANK, MainActivity.EXTRA_ADD_BANK),
        )
    }

    /**
     * [add]'s values under the public keys, all as text: the only extras
     * [AddAutomationActivity] passes on, and [prefill] reads them back unchanged.
     */
    fun extras(add: AddTransaction): Map<String, String> = listOfNotNull(
        add.amount?.let { KEY_AMOUNT to it },
        add.currency?.let { KEY_CURRENCY to it },
        add.merchant?.let { KEY_MERCHANT to it },
        add.category?.let { KEY_CATEGORY to it },
        add.type?.let { KEY_TYPE to it },
        add.note?.let { KEY_NOTE to it },
        add.accountLast4?.let { KEY_ACCOUNT_LAST4 to it },
        add.bankName?.let { KEY_BANK to it },
    ).toMap()

    /**
     * A positive decimal as plain text ("12.50"; a number extra's "1.0E7" becomes
     * "10000000"). Bounded, so a huge exponent can't expand into a giant string.
     */
    private fun amount(raw: String): String? = raw.takeIf { it.length <= MAX_AMOUNT_CHARS }
        ?.toBigDecimalOrNull()
        // Long math: "1E2147483647" has scale Int.MIN_VALUE + 1, and in Int precision - scale wraps negative.
        ?.takeIf { it.signum() > 0 && it.precision().toLong() - it.scale() <= MAX_DIGITS && it.scale() <= MAX_DIGITS }
        ?.toPlainString()
}
