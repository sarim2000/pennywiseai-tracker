package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Chartered Bank Nepal (SC_ALERT) SMS messages.
 *
 * Known limitation: based on available SMS samples, neither reference numbers
 * nor balance information appear in SC Nepal messages. If real-world messages
 * include these fields, extractReference and extractBalance overrides should
 * be added.
 */
class StandardCharteredNepalParser : BankParser() {

    override fun getBankName() = "Standard Chartered Bank Nepal"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "SC_ALERT"
    }

    override fun extractAmount(message: String): BigDecimal? {
        val nprPattern = Regex("""NPR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        nprPattern.find(message)?.let { m ->
            return m.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        if (lower.contains("debited from")) return TransactionType.EXPENSE
        if (lower.contains("deposited into")) return TransactionType.INCOME
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        val accountPattern = Regex("""your account\s+(\d{4,})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        if (lower.contains("atm")) return "ATM Withdrawal"
        if (lower.contains("visa")) return "VISA Transaction"
        return super.extractMerchant(message, sender)
    }
}
