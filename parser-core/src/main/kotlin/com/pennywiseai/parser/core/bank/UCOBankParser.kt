package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for UCO Bank SMS messages
 *
 * Supported formats:
 * - Debit: "A/c XX1111 Debited with Rs.2000.00 on 21-09-2025 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in/Home_Page.jsp"
 * - Credit: "A/c XX1111 Credited with Rs.2,000.00 on 21-09-2025 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in/Home_Page.jsp -UCO Bank"
 *
 * Sender patterns: XX-UCOBNK-S (where XX can be any two letters)
 */
class UCOBankParser : BankParser() {

    override fun getBankName() = "UCO Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("UCOBNK") ||
                normalizedSender.contains("UCOBANK") ||
                normalizedSender.contains("UCO BANK") ||
                // DLT patterns with any two-letter prefix followed by -UCOBNK-S
                normalizedSender.matches(Regex("^[A-Z]{2}-UCOBNK-[ST]$")) ||
                // Other variations
                normalizedSender.matches(Regex("^[A-Z]{2}-UCOBNK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-UCOBANK$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // UCO Bank format: "Rs.2000.00" or "Rs.2,000.00"
        val amountPattern = Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        amountPattern.find(message)?.let { match ->
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

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // UCO Bank format: "by UCO-UPI" or "by <merchant>"
        val merchantPattern = Regex("""by\s+([^.]+?)(?:\.Avl|$)""", RegexOption.IGNORE_CASE)
        merchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()

            // Handle UCO-UPI transactions
            if (merchant.contains("UCO-UPI", ignoreCase = true)) {
                return "UPI Transfer"
            }

            // Clean up common suffixes
            return cleanMerchantName(merchant)
        }

        // Fall back to base class extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // UCO Bank format: "A/c XX1111"
        val accountPatterns = listOf(
            Regex("""A/c\s+[X]{2}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""Account\s+[X]{2}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""Acc\s+[X]{2}(\d{4})""", RegexOption.IGNORE_CASE),
            // Also handle variations with asterisks
            Regex("""A/c\s+[*]{2}(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // UCO Bank format: "Avl Bal Rs.11111.11"
        val balancePatterns = listOf(
            Regex("""Avl\s+Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex(
                """Available\s+Balance\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""Balance[:.]?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Look for any transaction reference patterns specific to UCO Bank
        val refPatterns = listOf(
            Regex("""ref[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""txn[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""transaction\s+id[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in refPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        return super.extractReference(message)
    }
}