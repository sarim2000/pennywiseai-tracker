package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Keystone Bank (Nigeria) SMS messages.
 *
 * Supported format (line-based):
 * ```
 * Debit!                                  (or Credit!)
 * Acct:602****370
 * Amt:NGN-57,000.00                       (debit carries a leading minus)
 * Desc:<description>
 * Date:26-05-2026 0:0
 * Bal:NGN1,929.24
 * Download Keymobile bit.ly/31MJj1s       (promo — ignored)
 * ```
 *
 * Sender: KEYSTONE
 */
class KeystoneBankParser : BankParser() {

    override fun getBankName() = "Keystone Bank"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("KEYSTONE")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        return Regex("""(?im)^\s*(debit|credit)!""").containsMatchIn(message) &&
                Regex("""(?i)Amt:\s*NGN""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?im)^\s*debit!""").containsMatchIn(message) -> TransactionType.EXPENSE
            Regex("""(?im)^\s*credit!""").containsMatchIn(message) -> TransactionType.INCOME
            // Fallback to the sign on the amount line.
            Regex("""(?i)Amt:\s*NGN\s*-""").containsMatchIn(message) -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Strip the leading minus when present; we only need the magnitude.
        val match = Regex("""(?i)Amt:\s*NGN\s*-?\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val match = Regex("""(?i)Bal:\s*NGN\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Use [ \t]* (not \s*) so an empty "Desc:" line can't let the capture cross the
        // newline and grab the following Date:/Bal: line as the merchant.
        val match = Regex("""(?i)Desc:[ \t]*(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        val match = Regex("""(?i)Acct:\s*[0-9]*\*+([0-9]+)""").find(message)
        if (match != null) {
            return extractLast4Digits(match.groupValues[1])
        }
        val plain = Regex("""(?i)Acct:\s*([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(plain.groupValues[1])
    }
}
