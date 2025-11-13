package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for LazyPay wallet transactions.
 * Handles messages from BP-LZYPAY-S, JM-LZYPAY-S, JD-LZYPAY-S and similar senders.
 * LazyPay is a Buy Now Pay Later (BNPL) wallet service similar to Amazon Pay/Juspay.
 * All transactions are treated as CREDIT type since they're wallet-based credit transactions.
 */
class LazyPayParser : BankParser() {

    override fun getBankName() = "LazyPay"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("LZYPAY") ||
                normalizedSender.contains("LAZYPAY")
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "for txn TXN512924131 on [MERCHANT] was successful"
        val onMerchantPattern =
            Regex("""on\s+([^.]+?)\s+was\s+successful""", RegexOption.IGNORE_CASE)
        onMerchantPattern.find(message)?.let { match ->
            val rawMerchant = match.groupValues[1].trim()
            // Clean up common merchant names
            val cleanedMerchant = when {
                rawMerchant.contains("Zepto Marketplace", ignoreCase = true) -> "Zepto"
                rawMerchant.contains("Innovative Retail Concepts", ignoreCase = true) -> "BigBasket"
                rawMerchant.contains("Swiggy", ignoreCase = true) -> "Swiggy"
                rawMerchant.contains("Zomato", ignoreCase = true) -> "Zomato"
                else -> {
                    // Remove common suffixes like "Private Limited", "Pvt Ltd", etc.
                    rawMerchant
                        .replace(
                            Regex(
                                """\s*(Private|Pvt\.?|Ltd\.?|Limited|Inc\.?|LLC|LLP).*$""",
                                RegexOption.IGNORE_CASE
                            ), ""
                        )
                        .replace(Regex("""\s*\d+$"""), "") // Remove trailing numbers
                        .trim()
                }
            }
            if (cleanedMerchant.isNotEmpty()) {
                return cleanedMerchant
            }
        }

        // Pattern 2: Repayment messages
        if (message.contains("against your LazyPay statement", ignoreCase = true)) {
            return "LazyPay Repayment"
        }

        // Default to LazyPay if no specific merchant found
        return super.extractMerchant(message, sender) ?: "LazyPay"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "Rs. 235.76" or "Rs 235.76"
        val amountPatterns = listOf(
            Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun extractReference(message: String): String? {
        // Extract transaction ID like "TXN512924131"
        val txnPattern = Regex("""txn\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        txnPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType {
        // LazyPay is a credit service - all transactions are credit-based
        // Similar to how JuspayParser handles Amazon Pay
        return TransactionType.CREDIT
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip failed payment messages
        if (lowerMessage.contains("could not be processed") ||
            lowerMessage.contains("due to a failure") ||
            lowerMessage.contains("payment failed") ||
            lowerMessage.contains("transaction failed") ||
            lowerMessage.contains("unsuccessful")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("get cashback") ||
            lowerMessage.contains("explore more")
        ) {
            // But allow if it's a payment confirmation
            if (!lowerMessage.contains("payment of") &&
                !lowerMessage.contains("was successful")
            ) {
                return false
            }
        }

        // Transaction indicators for LazyPay
        val transactionKeywords = listOf(
            "payment of",
            "was successful",
            "against your lazypay statement",
            "thanks for your payment"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}