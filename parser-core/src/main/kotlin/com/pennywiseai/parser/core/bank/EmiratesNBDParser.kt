package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Emirates NBD Bank (UAE) transactions.
 * Inherits from UAEBankParser for multi-currency support.
 * Handles credit card and account transactions in AED and other currencies.
 */
class EmiratesNBDParser : UAEBankParser() {

    override fun getBankName() = "Emirates NBD"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace(Regex("\\s+"), "")
        return normalizedSender.contains("EMIRATESNBD") ||
                normalizedSender.contains("ENBD") ||
                normalizedSender.contains("EMIRATESNB")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for transaction keywords
        return lowerMessage.contains("purchase of") ||
                lowerMessage.contains("debited") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("deposited") ||
                lowerMessage.contains("transfer")
    }

    // extractAmount is now handled by UAEBankParser which supports multi-currency patterns

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern: "at MERCHANT_NAME. Avl" or "at MERCHANT_NAME$"
        val atPattern = Regex("""at\s+(.+?)(?:\.\s*Avl|$)""", RegexOption.IGNORE_CASE)
        atPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern: "to MERCHANT" for transfers
        val toPattern = Regex("""to\s+([A-Z][A-Z0-9\s]+?)(?:\s+on|\s+\(|$)""", RegexOption.IGNORE_CASE)
        toPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "ending 9074" or "A/C xxxx9074"
        val endingPattern = Regex("""ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        endingPattern.find(message)?.let {
            return it.groupValues[1]
        }

        val accountPattern = Regex("""[xX]{4}(\d{4})""")
        return accountPattern.find(message)?.groupValues?.get(1)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Emirates NBD balance patterns: Support multi-currency
        val balancePatterns = listOf(
            // Pattern 1: "Avl Bal is CURRENCY X,XXX.XX"
            Regex("""(?:Avl\s+Bal|Available\s+Balance)(?:\s+is)?\s*([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // Pattern 2: "Available Balance: CURRENCY X,XXX.XX"
            Regex("""Available\s+Balance:\s*([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
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

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Emirates NBD credit limit patterns: Support multi-currency
        val limitPatterns = listOf(
            // Pattern 1: "Avl Cr. Limit is CURRENCY 30,978.13"
            Regex("""Avl\s+Cr\.?\s+Limit(?:\s+is)?\s*([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // Pattern 2: "Available Credit Limit: CURRENCY X,XXX.XX"
            Regex("""Available\s+Credit\s+Limit:\s*([A-Z]{3})\s+([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in limitPatterns) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[2].replace(",", "")
                return try {
                    BigDecimal(limitStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        // Fallback to FAB's multi-currency limit extraction
        return super.extractAvailableLimit(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credits/Income
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("cashback") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME

            // Credit card purchases
            lowerMessage.contains("purchase of") && lowerMessage.contains("credit card") -> TransactionType.CREDIT

            // Debits/Expenses
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("transfer") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }
}
