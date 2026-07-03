package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class CitizensBankParser : BankParser() {

    override fun getBankName() = "Citizens Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "CTZN_ALERT"
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
        if (lower.contains("is debited")) return TransactionType.EXPENSE
        if (lower.contains("is credited")) return TransactionType.INCOME
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        val maskedPattern = Regex("""#+(\d{4})""")
        maskedPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val remarksPattern = Regex("""Remarks:\s*(.+?)(?:\s+Av\s+Bal\s*:|$)""", RegexOption.IGNORE_CASE)
        remarksPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            val upper = raw.uppercase()
            if (upper.startsWith("ATM")) return "ATM Withdrawal"
            if (upper.contains("VISA")) return "VISA Transaction"
            val merchant = cleanMerchantName(
                if (raw.contains("/")) raw.substringBefore("/") else raw
            )
            if (isValidMerchantName(merchant)) return merchant
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val remarks = Regex("""Remarks:\s*(.+?)(?:\s+Av\s+Bal\s*:|$)""", RegexOption.IGNORE_CASE)
        remarks.find(message)?.let { return it.groupValues[1].trim() }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val avBalPattern = Regex("""Av\s+Bal[:\s]+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        avBalPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return super.extractBalance(message)
    }
}
