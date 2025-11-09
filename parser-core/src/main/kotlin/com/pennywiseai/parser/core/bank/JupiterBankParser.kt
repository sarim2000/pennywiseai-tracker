package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Parser for Jupiter Bank (CSB Bank partner) SMS messages
 *
 * Jupiter is a digital banking app powered by CSB Bank.
 *
 * Supported formats:
 * - Credit card transactions: "Rs.130.00 debited to your Edge CSB Bank RuPay Credit Card"
 * - UPI transactions
 * - Account debits/credits
 *
 * Common senders: JTEDGE, JUPITER, variations with DLT patterns
 */
class JupiterBankParser : BankParser() {

    override fun getBankName() = "Jupiter"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.matches(Regex("^[A-Z]{2}-JTEDGE-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-JTEDGE-T$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-JTEDGE$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "Rs.130.00 debited"
        val debitPattern = Regex(
            """Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+debited""",
            RegexOption.IGNORE_CASE
        )
        debitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Rs.XXX credited"
        val creditPattern = Regex(
            """Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+credited""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
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
        // For Jupiter/CSB credit card transactions, the merchant info is usually not in the message
        // These are typically just marked as credit card transactions

        val lowerMessage = message.lowercase()

        // Check for specific transaction types
        return when {
            lowerMessage.contains("edge csb bank rupay credit card") -> "Credit Card Payment"
            lowerMessage.contains("jupiter csb edge") -> "Credit Card Payment"
            lowerMessage.contains("credit card") -> "Credit Card Payment"
            lowerMessage.contains("upi") -> "UPI Transaction"
            else -> super.extractMerchant(message, sender) ?: "Jupiter Transaction"
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "ending 6852"
        val endingPattern = Regex(
            """ending\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        endingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "Card ending 6852"
        val cardEndingPattern = Regex(
            """Card\s+ending\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        cardEndingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern: "UPI Ref no.281751568470"
        val upiRefPattern = Regex(
            """UPI\s+Ref\s+no\.?\s*([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip dispute instructions (not a transaction)
        if (lowerMessage.contains("to dispute") && lowerMessage.contains("call")) {
            // This is just instruction text, don't skip the entire message
        }

        // Check for Jupiter-specific transaction keywords
        if (lowerMessage.contains("jupiter") || lowerMessage.contains("csb")) {
            // If it's from Jupiter/CSB and has transaction keywords, it's likely valid
            return super.isTransactionMessage(message)
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }
}
