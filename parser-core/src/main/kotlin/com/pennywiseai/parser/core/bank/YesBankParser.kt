package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Yes Bank SMS messages
 *
 * Supported formats:
 * - Credit Card UPI: "INR XXX.XX spent on YES BANK Card XXXXX @UPI_MERCHANT DATE TIME. Avl Lmt INR XXX,XXX.XX"
 *
 * Common senders: CP-YESBNK-S, VM-YESBNK-S, JX-YESBNK-S
 */
class YesBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Yes Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        // DLT patterns for Yes Bank (XX-YESBNK-S format)
        return normalizedSender.matches(Regex("^[A-Z]{2}-YESBNK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-YESBNK$")) ||
                normalizedSender == "YESBNK" ||
                normalizedSender == "YESBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern for "INR XXX.XX spent" format
        val inrSpentPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+spent""",
            RegexOption.IGNORE_CASE
        )
        inrSpentPattern.find(message)?.let { match ->
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
        // Pattern for "@UPI_MERCHANT NAME" format
        // Matches everything after @UPI_ until the date pattern (DD-MM-YYYY)
        val upiMerchantPattern = Regex(
            """@UPI_([^0-9]+?)(?:\s+\d{2}-\d{2}-\d{4})""",
            RegexOption.IGNORE_CASE
        )
        upiMerchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Clean up the merchant name
            val cleanedMerchant = merchant
                .replace(Regex("""\s+"""), " ")  // Replace multiple spaces with single space
                .trim()

            if (cleanedMerchant.isNotEmpty()) {
                return cleanedMerchant
            }
        }

        // Alternative pattern if date format is different
        val upiMerchantAltPattern = Regex(
            """@UPI_([A-Z\s]+)""",
            RegexOption.IGNORE_CASE
        )
        upiMerchantAltPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Clean up the merchant name
            val cleanedMerchant = merchant
                .replace(Regex("""\s+"""), " ")  // Replace multiple spaces with single space
                .trim()

            if (cleanedMerchant.isNotEmpty() && isValidMerchantName(cleanedMerchant)) {
                return cleanedMerchant
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern for "YES BANK Card XXXXX" where X can be X or actual digit
        val cardPattern = Regex(
            """YES\s+BANK\s+Card\s+[X]*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            val cardNumber = match.groupValues[1]
            return if (cardNumber.length >= 4) {
                cardNumber.takeLast(4)
            } else {
                cardNumber
            }
        }

        // Pattern for SMS BLKCC instruction (contains last 4 digits)
        val blkccPattern = Regex(
            """SMS\s+BLKCC\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        blkccPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Pattern for "Avl Lmt INR XXX,XXX.XX"
        val avlLmtPattern = Regex(
            """Avl\s+Lmt\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        avlLmtPattern.find(message)?.let { match ->
            val limitStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(limitStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAvailableLimit(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Check for investment transactions first
        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }

        // Yes Bank credit card transactions have "spent" and "Avl Lmt"
        if (lowerMessage.contains("spent") &&
            lowerMessage.contains("yes bank card") &&
            lowerMessage.contains("avl lmt")
        ) {
            return TransactionType.CREDIT
        }

        // Check for other transaction patterns
        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE

            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            else -> null
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and non-transaction messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("verification") ||
            lowerMessage.contains("one time password")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("discount")
        ) {
            return false
        }

        // Check for Yes Bank specific transaction keywords
        val yesBankKeywords = listOf(
            "spent on yes bank card",
            "debited",
            "credited",
            "withdrawn",
            "deposited",
            "avl lmt"  // Available limit indicates a transaction
        )

        // If any Yes Bank specific pattern is found, it's likely a transaction
        if (yesBankKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Yes Bank card patterns
        if (lowerMessage.contains("yes bank card")) {
            return true
        }

        // SMS BLKCC (Block Credit Card) instruction indicates card transaction
        if (lowerMessage.contains("sms blkcc")) {
            return true
        }

        // Fall back to base class
        return super.detectIsCard(message)
    }
}