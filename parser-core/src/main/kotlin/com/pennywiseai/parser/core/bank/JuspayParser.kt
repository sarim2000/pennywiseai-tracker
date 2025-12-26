package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Juspay/Amazon Pay wallet transactions.
 * Handles messages from XX-JUSPAY-X, APAY, and similar senders.
 */
class JuspayParser : BaseIndianBankParser() {

    override fun getBankName() = "Amazon Pay"

    override fun getCurrency() = "INR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("JUSPAY") ||
                normalizedSender.contains("APAY") ||
                normalizedSender == "AMAZON PAY"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "Your Apay Wallet balance is debited for INR Xxx"
        val debitPattern =
            Regex("""debited\s+for\s+INR\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        debitPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Payment of Rs xxx using Apay Balance"
        val paymentPattern =
            Regex("""Payment\s+of\s+Rs\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        paymentPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: "Rs xxx" generic pattern
        val genericPattern = Regex("""Rs\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        genericPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 4: "INR xxx" generic pattern
        val inrPattern = Regex("""INR\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        inrPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "successful at merchant" - improved to capture multi-word merchants
        // Captures everything between "successful at" and the period or "Updated Balance"
        val merchantPattern = Regex(
            """successful\s+at\s+(.+?)(?:\.\s*Updated|\s*\.\s*Updated|\.(?:\s|$))""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Pattern 2: Common merchant indicators
        return when {
            lowerMessage.contains("amazon") -> "Amazon"
            lowerMessage.contains("flipkart") -> "Flipkart"
            lowerMessage.contains("swiggy") -> "Swiggy"
            lowerMessage.contains("zomato") -> "Zomato"
            lowerMessage.contains("ola") -> "Ola"
            lowerMessage.contains("uber") -> "Uber"
            lowerMessage.contains("zepto") -> "Zepto"
            lowerMessage.contains("blinkit") -> "Blinkit"
            lowerMessage.contains("apay wallet") -> "Amazon Pay Transaction"
            lowerMessage.contains("wallet") -> "Amazon Pay Transaction"
            else -> super.extractMerchant(message, sender) ?: "Amazon Pay"
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("credited") -> TransactionType.CREDIT
            lowerMessage.contains("refunded") -> TransactionType.CREDIT
            lowerMessage.contains("received") -> TransactionType.CREDIT
            else -> null
        }
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "Transaction Reference Number is 123456789012"
        val refPattern = Regex(
            """Transaction\s+Reference\s+Number\s+is\s+(\d{12})""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "Reference Number: 123456789012"
        val altRefPattern = Regex(
            """Reference\s+(?:Number|No)[:\s]+(\d{12})""",
            RegexOption.IGNORE_CASE
        )
        altRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for transaction keywords
        val transactionKeywords = listOf(
            "debited for",
            "payment of rs",
            "using apay balance",
            "transaction reference number",
            "updated balance is"
        )

        return transactionKeywords.any { lowerMessage.contains(it) } ||
                super.isTransactionMessage(message)
    }
}
