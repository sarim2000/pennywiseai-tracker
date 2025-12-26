package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Liv Bank (UAE) - Digital bank
 * Inherits from FABParser for multi-currency support.
 * Handles AED and other currency transactions.
 *
 * Example SMS formats:
 * - Credit: "AED 3,586.96 has been credited to account 095XXX71XXXO1. Current balance is AED 4,377.01."
 * - Debit: "Purchase of AED 33.00 with Debit Card ending 4878 at JABAL HAFEET HAIRDRESS, Sharjah. Avl Balance is AED 4,344.01."
 * - Multi-currency: "Purchase of USD 100.00 with Debit Card ending 4878 at AMAZON.COM. Avl Balance is AED 4,244.01."
 */
class LivBankParser : UAEBankParser() {

    override fun getBankName() = "Liv Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace(Regex("\\s+"), "")
        return normalizedSender == "LIV" ||
                normalizedSender.contains("LIV") ||
                // DLT patterns for UAE (if they exist)
                normalizedSender.matches(Regex("^[A-Z]{2}-LIV-[A-Z]$"))
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip non-transaction messages specific to Liv
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code") ||
            lowerMessage.contains("do not share") ||
            lowerMessage.contains("activation") ||
            lowerMessage.contains("has been blocked") ||
            lowerMessage.contains("has been activated") ||
            lowerMessage.contains("failed") ||
            lowerMessage.contains("declined") ||
            lowerMessage.contains("insufficient balance")
        ) {
            return false
        }

        // Liv-specific transaction indicators
        val livTransactionKeywords = listOf(
            "has been credited",
            "purchase of",
            "debit card ending",
            "credit card ending"
        )

        if (livTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fallback to base class transaction detection
        return super.isTransactionMessage(message)
    }

    // extractAmount handled by UAEBankParser

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern for purchases: "at MERCHANT_NAME"
        // This allows dots in merchant names (like AMAZON.COM) but stops at comma or "Avl Balance" or period with space
        if (lowerMessage.contains("purchase of")) {
            // Pattern: Match everything after "at " until we hit a comma, " Avl", or ". " (period with space)
            val merchantPattern = Regex("""at\s+([^,]+?)(?:,|\s+Avl|\.\s)""", RegexOption.IGNORE_CASE)
            merchantPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.contains("Avl Balance")) {
                    return cleanMerchantName(merchant)
                }
            }

            // Fallback: Match up to just "Avl" or end of merchant before punctuation
            val fallbackPattern = Regex("""at\s+([^.]+?)(?:\s+Avl|,)""", RegexOption.IGNORE_CASE)
            fallbackPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Credit transactions might not have merchant
        if (lowerMessage.contains("has been credited")) {
            return "Account Credit"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Debit Card ending XXXX" or "Credit Card ending XXXX"
        val cardPattern = Regex("""(?:Debit|Credit)\s+Card ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let {
            return it.groupValues[1]
        }

        // Pattern 2: Account number (may be alphanumeric)
        // "account 095XXX71XXXO1" - extract last 4 characters (can include letters)
        val accountPattern = Regex("""account\s+[0-9X]+([0-9A-Z]{2,4})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            val last4 = match.groupValues[1]
            // Filter out all X's, keep only actual digits/letters
            val cleanLast4 = last4.replace("X", "", ignoreCase = true)
            if (cleanLast4.isNotEmpty()) {
                return cleanLast4.takeLast(4)
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Liv Bank balance patterns: Support multi-currency
        val balancePatterns = listOf(
            // "Current balance is CURRENCY X,XXX.XX"
            Regex("""Current balance is\s+([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // "Avl Balance is CURRENCY X,XXX.XX"
            Regex("""Avl Balance is\s+([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // Generic "Balance: CURRENCY X,XXX.XX"
            Regex("""Balance:?\s+([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[2].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        // Fallback to FAB's multi-currency balance extraction
        return super.extractBalance(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credits/Income
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("credited to account") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("cashback") -> TransactionType.INCOME

            // Purchases/Expenses
            lowerMessage.contains("purchase of") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE

            // Fallback to base class logic
            else -> super.extractTransactionType(message)
        }
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Liv-specific card indicators
        return lowerMessage.contains("debit card ending") ||
                lowerMessage.contains("credit card ending") ||
                lowerMessage.contains("purchase of") ||  // Liv Bank uses this for card purchases
                super.detectIsCard(message)
    }

    override fun containsCardPurchase(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Liv Bank uses "Purchase of" with "Debit Card ending" or "Credit Card ending"
        return (lowerMessage.contains("purchase of") &&
                (lowerMessage.contains("debit card ending") || lowerMessage.contains("credit card ending"))) ||
                super.containsCardPurchase(message)
    }

    override fun extractCurrency(message: String): String? {
        // Extract currency from the transaction context for Liv Bank
        val currencyPatterns = listOf(
            // "Purchase of CURRENCY amount"
            Regex("""purchase of\s+([A-Z]{3})\s+[\d,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "CURRENCY amount has been credited"
            Regex("""([A-Z]{3})\s+[\d,]+(?:\.\d{2})?[\s\n]+has been credited""", RegexOption.IGNORE_CASE),

            // Generic pattern - CURRENCY followed by amount
            Regex("""([A-Z]{3})\s+[\d,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in currencyPatterns) {
            pattern.find(message)?.let { match ->
                val currencyCode = match.groupValues[1].uppercase()

                // Validate it's a 3-letter code (standard ISO currency format) but not month names
                if (currencyCode.matches(Regex("""[A-Z]{3}""")) &&
                    !currencyCode.matches(Regex("""^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""", RegexOption.IGNORE_CASE))
                ) {
                    return currencyCode
                }
            }
        }

        // Default to AED for Liv Bank (UAE Dirham)
        return "AED"
    }
}
