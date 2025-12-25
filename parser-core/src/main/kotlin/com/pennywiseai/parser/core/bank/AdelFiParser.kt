package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for AdelFi Credit Union transactions.
 * Handles messages from sender 42141 and similar.
 */
class AdelFiParser : BankParser() {

    override fun getBankName() = "AdelFi"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        // Sender is typically "42141" but message contains "AdelFi"
        return sender.contains("42141")
    }

    override fun isTransactionMessage(message: String): Boolean {
        return message.contains("Transaction Alert from AdelFi", ignoreCase = true) &&
                message.contains("had a transaction of", ignoreCase = true)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Amount is in format "($15.00)" or "($33.79)"
        val amountPattern = Regex("""\(\$(\d+(?:\.\d{2})?)\)""")
        return amountPattern.find(message)?.let {
            it.groupValues[1].toBigDecimalOrNull()
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Merchant is in "Description: MERCHANT_NAME" field
        val descriptionPattern = Regex("""Description:\s*(.+?)(?:\.\s*Date:|$)""", RegexOption.IGNORE_CASE)
        return descriptionPattern.find(message)?.let { match ->
            val description = match.groupValues[1].trim()
            if (description.isNotEmpty()) {
                // Clean up merchant name - remove transaction IDs at start
                val cleaned = description
                    .replace(Regex("""^\d+\s+"""), "") // Remove leading numbers
                    .trim()
                cleanMerchantName(cleaned)
            } else {
                null
            }
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Account shown as "**1234"
        val accountPattern = Regex("""\*\*(\d{4})""")
        return accountPattern.find(message)?.groupValues?.get(1)
    }

    override fun extractTransactionType(message: String): TransactionType {
        // AdelFi messages show debits/purchases as transactions
        // Amount is in parentheses which typically indicates debit
        return TransactionType.CREDIT // Credit card transactions
    }
}
