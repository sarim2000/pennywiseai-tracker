package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class ManjushreeFinanceParser : BankParser() {

    override fun getBankName() = "Manjushree Finance"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "MFL" || s == "MFL_ALERT" || s.contains("MANJUSHREE")
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
        if (lower.contains("debited") || lower.contains("debited by")) return TransactionType.EXPENSE
        if (lower.contains("credited") || lower.contains("deposited")) return TransactionType.INCOME
        return null
    }

    override fun extractReference(message: String): String? {
        val remarks = Regex("""Remarks[:\s]*([^,]+)""", RegexOption.IGNORE_CASE)
        remarks.find(message)?.let { return it.groupValues[1].trim() }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val transfer = Regex("""transfer\s+([^,~\n]+)""", RegexOption.IGNORE_CASE)
        transfer.find(message)?.let {
            val name = it.groupValues[1].trim()
            val cleaned = cleanMerchantName(name)
            if (isValidMerchantName(cleaned)) return cleaned
        }

        return super.extractMerchant(message, sender)
    }
}
