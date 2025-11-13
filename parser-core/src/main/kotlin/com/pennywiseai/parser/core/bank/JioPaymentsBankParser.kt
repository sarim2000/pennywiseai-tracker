package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Jio Payments Bank (JPB/JPBL) SMS messages
 */
class JioPaymentsBankParser : BankParser() {

    override fun getBankName() = "Jio Payments Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("JIOPBS")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: credited with Rs.1670.00
        val creditPattern = Regex(
            """credited\s+with\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Rs. 1170.00 Sent from
        val sentPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+Sent\s+from""",
            RegexOption.IGNORE_CASE
        )
        sentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: debited with Rs. 1750.00
        val debitPattern = Regex(
            """debited\s+with\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: UPI/CR/700003371002/AMAN KU
        // Pattern 2: UPI/DR/520300007125/AMAN KUM
        val upiPattern = Regex(
            """UPI/(?:CR|DR)/[\d]+/([^.\n]+?)(?:\s*\.|$)""",
            RegexOption.IGNORE_CASE
        )
        upiPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // If no specific merchant found, check transaction type
        return when {
            message.contains("UPI/CR", ignoreCase = true) -> "UPI Credit"
            message.contains("UPI/DR", ignoreCase = true) -> "UPI Payment"
            message.contains("Sent from", ignoreCase = true) -> "Money Transfer"
            else -> super.extractMerchant(message, sender)
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: JPB A/c x4288
        val jpbPattern = Regex(
            """JPB\s+A/c\s+x(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        jpbPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: from x4288
        val fromPattern = Regex(
            """from\s+x(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: Avl. Bal: Rs. 9095.5
        val balancePattern = Regex(
            """Avl\.?\s*Bal:\s*Rs\.?\s*([\d,]+(?:\.\d{1,2})?)""",
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

    override fun extractReference(message: String): String? {
        // Pattern: UPI/CR/700003371002 or UPI/DR/520300007125
        val upiRefPattern = Regex(
            """UPI/(?:CR|DR)/(\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("upi/cr") -> TransactionType.INCOME
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("upi/dr") -> TransactionType.EXPENSE
            lowerMessage.contains("sent from") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for Jio Payments Bank specific transaction keywords
        if (lowerMessage.contains("jpb a/c") ||
            lowerMessage.contains("upi/cr") ||
            lowerMessage.contains("upi/dr") ||
            lowerMessage.contains("sent from")
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}