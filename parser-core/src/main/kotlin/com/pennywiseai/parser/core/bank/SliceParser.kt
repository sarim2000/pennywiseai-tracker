package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Slice payments bank transactions.
 * Handles messages from JK-SLICEIT and similar senders.
 */
class SliceParser : BankParser() {

    override fun getBankName() = "Slice"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("SLICE") ||
                normalizedSender.contains("SLICEIT") ||
                normalizedSender.contains("SLCEIT")  // Matches JD-SLCEIT-S and similar
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Slice uses "sent" for UPI transfers
        if (lowerMessage.contains("sent")) {
            return true
        }

        return super.isTransactionMessage(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Look for "sent to NAME" pattern for UPI transfers
        val sentToPattern = Regex("""sent.*to\s+([A-Z][A-Z0-9\s./&-]+?)\s*\(""", RegexOption.IGNORE_CASE)
        sentToPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        // Look for "from MERCHANT" pattern
        val fromPattern =
            Regex("""from\s+([A-Z][A-Z0-9\s]+?)(?:\s+on|\s+\(|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && !merchant.equals("NEFT", ignoreCase = true)) {
                return cleanMerchantName(merchant)
            }
        }

        // Check for specific patterns
        return when {
            lowerMessage.contains("paypal") -> "PayPal"
            lowerMessage.contains("slice") && lowerMessage.contains("credited") -> "Slice Credit"
            else -> super.extractMerchant(message, sender) ?: "Slice"
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Slice credits/cashbacks
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("cashback") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            // Slice payments/debits
            lowerMessage.contains("debited") -> TransactionType.CREDIT
            lowerMessage.contains("spent") -> TransactionType.CREDIT
            lowerMessage.contains("paid") -> TransactionType.CREDIT
            lowerMessage.contains("sent") -> TransactionType.CREDIT  // UPI transfers
            lowerMessage.contains("payment") && !lowerMessage.contains("received") -> TransactionType.CREDIT

            else -> super.extractTransactionType(message)
        }
    }
}
