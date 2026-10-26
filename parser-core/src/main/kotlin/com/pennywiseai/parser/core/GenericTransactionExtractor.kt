package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.BankParser
import java.math.BigDecimal
import java.util.Currency

/**
 * Reads a likely transaction out of an SMS that no bank parser claims (#994), so
 * the app can SUGGEST it. Not a bank parser: never registered in
 * BankParserFactory, and nothing it returns is saved until the user confirms it.
 *
 * Confidence (0–100): a currency-tagged amount plus a debit/credit verb is the
 * base, [BASE]; an account/card last-4 adds 20, a merchant 20, a balance or
 * available limit 10. Without both an amount and a direction there is no result.
 *
 * ponytail: naive regex heuristics, English wording and "1,234.56" numbers only
 * ("1.234,56" reads wrong). Per-sender learning (#994 step 3) is the upgrade path.
 */
object GenericTransactionExtractor {
    /** The confidence worth showing as a suggestion: the base plus one corroborating field. */
    const val SUGGEST_AT = 60
    private const val BASE = 50

    data class Suggestion(val transaction: ParsedTransaction, val confidence: Int)

    fun extract(sender: String, body: String, timestamp: Long = 0L): Suggestion? {
        if (PERSONAL_NUMBER.matches(sender.replace(" ", "")) || Base.isNonTransaction(body)) return null

        val money = MONEY.findAll(body).mapNotNull { printed(it, body) }.toList()
        val main = money.firstOrNull { it.figure == null } ?: return null
        val type = DIRECTION.find(body)
            ?.let { if (it.groups[1] != null) TransactionType.EXPENSE else TransactionType.INCOME }
            ?: return null

        // Only figures in the transaction's own currency: a balance is never mixed with another currency.
        fun figure(kind: String) = money.firstOrNull { it.figure == kind && it.currency == main.currency }?.amount
        val balance = figure(BAL)
        val limit = figure(LIMIT)
        val last4 = Base.last4(body) ?: MASKED_LAST4.find(body)?.groupValues?.get(1)
        val merchant = merchant(body)

        val confidence = BASE +
            (if (last4 != null) 20 else 0) +
            (if (merchant != null) 20 else 0) +
            (if (balance != null || limit != null) 10 else 0)

        return Suggestion(
            ParsedTransaction(
                amount = main.amount,
                type = type,
                merchant = merchant,
                reference = null,
                accountLast4 = last4,
                balance = balance,
                creditLimit = limit,
                smsBody = body,
                sender = sender,
                timestamp = timestamp,
                bankName = sender,
                currency = main.currency
            ),
            confidence
        )
    }

    private data class Printed(val amount: BigDecimal, val currency: String, val figure: String?)

    private const val BAL = "balance"
    private const val LIMIT = "limit"

    private fun printed(m: MatchResult, body: String): Printed? {
        val tag = (m.groups[1] ?: m.groups[2] ?: m.groups[5])?.value ?: return null
        val number = (m.groups[3] ?: m.groups[4])?.value?.replace(",", "") ?: return null
        val amount = try { BigDecimal(number) } catch (e: NumberFormatException) { return null }
        if (amount.signum() <= 0) return null
        val currency = currencyOf(tag) ?: return null
        val before = body.substring(maxOf(0, m.range.first - 30), m.range.first)
        val figure = when {
            BALANCE_BEFORE.containsMatchIn(before) -> BAL
            LIMIT_BEFORE.containsMatchIn(before) -> LIMIT
            else -> null
        }
        return Printed(amount, currency, figure)
    }

    private fun currencyOf(tag: String): String? {
        SYMBOLS[tag]?.let { return it }
        if (tag.equals("rs", ignoreCase = true)) return "INR"
        if (tag == "XXX") return null // ISO's "no currency", and the usual account mask
        return try {
            Currency.getInstance(tag).currencyCode
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun merchant(body: String): String? = MERCHANT.findAll(body)
        .map { Base.clean(it.groupValues[1].trim()) }
        .firstOrNull { !NOT_MERCHANT.containsMatchIn(it) && Base.isValidMerchant(it) }

    /** BankParser's shared checks, reused rather than copied. */
    private object Base : BankParser() {
        override fun getBankName() = ""
        override fun canHandle(sender: String) = false
        fun isNonTransaction(body: String) = isNonTransactionMessage(body)
        fun last4(body: String) = extractAccountLast4(body)
        fun clean(name: String) = cleanMerchantName(name)
        fun isValidMerchant(name: String) = isValidMerchantName(name)
    }

    // "₹" alone is INR; "$" is read as USD, the most common dollar in SMS.
    private val SYMBOLS = mapOf("₹" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP")

    private const val NUMBER = """(\d[\d,]{0,15}(?:\.\d{1,2})?)(?!\.?\d)"""

    // A code (1) or symbol (2) before the number (3), or a code (5) after it (4).
    // Codes stay upper case so "try 2 times" never reads as TRY 2.
    private val MONEY = Regex("""(?:\b([Rr][Ss]|[A-Z]{3})\.?|([₹$€£]))\s*$NUMBER|(?<![\w.,])$NUMBER\s*([A-Z]{3})\b""")

    // The figure is a balance / limit when that word is just before it, with no other number between.
    private val BALANCE_BEFORE = Regex("""\b(?:bal|balance)\b[^\d]*$""", RegexOption.IGNORE_CASE)
    private val LIMIT_BEFORE = Regex("""\b(?:limit|lmt)\b[^\d]*$""", RegexOption.IGNORE_CASE)

    // The first direction word wins: group 1 is money out, group 2 money in.
    private val DIRECTION = Regex(
        """\b(?:(debited|spent|purchased?|paid|withdrawn|charged|deducted|sent|used|transferred to)|""" +
            """(credited|received|deposited|refunded|refund|reversed))\b""",
        RegexOption.IGNORE_CASE
    )

    private val MASKED_LAST4 = Regex("""(?:[Xx*•]{2,}|\.{2,})(\d{4})\b""")

    private val MERCHANT = Regex(
        """\b(?:at|to|from|towards)\s+([A-Za-z0-9&'][^,;:\n]*?)""" +
            """(?=\s+(?:on|at|via|using|with|ref|for|dated|avl|avail|available|bal|balance|txn|from|to)\b|\.(?:\s|$)|[,;:\n]|$)""",
        RegexOption.IGNORE_CASE
    )

    // "credited to your a/c XX1234" names an account, not a counterparty.
    private val NOT_MERCHANT = Regex("""^(?:your|you|a/c|ac|acct|account|card|the)\b|\d{3,}""", RegexOption.IGNORE_CASE)

    // A phone number sender is a person, not a bank.
    private val PERSONAL_NUMBER = Regex("""\+?\d{10,}""")
}
