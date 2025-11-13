package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for OneCard credit card SMS messages
 *
 * Supported formats:
 * - Spending: "You've made a booking of Rs. X on MERCHANT on card ending XXXX"
 * - Fuel: "You've fueled up for Rs. X at MERCHANT on card ending XXXX"
 * - General: "You've made a transaction of Rs. X on MERCHANT on card ending XXXX"
 *
 * Common senders: CP-OneCrd-S, ONECRD, OneCard
 */
class OneCardParser : BankParser() {

    override fun getBankName() = "OneCard"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("ONECRD") ||
                normalizedSender.contains("ONECARD") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-ONECRD-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-ONECARD-S$")) ||
                // Other DLT patterns (OTP, Promotional, Govt)
                normalizedSender.matches(Regex("^[A-Z]{2}-ONECRD-[TPG]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-ONECARD-[TPG]$")) ||
                // Legacy patterns without suffix
                normalizedSender.matches(Regex("^[A-Z]{2}-ONECRD$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-ONECARD$")) ||
                // Direct sender IDs
                normalizedSender == "ONECRD" ||
                normalizedSender == "ONECARD"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsed = super.parse(smsBody, sender, timestamp) ?: return null

        // OneCard transactions are always credit card transactions
        // All spending on OneCard should be marked as CREDIT type
        return parsed.copy(
            type = TransactionType.CREDIT
        )
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Generic pattern: "for Rs. X at" - covers most OneCard formats
        // Examples: "fueled up for Rs. X at", "hand-picked groceries for Rs. X at", etc.
        val forAmountPattern = Regex(
            """for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+at""",
            RegexOption.IGNORE_CASE
        )
        forAmountPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern: "of Rs. X on" - for booking/transaction patterns
        val ofAmountPattern = Regex(
            """of\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        ofAmountPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern: "spent Rs. X"
        val spentPattern = Regex(
            """spent\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
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

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "at MERCHANT on card" - for fuel transactions
        val atMerchantOnCardPattern = Regex(
            """at\s+([^•\n]+?)\s+on\s+card""",
            RegexOption.IGNORE_CASE
        )
        atMerchantOnCardPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern: "on MERCHANT on card" - extract merchant between "on" and "on card"
        val merchantPattern = Regex(
            """on\s+([^•\n]+?)\s+on\s+card""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Alternative pattern: "at MERCHANT on"
        val atMerchantPattern = Regex(
            """at\s+([^•\n]+?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        atMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "card ending XXXX" or "card ending XXXX"
        val cardEndingPattern = Regex(
            """card\s+ending\s+[X]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        cardEndingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Alternative pattern: "on card XXXX"
        val onCardPattern = Regex(
            """on\s+card\s+[X]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        onCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("get reward") ||
            lowerMessage.contains("statement") ||
            lowerMessage.contains("due date") ||
            lowerMessage.contains("bill generated")
        ) {
            return false
        }

        // Transaction indicators - OneCard always starts with "You've"
        if (lowerMessage.startsWith("you've") &&
            lowerMessage.contains("on card ending")
        ) {
            return true
        }

        // Additional patterns
        if (lowerMessage.contains("spent") ||
            lowerMessage.contains("made a")
        ) {
            return true
        }

        // Fall back to base class for other checks
        return super.isTransactionMessage(message)
    }
}