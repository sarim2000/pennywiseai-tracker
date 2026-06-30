package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class MachchhapuchreBankParser : BankParser() {

    override fun getBankName() = "Machchhapuchchhre Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "MBL_ALERT"
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
        if (lower.contains("withdrawn from")) return TransactionType.EXPENSE
        if (lower.contains("deposited in")) return TransactionType.INCOME
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
        val remarksPattern = Regex("""Remarks:\s*(.+?)(?:\s+Available\s+Bal\s*:|$)""", RegexOption.IGNORE_CASE)
        remarksPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            val merchant = cleanMerchantName(
                if (raw.contains(",")) raw.substringBefore(",") else raw
            )
            if (isValidMerchantName(merchant)) return merchant
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val remarks = Regex("""Remarks:\s*(.+?)(?:\s+Available\s+Bal\s*:|$)""", RegexOption.IGNORE_CASE)
        remarks.find(message)?.let { return it.groupValues[1].trim() }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val pattern = Regex("""Available\s+Bal[:\s]+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return super.extractBalance(message)
    }
}
