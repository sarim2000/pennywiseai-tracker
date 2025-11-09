package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Airtel Payments Bank SMS messages
 *
 * Common senders: AD-AIRBNK-S, XX-AIRBNK-T, etc.
 *
 * SMS Formats:
 * - Airtel Payments Bank a/c is credited with Rs.20.00. Txn ID: 560992310006. Call 180023400 for help
 * - Rs. 5.00 debited from Airtel Payments Bank a/c Txn ID xxxxxxxx Bal:15.56 Call 180023400 for help
 */
class AirtelPaymentsBankParser : BankParser() {

    override fun getBankName() = "Airtel Payments Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        // Only handle Airtel Payments Bank, not prepaid recharges (Airtel-S)
        return normalizedSender.contains("AIRBNK")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // List of amount patterns for Airtel Payments Bank
        val amountPatterns = listOf(
            // "credited with Rs.20.00"
            Regex("""credited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Rs. 5.00 debited from"
            Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+debited\s+from""", RegexOption.IGNORE_CASE),
            // "debited with Rs.5.00" (potential variant)
            Regex("""debited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                val amount = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amount)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("is credited") -> TransactionType.INCOME
            lowerMessage.contains("credit") -> TransactionType.INCOME

            lowerMessage.contains("debited from") -> TransactionType.EXPENSE
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            lowerMessage.contains("debit") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // For basic credit/debit transactions, use bank name
        // In future, can enhance to extract merchant info from more detailed messages

        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("airtel payments bank") -> "Airtel Payments Bank Transaction"
            else -> super.extractMerchant(message, sender) ?: "Airtel Payments Bank"
        }
    }

    override fun extractReference(message: String): String? {
        // Pattern: "Txn ID: 560992310006" or "Txn ID xxxxxxxx"
        val txnIdPattern = Regex(
            """Txn\s+ID[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            val txnId = match.groupValues[1]
            // Filter out masked IDs like "xxxxxxxx"
            if (!txnId.contains("x", ignoreCase = true)) {
                return txnId
            }
        }

        // Alternative pattern for transaction ID
        val altTxnPattern = Regex(
            """Transaction\s+ID[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        altTxnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Bal:15.56"
        val balancePattern = Regex(
            """Bal[:\s]+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Alternative pattern: "Balance: Rs. 15.56"
        val altBalancePattern = Regex(
            """Balance[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        altBalancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and non-transaction messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("verification") ||
            lowerMessage.contains("request") ||
            lowerMessage.contains("failed")
        ) {
            return false
        }

        // Check for Airtel Payments Bank specific transaction patterns
        if (lowerMessage.contains("credited with") ||
            lowerMessage.contains("debited from") ||
            lowerMessage.contains("airtel payments bank") &&
            (lowerMessage.contains("credited") || lowerMessage.contains("debited"))
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}