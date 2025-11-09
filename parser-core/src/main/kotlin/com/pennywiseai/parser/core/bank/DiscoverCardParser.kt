package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Discover Card - handles USD credit card transactions
 */
class DiscoverCardParser : BankParser() {

    override fun getBankName() = "Discover Card"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "DISCOVER" ||
                upperSender.contains("DISCOVERCARD") ||
                upperSender == "347268" ||  // DLT sender ID
                upperSender.matches(Regex("""^[A-Z]{2}-DISCOVER-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Discover patterns: "A transaction of $25.00", "transaction of $5.36"
        val patterns = listOf(
            Regex("""transaction of\s+\$([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""A transaction of\s+\$([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""\$([0-9,]+(?:\.[0-9]{2})?)\s+at""", RegexOption.IGNORE_CASE)
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
            lowerMessage.contains("discover card alert") -> TransactionType.EXPENSE
            lowerMessage.contains("transaction of") -> TransactionType.EXPENSE
            lowerMessage.contains("transaction") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "transaction of $25.00 at WWW.XXX.ORG"
        val atPattern =
            Regex("""at\s+([^\s]+(?:\s+[^\s]*)*?)(?:\s+on|\s+Text|$)""", RegexOption.IGNORE_CASE)
        atPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && !merchant.matches(Regex("""\w+\s+\d{1,2},\s+\d{4}"""))) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: More specific for PAYPAL cases "at PAYPAL *SParkXXX"
        val paypalPattern = Regex("""at\s+(PAYPAL\s+\*[^\s]+)""", RegexOption.IGNORE_CASE)
        paypalPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        // Look for dates in the message: "on February 21, 2025" or "on July 20, 2025"
        val datePattern = Regex("""on\s+(\w+\s+\d{1,2},\s+\d{4})""", RegexOption.IGNORE_CASE)
        datePattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip STOP messages
        if (lowerMessage.contains("text stop to end")) {
            // But still process if it has transaction info
            if (!lowerMessage.contains("transaction of")) {
                return false
            }
        }

        // Discover specific transaction keywords
        val discoverTransactionKeywords = listOf(
            "discover card alert:",
            "transaction of",
            "no action needed",
            "see it at https://app.discover.com"
        )

        if (discoverTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}