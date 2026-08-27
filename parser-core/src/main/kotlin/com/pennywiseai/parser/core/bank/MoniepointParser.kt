package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Moniepoint MFB (Nigeria) SMS messages.
 *
 * Supported format (line-based, with a CREDIT/DEBIT ALERT header line):
 * ```
 * CREDIT ALERT
 *
 * Acc: 512****904 (Personal)
 * Amt: NGN1,000.00
 * Bal: NGN5,000.00
 * Date: 15/01/26
 * Time: 03:09 PM
 * Desc: Transfer from JANE DOE
 * ```
 * Direction comes from the "CREDIT ALERT" / "DEBIT ALERT" header rather than a
 * DR/CR suffix (GTBank, VFD) or an inline "Credit Alert!" prefix (Standard
 * Chartered Nigeria).
 *
 * The account line carries a parenthesised account label — "(Personal)",
 * "(Business)" — after the masked number, so the account pattern must stop at
 * the digits and not absorb it.
 *
 * Sender: Moniepoint
 */
class MoniepointParser : BankParser() {

    override fun getBankName() = "Moniepoint"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("MONIEPOINT") || upper.contains("MONNIFY")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        // Must carry the alert header AND an Amt line — the defining shape.
        return Regex("""(?i)\b(credit|debit)\s+alert\b""").containsMatchIn(message) &&
                Regex("""(?i)Amt:\s*NGN""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?i)\bcredit\s+alert\b""").containsMatchIn(message) -> TransactionType.INCOME
            Regex("""(?i)\bdebit\s+alert\b""").containsMatchIn(message) -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val match = Regex("""(?i)Amt:\s*NGN\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val match = Regex("""(?i)\bBal:\s*NGN\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Use [ \t]* (not \s*) so an empty "Desc:" line can't cross the newline and
        // capture whatever follows as the merchant.
        val match = Regex("""(?i)Desc:[ \t]*(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        // "Acc: 512****904 (Personal)" — capture the digit/asterisk run only, so the
        // trailing "(Personal)" / "(Business)" label is never absorbed.
        val match = Regex("""(?i)Acc:\s*([0-9*]+)""").find(message) ?: return null
        return extractLast4Digits(match.groupValues[1])
    }
}
