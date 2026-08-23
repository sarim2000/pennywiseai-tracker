package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for VFD Microfinance Bank (Nigeria) SMS messages.
 *
 * Supported format (line-based, "N" currency prefix, DR/CR suffix on the amount):
 * ```
 * Acct: xxx901
 * Amt: N11,000.00 DR
 * Date: 15-JAN-2026 21:53:32
 * Chgs: N25.00 (COMM VAT)
 * Desc: Fruits/ To JANE DOE
 * Balance:N30,000.00
 * ```
 *
 * Two traps this format sets:
 *  1. A "Chgs:" line carries its OWN N-amount (the fee). Every money pattern here is
 *     anchored to its specific label so the fee can never be read as the transaction
 *     amount or the balance.
 *  2. Currency is "N", not "NGN" — a bare `N` prefix, so patterns must not require NGN.
 *
 * Sender: VFD
 */
class VFDBankParser : BankParser() {

    override fun getBankName() = "VFD Microfinance Bank"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("VFD")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        // Must have an Amt line with a DR/CR suffix — the defining shape of a VFD alert.
        return Regex("""(?i)Amt:\s*N\s*[0-9,]+(?:\.\d{1,2})?\s*(DR|CR)\b""")
            .containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val match = Regex("""(?i)Amt:\s*N\s*[0-9,]+(?:\.\d{1,2})?\s*(DR|CR)\b""")
            .find(message) ?: return null
        return when (match.groupValues[1].uppercase()) {
            "DR" -> TransactionType.EXPENSE
            "CR" -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Anchored to "Amt:" so the "Chgs:" fee amount is never picked up.
        val match = Regex("""(?i)Amt:\s*N\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Balance:N2,341.81" — note no space after the colon in the observed samples.
        val match = Regex("""(?i)Balance:\s*N\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Use [ \t]* (not \s*) so an empty "Desc:" line can't cross the newline and
        // capture the following Balance: line as the merchant.
        val match = Regex("""(?i)Desc:[ \t]*(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        // Masked as "xxx348" — only 3 digits exposed; extractLast4Digits allows >= 3.
        val match = Regex("""(?i)Acct:\s*x*([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(match.groupValues[1])
    }
}
