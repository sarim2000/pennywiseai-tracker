package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Liv Bank (UAE) - Digital bank
 * Handles AED currency transactions
 *
 * Example SMS formats:
 * - Credit: "AED 3,586.96 has been credited to account 095XXX71XXXO1. Current balance is AED 4,377.01."
 * - Debit: "Purchase of AED 33.00 with Debit Card ending 4878 at JABAL HAFEET HAIRDRESS, Sharjah. Avl Balance is AED 4,344.01."
 */
class LivBankParser : BankParser() {

    override fun getBankName() = "Liv Bank"

    override fun getCurrency() = "AED"

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

    override fun extractAmount(message: String): BigDecimal? {
        // Liv Bank patterns: "AED X,XXX.XX" or "AED XXX.XX"
        val amountPattern = Regex("""AED\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        return amountPattern.find(message)?.let {
            it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern for purchases: "at MERCHANT_NAME, Location."
        if (lowerMessage.contains("purchase of")) {
            val merchantPattern = Regex("""at\s+([^,\.]+)""", RegexOption.IGNORE_CASE)
            merchantPattern.find(message)?.let { match ->
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
        // Liv Bank balance patterns
        val balancePatterns = listOf(
            // "Current balance is AED X,XXX.XX"
            Regex("""Current balance is\s+AED\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            
            // "Avl Balance is AED X,XXX.XX"
            Regex("""Avl Balance is\s+AED\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            
            // Generic "Balance: AED X,XXX.XX"
            Regex("""Balance:?\s+AED\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

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
                lowerMessage.contains("debit card") ||
                lowerMessage.contains("credit card") ||
                super.detectIsCard(message)
    }
}
