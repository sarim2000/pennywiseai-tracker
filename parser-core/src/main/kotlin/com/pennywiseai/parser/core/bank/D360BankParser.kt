package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for D360 Bank (Saudi Arabia) — English SMS, SAR-denominated.
 *
 * Handles three known message shapes:
 *  - "International Online Purchase" / card purchases  -> EXPENSE
 *  - "International ATM Withdrawal"                     -> EXPENSE
 *  - "Incoming Transfer: <bank>"                        -> INCOME
 *
 * Foreign-currency transactions carry the local SAR conversion in parentheses,
 * e.g. `Amount: TRY 342.00 (SAR 27.51)`. We record the SAR amount, since that
 * is what the account is actually charged — keeping every D360 transaction in
 * a single currency (SAR).
 */
class D360BankParser : BankParser() {

    override fun getBankName() = "D360 Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        // Senders seen in the wild: "D360Bank", "D360BANK" (and DLT-prefixed variants).
        return sender.uppercase().contains("D360")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Foreign purchase/withdrawal: "Amount: TRY 342.00 (SAR 27.51)" -> take the SAR conversion.
        val convertedPattern = Regex(
            """Amount\s*:\s*[A-Z]{3}\s*[0-9,]+(?:\.\d{1,2})?\s*\(\s*SAR\s*([0-9,]+(?:\.\d{1,2})?)\s*\)""",
            RegexOption.IGNORE_CASE
        )
        convertedPattern.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        // Local transaction: "Amount: SAR 250.00". Anchored to "Amount:" so the
        // "Fee: SAR 0.00" line can never be mistaken for the transaction amount.
        val localPattern = Regex(
            """Amount\s*:\s*SAR\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        localPattern.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        return null
    }

    private fun parseAmount(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(",", ""))
    } catch (e: NumberFormatException) {
        null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("incoming") -> TransactionType.INCOME
            lower.contains("refund") -> TransactionType.INCOME
            lower.contains("purchase") -> TransactionType.EXPENSE
            lower.contains("withdrawal") || lower.contains("withdraw") -> TransactionType.EXPENSE
            lower.contains("outgoing") -> TransactionType.EXPENSE
            lower.contains("payment") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Purchases and ATM withdrawals: "At: FEED ME" / "At: CITY,TR". Matched
        // case-insensitively (tolerates "AT:"), but the "at: <datetime>" line on
        // transfers is skipped by rejecting date-shaped captures.
        Regex("""At\s*:\s*([^\n]+)""", RegexOption.IGNORE_CASE).findAll(message).forEach { match ->
            val candidate = match.groupValues[1].trim()
            if (DATE_LIKE.containsMatchIn(candidate)) return@forEach
            val merchant = cleanMerchantName(candidate)
            if (isValidMerchantName(merchant)) return merchant
        }

        // Transfers: the counterparty is on the title line,
        // "Incoming Transfer: <bank>" / "Outgoing Transfer: <bank>".
        Regex("""(?:Incoming|Outgoing) Transfer\s*:\s*([^\n]+)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) return merchant
            }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "Card: *1234 - VISA" or "Account number: *1234"
        Regex("""\*+(\d{4})\b""").find(message)?.let { return extractLast4Digits(it.groupValues[1]) }
        return super.extractAccountLast4(message)
    }

    override fun detectIsCard(message: String): Boolean {
        if (Regex("""Card\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(message)) return true
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") ||
            lower.contains("verification code") ||
            lower.contains("one time password")
        ) return false

        // Reject promotional messages that happen to mention transaction words
        // (e.g. "Exclusive SAR cashback offer on all transfers!"). The base class
        // filters these, but this override replaces its keyword gate, so guard here.
        val promoMarkers = listOf(
            "offer", "discount", "% off", "sale", "win ", "congratulations",
            "promo", "reward points", "click", "unsubscribe"
        )
        if (promoMarkers.any { lower.contains(it) }) return false

        val keywords = listOf(
            "purchase", "withdrawal", "transfer", "incoming", "outgoing", "amount", "sar"
        )
        return keywords.any { lower.contains(it) }
    }

    private companion object {
        // Matches an ISO-ish datetime ("2026-07-08 18:14") so the transfer
        // "at: <datetime>" line is never mistaken for a merchant/location.
        private val DATE_LIKE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
