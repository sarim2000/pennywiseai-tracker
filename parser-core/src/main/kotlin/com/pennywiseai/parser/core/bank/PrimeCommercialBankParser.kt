package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class PrimeCommercialBankParser : BankParser() {

    override fun getBankName() = "Prime Commercial Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace("-", "_")
        return normalizedSender.contains("PCBLNPKA") ||
                normalizedSender == "PRIME_ALERT" ||
                normalizedSender.contains("PRIME")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val pattern = Regex("""NPR\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("withdrawn") -> TransactionType.EXPENSE
            lower.contains("deposited") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val pattern = Regex("""Rmk:\s*([^.\s]+)""", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)?.trim()
    }

    override fun extractAccountLast4(message: String): String? {
        val pattern = Regex("""#(\d{4})""")
        return pattern.find(message)?.groupValues?.get(1)
    }

    override fun extractReference(message: String): String? {
        val pattern = Regex("""\bon\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})\b""", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("npr") &&
                (lower.contains("withdrawn") || lower.contains("deposited"))
    }
}
