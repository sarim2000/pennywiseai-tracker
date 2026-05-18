package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Cashfree payment gateway confirmations.
 * Handles DLT-style senders such as JX-CSHfre-S, VK-CSHfre-S, JD-CSHfre-T, etc.
 *
 * Cashfree is a payment aggregator (similar in role to Juspay) that delivers
 * outgoing payment confirmations on behalf of merchants. Messages typically look like:
 *
 *   "Payment INR 50.00 (ID:1234567890) confirmed for order #abc_123 on MerchantName.
 *    Powered by Cashfree"
 *
 * The gateway has no concept of balance, account, mandate, or subscription, so this
 * parser extends [BankParser] directly rather than [BaseIndianBankParser].
 */
class CashfreeParser : BankParser() {

    override fun getBankName() = "Cashfree"

    override fun getCurrency() = "INR"

    override fun canHandle(sender: String): Boolean {
        // Match the CSHfre token case-insensitively. Covers headers like
        // JX-CSHfre-S, VK-CSHfre-S, JD-CSHfre-T, and plain "CSHFRE".
        return sender.uppercase().contains("CSHFRE")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Reject OTP / verification messages first so a Cashfree-sender
        // promo or deep-link that happens to contain "payment" + "confirmed
        // for order" alongside OTP content can never short-circuit past the
        // guard below.
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Cashfree-specific confirmation phrasing: "Payment ... confirmed for order ..."
        if (lowerMessage.contains("payment") &&
            lowerMessage.contains("confirmed for order")
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "Payment INR 50.00"
        val paymentPattern = Regex(
            """Payment\s+INR\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        paymentPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Cashfree messages are outgoing payment confirmations.
        if (lowerMessage.contains("payment") &&
            lowerMessage.contains("confirmed for order")
        ) {
            return TransactionType.EXPENSE
        }

        return super.extractTransactionType(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "...confirmed for order #<orderId> on <Merchant>."
        // Capture the merchant between "on " and the next period/end-of-line.
        val merchantPattern = Regex(
            """confirmed\s+for\s+order\s+#\S+\s+on\s+([^.\n\r]+?)(?:\.|$)""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val cleaned = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(cleaned)) {
                return cleaned
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        // Pattern: "(ID:5448114171)"
        val idPattern = Regex(
            """\(ID:\s*([A-Za-z0-9]+)\)""",
            RegexOption.IGNORE_CASE
        )
        idPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Cashfree confirmations carry no account/card identifier.
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Cashfree confirmations carry no balance information.
        return null
    }
}
