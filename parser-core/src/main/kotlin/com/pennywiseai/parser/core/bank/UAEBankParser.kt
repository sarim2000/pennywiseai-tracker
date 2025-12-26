package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Base abstract class for UAE bank parsers.
 * Handles common patterns across UAE banks (AED currency, specific transaction types, etc.).
 */
abstract class UAEBankParser : BankParser() {

    override fun getCurrency() = "AED"

    /**
     * Checks if the message contains a credit/debit card purchase pattern.
     * Common across UAE banks.
     */
    protected open fun containsCardPurchase(message: String): Boolean {
        return message.contains("Credit Card Purchase", ignoreCase = true) ||
                message.contains("Debit Card Purchase", ignoreCase = true)
    }

    override fun extractCurrency(message: String): String? {
        // Extract currency code from transaction message
        val currencyPatterns = listOf(
            Regex("""Amount\s+([A-Z]{3})""", RegexOption.IGNORE_CASE),
            Regex("""[A-Z]{3}\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in currencyPatterns) {
            pattern.find(message)?.let { match ->
                val currencyMatch = Regex("""([A-Z]{3})""").find(match.value)
                currencyMatch?.let {
                    return it.groupValues[1].uppercase()
                }
            }
        }

        return "AED"
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit card transactions (always expenses)
            lowerMessage.contains("credit card purchase") -> TransactionType.CREDIT
            containsCardPurchase(message) -> TransactionType.EXPENSE

            //Cheque transactions
            lowerMessage.contains("cheque credited") -> TransactionType.INCOME
            lowerMessage.contains("cheque returned") -> TransactionType.EXPENSE

            // ATM withdrawals are expenses
            lowerMessage.contains("atm cash withdrawal") -> TransactionType.EXPENSE

            // Income transactions
            lowerMessage.contains("inward remittance") -> TransactionType.INCOME
            lowerMessage.contains("cash deposit") -> TransactionType.INCOME
            lowerMessage.contains("has been credited") -> TransactionType.INCOME

            // Outward remittance and payment instructions are expenses
            lowerMessage.contains("outward remittance") -> TransactionType.EXPENSE
            lowerMessage.contains("payment instructions") -> TransactionType.EXPENSE
            lowerMessage.contains("funds transfer request") -> TransactionType.TRANSFER
            lowerMessage.contains("has been processed") -> TransactionType.EXPENSE

            // Standard keywords
            lowerMessage.contains("credit") && !lowerMessage.contains("credit card") &&
                    !lowerMessage.contains("debit") &&
                    !lowerMessage.contains("purchase") &&
                    !lowerMessage.contains("payment") -> TransactionType.INCOME

            lowerMessage.contains("debit") && !lowerMessage.contains("credit") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }
}
