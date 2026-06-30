package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class NepalBankParser : BankParser() {

    override fun getBankName() = "Nepal Bank Limited"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "NBL_ALERT"
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
        if (lower.contains("debited")) return TransactionType.EXPENSE
        if (lower.contains("credited")) return TransactionType.INCOME
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        val maskedPattern = Regex("""#+(\d{3,})""")
        maskedPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        if (lower.contains("visa")) return "VISA Transaction"
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val refPattern = Regex("""\d{2}:\d{2}:\d{2},\s*([^,\s]+)""")
        refPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }
        return null
    }
}
