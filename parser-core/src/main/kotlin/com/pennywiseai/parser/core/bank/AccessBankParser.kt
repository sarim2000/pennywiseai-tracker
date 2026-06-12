package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Access Bank (Nigeria) SMS messages.
 *
 * Supported format (line-based):
 * ```
 * Debit
 * Amt:NGN20,400.00
 * Acc:146******325
 * Desc:<description>
 * Date:09/06/2026
 * Avail Bal:NGN224,408.56
 * Total:NGN2          (may be truncated — ignored)
 * ```
 * First line is "Debit" (EXPENSE) or "Credit" (INCOME).
 *
 * Sender: AccessBank
 */
class AccessBankParser : BankParser() {

    override fun getBankName() = "Access Bank"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("ACCESSBANK")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        // Must look like the Access Bank line-based alert.
        return Regex("""(?im)^\s*(debit|credit)\b""").containsMatchIn(message) &&
                Regex("""(?i)Amt:\s*NGN""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?im)^\s*debit\b""").containsMatchIn(message) -> TransactionType.EXPENSE
            Regex("""(?im)^\s*credit\b""").containsMatchIn(message) -> TransactionType.INCOME
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
        val match = Regex("""(?i)Avail\s*Bal:\s*NGN\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Use [ \t]* (not \s*) so an empty "Desc:" line can't let the capture cross the
        // newline and grab the following Date:/Avail Bal: line as the merchant.
        val match = Regex("""(?i)Desc:[ \t]*(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        // Account is masked, e.g. "146******325" — use the trailing digit group.
        val match = Regex("""(?i)Acc:\s*[0-9]*\*+([0-9]+)""").find(message)
        if (match != null) {
            return extractLast4Digits(match.groupValues[1])
        }
        // Fallback: unmasked account number.
        val plain = Regex("""(?i)Acc:\s*([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(plain.groupValues[1])
    }
}
