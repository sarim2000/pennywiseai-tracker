package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Jaiz Bank (Nigeria) SMS messages.
 *
 * Supported format (line-based):
 * ```
 * Acct:**737
 * Amt:N50.00DR                            (DR -> EXPENSE, CR -> INCOME)
 * Desc:<description>
 * 04-JAN-26 17:46
 * Help:07007730000                        (promo/support — ignored)
 * Bal:N252.28
 * Buy Airtime, Dial *773*Amount#          (promo — ignored)
 * ```
 * Amounts carry an "N" prefix and a trailing DR/CR suffix.
 *
 * Sender: Jaiz
 */
class JaizBankParser : BankParser() {

    override fun getBankName() = "Jaiz Bank"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("JAIZ")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        return Regex("""(?i)Amt:\s*N\s*[0-9,]+(?:\.\d{1,2})?\s*(?:DR|CR)""").containsMatchIn(message) &&
                Regex("""(?i)Acct:""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?i)Amt:\s*N\s*[0-9,]+(?:\.\d{1,2})?\s*DR""").containsMatchIn(message) -> TransactionType.EXPENSE
            Regex("""(?i)Amt:\s*N\s*[0-9,]+(?:\.\d{1,2})?\s*CR""").containsMatchIn(message) -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val match = Regex("""(?i)Amt:\s*N\s*([0-9,]+(?:\.\d{1,2})?)\s*(?:DR|CR)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val match = Regex("""(?i)Bal:\s*N\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Use [ \t]* (not \s*) so an empty "Desc:" line can't let the capture cross the
        // newline and grab the following date/Bal: line as the merchant.
        val match = Regex("""(?i)Desc:[ \t]*(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        val match = Regex("""(?i)Acct:\s*\*+([0-9]+)""").find(message)
        if (match != null) {
            return extractLast4Digits(match.groupValues[1])
        }
        val plain = Regex("""(?i)Acct:\s*([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(plain.groupValues[1])
    }
}
