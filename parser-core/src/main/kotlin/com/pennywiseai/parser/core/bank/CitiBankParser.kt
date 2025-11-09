package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Citi Bank (USA) - handles USD credit card transactions
 */
class CitiBankParser : BankParser() {

    override fun getBankName() = "Citi Bank"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "CITI" ||
                upperSender.contains("CITIBANK") ||
                upperSender == "692484" ||  // DLT sender ID
                upperSender.matches(Regex("""^[A-Z]{2}-CITI-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Citi patterns: "A $3.01 transaction", "$506.39 transaction"
        val patterns = listOf(
            Regex("""\$([0-9,]+(?:\.[0-9]{2})?)\s+transaction""", RegexOption.IGNORE_CASE),
            Regex("""transaction.*?\$([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""A\s+\$([0-9,]+(?:\.[0-9]{2})?)\s+transaction""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit card transactions are expenses
            lowerMessage.contains("transaction was made") -> TransactionType.EXPENSE
            lowerMessage.contains("card ending") -> TransactionType.EXPENSE
            lowerMessage.contains("was not present") -> TransactionType.EXPENSE // Card not present transactions
            lowerMessage.contains("transaction") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "transaction was made at BP#1234E"
        val atPattern =
            Regex("""transaction was made at\s+([^.]+?)(?:\s+on|$)""", RegexOption.IGNORE_CASE)
        atPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: "transaction at WWW Google C"
        val transactionAtPattern =
            Regex("""transaction at\s+([^.]+?)(?:\s+View|\.|$)""", RegexOption.IGNORE_CASE)
        transactionAtPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "card ending in 1234"
        val cardPattern = Regex("""card ending in\s+(\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Look for dates in the message
        val datePattern =
            Regex("""on\s+(card ending|\w+\s+\d{1,2},\s+\d{4})""", RegexOption.IGNORE_CASE)
        datePattern.find(message)?.let { match ->
            if (!match.groupValues[1].contains("card ending")) {
                return match.groupValues[1]
            }
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Citi specific transaction keywords
        val citiTransactionKeywords = listOf(
            "citi alert:",
            "transaction was made",
            "card ending",
            "was not present for",
            "view details at citi.com"
        )

        if (citiTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}