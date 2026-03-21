package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Chase Bank (USA) SMS messages
 *
 * Supported formats:
 * - Transaction: "Card Name: You made a $9.17 transaction with TACO BELL on Mar 17, 2026 at 1:56 PM ET."
 * - Refund: "Card Name: A $X.XX refund was posted..."
 *
 * Sender: 24273 (Chase short code)
 */
class ChaseBankParser : BankParser() {

    override fun getBankName() = "Chase"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized == "24273" ||
                normalized.contains("CHASE")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "$9.17" or "$1,234.56"
        val amountPattern = Regex(
            """\$([0-9,]+(?:\.\d{2})?)"""
        )
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()

        return when {
            lower.contains("refund") -> TransactionType.INCOME
            lower.contains("credit was posted") -> TransactionType.INCOME
            lower.contains("transaction") -> TransactionType.EXPENSE
            lower.contains("purchase") -> TransactionType.EXPENSE
            lower.contains("charged") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "transaction with MERCHANT on"
        val withPattern = Regex(
            """transaction\s+with\s+(.+?)\s+on\s+""",
            RegexOption.IGNORE_CASE
        )
        withPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern: "purchase at MERCHANT on"
        val atPattern = Regex(
            """purchase\s+at\s+(.+?)\s+on\s+""",
            RegexOption.IGNORE_CASE
        )
        atPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // Pattern: "card ending in 1234" or "ending 1234"
        val endingPattern = Regex(
            """ending\s+(?:in\s+)?(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        endingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        // Chase card alerts always mention card names like "Visa", "Mastercard", or "card"
        if (lower.contains("visa") || lower.contains("mastercard") ||
            lower.contains("card ending") || lower.contains("credit card")
        ) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        if (lower.contains("otp") || lower.contains("verification code") ||
            lower.contains("security code")
        ) {
            return false
        }

        val keywords = listOf("transaction", "purchase", "refund", "charged", "credit was posted")
        return keywords.any { lower.contains(it) }
    }
}
