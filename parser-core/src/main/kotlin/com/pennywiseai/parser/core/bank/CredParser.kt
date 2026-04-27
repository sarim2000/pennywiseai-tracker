package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for CRED credit card payment SMS messages.
 * CRED is a credit card payment service that facilitates bill payments.
 * Example: "Payment of Rs.XX,XXX has been successfully credited towards your ICICI Bank Credit Card. Your payment was settled in 3 seconds - CRED"
 * Sender: JK-CREDIN-S, etc.
 *
 * These messages represent credit card bill payments, which should be treated as transfers
 * from the user's bank account to their credit card account.
 */
class CredParser : BankParser() {

    override fun getBankName() = "CRED"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        // DLT patterns: JK-CREDIN-S, AX-CREDIN-S, etc.
        return normalizedSender.matches(Regex("^[A-Z]{2}-CREDIN-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-CRED-[TPG]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-CRED-S$")) ||
                normalizedSender == "CRED" ||
                normalizedSender == "CREDIN"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "Rs.XX,XXX" or "Rs. XX,XXX"
        val amountPattern = Regex(
            """Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Extract the credit card name after "towards your"
        val towardsPattern = Regex(
            """towards\s+your\s+(.+?)\s+Credit\s+Card""",
            RegexOption.IGNORE_CASE
        )
        towardsPattern.find(message)?.let { match ->
            val cardName = match.groupValues[1].trim()
            if (cardName.isNotEmpty()) {
                // Return something like "ICICI Bank Credit Card"
                return "$cardName Credit Card"
            }
        }
        // Default to "CRED"
        return super.extractMerchant(message, sender) ?: "CRED"
    }

    override fun extractTransactionType(message: String): TransactionType {
        // Credit card bill payments are transfers from bank account to credit card account
        return TransactionType.TRANSFER
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        // Must contain "payment of" and "credited towards your" to be a CRED transaction
        return lowerMessage.contains("payment of") && 
               lowerMessage.contains("credited towards your")
    }


}