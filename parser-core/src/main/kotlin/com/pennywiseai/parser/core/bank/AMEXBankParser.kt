package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for American Express (AMEX) card SMS messages
 *
 * Supported formats:
 * - Spending: "Alert: You've spent INR 1,017.70 on your AMEX card ** 91000 at VOUCHER PLAT on 20 August 2025"
 *
 * Common senders: TX-AMEXIN-S, AMEXIN, AMEX
 */
class AMEXBankParser : BankParser() {

    override fun getBankName() = "American Express"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("AMEX") ||
                normalizedSender.contains("AMEXIN") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-AMEXIN-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AMEX-S$")) ||
                // Other DLT patterns (OTP, Promotional, Govt)
                normalizedSender.matches(Regex("^[A-Z]{2}-AMEXIN-[TPG]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AMEX-[TPG]$")) ||
                // Legacy patterns without suffix
                normalizedSender.matches(Regex("^[A-Z]{2}-AMEXIN$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AMEX$")) ||
                // Direct sender IDs
                normalizedSender == "AMEXIN" ||
                normalizedSender == "AMEX"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsed = super.parse(smsBody, sender, timestamp) ?: return null

        // AMEX transactions are always credit card transactions
        // All spending on AMEX cards should be marked as CREDIT type
        return parsed.copy(
            type = TransactionType.CREDIT
        )
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "You've spent INR 1,017.70" or "spent INR 1,017.70"
        val spentPattern = Regex(
            """spent\s+INR\s+([0-9,]+(?:\.\d{2})?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        spentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern for other possible formats: "INR 1,017.70 spent"
        val altSpentPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+spent""",
            RegexOption.IGNORE_CASE
        )
        altSpentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "at VOUCHER PLAT on 20 August"
        val merchantPattern = Regex(
            """at\s+([^•\n]+?)\s+on\s+\d{1,2}\s+\w+""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "AMEX card ** 91000" - extract the last part
        val cardPattern = Regex(
            """AMEX\s+card\s+\*+\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            val cardNumber = match.groupValues[1]
            // If it's a 5-digit number, take last 4
            return if (cardNumber.length >= 4) {
                cardNumber.takeLast(4)
            } else {
                cardNumber
            }
        }

        // Alternative pattern: "card ending XXXX"
        val endingPattern = Regex(
            """card\s+ending\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        endingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("reward") ||
            lowerMessage.contains("membership") ||
            lowerMessage.contains("statement") ||
            lowerMessage.contains("due date")
        ) {
            return false
        }

        // Fall back to base class for other checks
        return super.isTransactionMessage(message)
    }
}