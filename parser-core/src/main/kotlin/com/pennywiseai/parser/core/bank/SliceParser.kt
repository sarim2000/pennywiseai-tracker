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

    private fun isSuccessMessage(message: String): Boolean {
        val lower = message.lowercase()
        // Use word boundaries to avoid matching "unsuccessful"
        return Regex("""\bsuccessful\b""").containsMatchIn(lower) || 
               Regex("""\bsuccess\b""").containsMatchIn(lower) || 
               lower.contains("approved") ||
               lower.contains("confirmed")
    }

    private fun isFailureMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("declined") ||
               lower.contains("failed") ||
               lower.contains("rejected") ||
               lower.contains("error") ||
               lower.contains("denied") ||
               lower.contains("unsuccessful")
    }

    private fun isDatePhrase(text: String): Boolean {
        // Simple date pattern matching month names with day numbers
        val datePattern = Regex("""\b(?:\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2})\b""", RegexOption.IGNORE_CASE)
        return datePattern.containsMatchIn(text)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Slice uses "sent" for UPI transfers (always success?)
        if (lowerMessage.contains("sent")) {
            return true
        }

        // For "transaction" keyword, ensure it's a successful transaction
        if (lowerMessage.contains("transaction")) {
            return isSuccessMessage(message) && !isFailureMessage(message)
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

        // Look for "on MERCHANT" pattern for credit card transactions
        val onPattern = Regex("""\bon\s+([A-Za-z0-9\s./&-]+?)(?:\s+is|$)""", RegexOption.IGNORE_CASE)
        onPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && 
                !merchant.equals("slice", ignoreCase = true) &&
                !merchant.equals("RS", ignoreCase = true) &&
                !isDatePhrase(merchant)) {
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

    /**
     * Returns true when the message clearly references a Slice credit-card product
     * (legacy, pre-2022 RBI PPI pivot). Modern Slice is a UPI / savings-account
     * product, so without explicit card context we treat debits as EXPENSE, not
     * CREDIT (which downstream is interpreted as "credit card account").
     */
    private fun hasCardContext(lowerMessage: String): Boolean {
        return lowerMessage.contains("credit card") ||
                lowerMessage.contains("credit limit") ||
                lowerMessage.contains("available limit") ||
                lowerMessage.contains("card ending") ||
                lowerMessage.contains("card xx") ||
                lowerMessage.contains("card no") ||
                lowerMessage.contains("on your slice card") ||
                lowerMessage.contains("slice card")
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        val cardContext = hasCardContext(lowerMessage)

        return when {
            // Slice credits/cashbacks (income side unchanged)
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("cashback") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            // Slice payments/debits.
            // After RBI's 2022 PPI guidelines, Slice pivoted from a credit-card
            // product to a UPI / savings-account product (Slice Bank). Debits
            // from the bank account must be EXPENSE so that downstream code
            // does not classify the account as a credit card. Only fall back
            // to CREDIT when the message explicitly mentions card context.
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("sent") -> TransactionType.EXPENSE  // UPI transfer
            lowerMessage.contains("spent") ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE
            lowerMessage.contains("paid") ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE
            lowerMessage.contains("payment") && !lowerMessage.contains("received") ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE
            // Only map bare "transaction" word to a type if it's a successful
            // transaction; default to EXPENSE unless clearly card-context.
            lowerMessage.contains("transaction") && !lowerMessage.contains("credited") &&
                isSuccessMessage(message) && !isFailureMessage(message) ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }
}
