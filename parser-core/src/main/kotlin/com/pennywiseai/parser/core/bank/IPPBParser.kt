package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for India Post Payments Bank (IPPB) SMS messages
 */
class IPPBParser : BankParser() {

    override fun getBankName() = "India Post Payments Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()

        // Pattern: XX-IPBMSG-S or XX-IPBMSG-T where XX is any two letters
        return normalizedSender.matches(Regex("^[A-Z]{2}-IPBMSG-[ST]$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: Rs.1.00 or Rs. 1.00
        val amountPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: A/C X1234 or a/c X1234
        val accountPattern = Regex(
            """[Aa]/[Cc]\s+X?(\d+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            val accountNumber = match.groupValues[1]
            // Return last 4 digits or the full number if less than 4 digits
            return if (accountNumber.length >= 4) {
                accountNumber.takeLast(4)
            } else {
                accountNumber
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: Avl Bal Rs.436.91
        val balancePattern = Regex(
            """Avl\s+Bal\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
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

        return super.extractBalance(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "for UPI to john@superyes" (Debit)
        if (lowerMessage.contains("debit")) {
            val toPattern = Regex(
                """to\s+([^\s]+(?:@[^\s]+)?)""",
                RegexOption.IGNORE_CASE
            )
            toPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                // Clean up UPI ID if needed
                return if (merchant.contains("@")) {
                    val name = merchant.substringBefore("@")
                    cleanMerchantName(name)
                } else {
                    cleanMerchantName(merchant)
                }
            }

            // Fallback: "for UPI" without specific merchant
            if (lowerMessage.contains("for upi")) {
                return "UPI Payment"
            }
        }

        // Pattern 2: "from john doe thru IPPB" (Credit)
        if (lowerMessage.contains("received a payment")) {
            val fromPattern = Regex(
                """from\s+(.+?)\s+thru""",
                RegexOption.IGNORE_CASE
            )
            fromPattern.find(message)?.let { match ->
                val sender = match.groupValues[1].trim()
                return cleanMerchantName(sender)
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: Ref 560002638161
        val refPattern = Regex(
            """Ref\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Info: UPI/CREDIT/523498793035
        val infoPattern = Regex(
            """Info:\s*UPI/[^/]+/(\d+)""",
            RegexOption.IGNORE_CASE
        )
        infoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debit") -> TransactionType.EXPENSE
            lowerMessage.contains("received a payment") -> TransactionType.INCOME
            lowerMessage.contains("credit") && lowerMessage.contains("info: upi/credit") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for IPPB-specific transaction keywords
        if (lowerMessage.contains("debit rs") ||
            lowerMessage.contains("received a payment") ||
            (lowerMessage.contains("info: upi") && lowerMessage.contains("credit"))
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}