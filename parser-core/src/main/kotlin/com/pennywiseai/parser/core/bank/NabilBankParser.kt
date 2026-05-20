package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class NabilBankParser : BankParser() {

    override fun getBankName() = "Nabil Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s.contains("NABIL") || s == "NABIL_ALERT" || s == "NABILBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        val nprPattern = Regex("""NPR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        nprPattern.find(message)?.let { m ->
            val amountStr = m.groupValues[1].replace(",", "")
            return amountStr.toBigDecimalOrNull()
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        if (lower.contains("withdrawn")) return TransactionType.EXPENSE
        if (lower.contains("deposited") || lower.contains("credited")) return TransactionType.INCOME
        return null
    }

    override fun extractReference(message: String): String? {
        val remarks = Regex("""Remarks[:\s]*([A-Z0-9\-~]+)""", RegexOption.IGNORE_CASE)
        remarks.find(message)?.let { return it.groupValues[1] }

        val refPattern = Regex("""(MTXN[0-9A-Z\-]+)""", RegexOption.IGNORE_CASE)
        refPattern.find(message)?.let { return it.groupValues[1] }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        val maskedPattern = Regex("""#+(\d{4,})""")
        maskedPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return super.extractAccountLast4(message)
    }
}
