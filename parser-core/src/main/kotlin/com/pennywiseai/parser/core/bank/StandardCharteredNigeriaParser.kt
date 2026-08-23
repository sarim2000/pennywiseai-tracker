package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Chartered Bank Nigeria SMS messages.
 *
 * Supported format (single line, comma-separated, "Credit Alert!"/"Debit Alert!" prefix):
 * ```
 * Credit Alert! Acct:xxxxxx1234, Amt:NGN1000.00, Desc:<description>, Date:2026-01-15, Bal:NGN1500000.00
 * ```
 * Amounts carry no thousands separators in the observed samples, but the patterns
 * tolerate them anyway.
 *
 * This parser shares its sender with [StandardCharteredBankParser] (India/Pakistan).
 * Both are registered; [BankParserFactory.parse] does content-aware dispatch, so each
 * must gate strictly on its own format. The "<Credit|Debit> Alert!" + "Amt:NGN" shape
 * is unique to the Nigeria alerts and does not appear in the India/Pakistan formats.
 *
 * Sender: StanChart / SCBANK
 */
class StandardCharteredNigeriaParser : BankParser() {

    override fun getBankName() = "Standard Chartered Bank Nigeria"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("SCBANK") ||
                upper.contains("STANCHART") ||
                upper.contains("STANDARDCHARTERED") ||
                upper.contains("STANDARD CHARTERED")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        // Gate tightly: the Nigeria alert always opens with "Credit Alert!"/"Debit Alert!"
        // AND carries an "Amt:NGN" field. This shape does not occur in the
        // India/Pakistan Standard Chartered formats, so the two parsers never overlap.
        return Regex("""(?i)\b(credit|debit)\s+alert!""").containsMatchIn(message) &&
                Regex("""(?i)Amt:\s*NGN""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?i)\bcredit\s+alert!""").containsMatchIn(message) -> TransactionType.INCOME
            Regex("""(?i)\bdebit\s+alert!""").containsMatchIn(message) -> TransactionType.EXPENSE
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
        // Fields are comma-separated on ONE line, so stop at the next ", Date:" field
        // rather than running to end-of-line. Descriptions themselves may contain commas.
        val match = Regex("""(?i)Desc:\s*(.+?)(?=,\s*Date:|$)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        // Masked as "xxxxxx1234" — take the trailing digit group after the x's.
        val match = Regex("""(?i)Acct:\s*x*([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(match.groupValues[1])
    }
}
