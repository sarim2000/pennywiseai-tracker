package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Greater Bank SMS messages.
 *
 * Supported formats:
 * - Debit alert: "Your Account XXXX<last4> had a DEBIT transaction of RS. <amount> on <date> at <time>.Available balance is Rs. <balance>: GREATER BANK"
 * - UPI/IMPS transfer: "Your a/c no. XXXXXXXX<last4> is debited for Rs.<amount> on <date> and credited to a/c no. XXXXXXXX<last4> (UPI Ref no <ref>) If Not You? Call ... Greater Bank"
 */
class GreaterBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Greater Bank"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("GRTRBN") ||
                upper.contains("GREATRBN") ||
                upper.contains("GREATERBNK") ||
                upper.contains("GREATERBANK") ||
                upper.contains("GREATER")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Format 1: "RS. 100.00" or "RS.100.00"
        val rsUpperPattern = Regex("""RS\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        rsUpperPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try { BigDecimal(amountStr) } catch (e: NumberFormatException) { null }
        }
        return super.extractAmount(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // "Account XXXX5207"
        val accountPattern = Regex("""Account\s+[X*]+(\d{4})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { return it.groupValues[1] }

        // "a/c no. XXXXXXXX5207"
        val acNoPattern = Regex("""a/c\s+no\.?\s+[X*]+(\d{4})""", RegexOption.IGNORE_CASE)
        acNoPattern.find(message)?.let { return it.groupValues[1] }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Available balance is Rs. 1127.55"
        val balPattern = Regex(
            """[Aa]vailable\s+balance\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balPattern.find(message)?.let { match ->
            val balStr = match.groupValues[1].replace(",", "")
            return try { BigDecimal(balStr) } catch (e: NumberFormatException) { null }
        }
        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // "UPI Ref no 232135417634"
        val upiRefPattern = Regex("""UPI\s+Ref\s+no\s+(\d+)""", RegexOption.IGNORE_CASE)
        upiRefPattern.find(message)?.let { return it.groupValues[1] }

        return super.extractReference(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()

        // UPI transfer to another account
        if (lower.contains("upi ref")) {
            return "Bank Transfer"
        }

        // Generic debit alert with no destination info
        if (lower.contains("debit transaction")) {
            return "Debit Transaction"
        }

        return super.extractMerchant(message, sender)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("debit transaction") || lower.contains("credit transaction")) return true
        return super.isTransactionMessage(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("debit transaction") || lower.contains("is debited") -> TransactionType.EXPENSE
            lower.contains("credit transaction") || lower.contains("is credited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }
}
