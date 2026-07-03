package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class NepalSBIBankParser : BankParser() {

    override fun getBankName() = "Nepal SBI Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "NSBI_ALERT"
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
        if (lower.contains("debited by")) return TransactionType.EXPENSE
        if (lower.contains("credited by")) return TransactionType.INCOME
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        val accountPattern = Regex("""[X#]+\s*(\d{4})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        if (lower.contains("atm")) return "ATM Withdrawal"
        val refPattern = Regex("""Ref:\s*([^.,\n]+)""", RegexOption.IGNORE_CASE)
        refPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val refPattern = Regex("""Ref:\s*([^.,\n]+)""", RegexOption.IGNORE_CASE)
        refPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }
        return null
    }
}
