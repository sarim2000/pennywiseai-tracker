package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for City Union Bank SMS messages
 *
 * Common senders: JK-CUBLTD-S, XX-CUBLTD-T, etc.
 *
 * SMS Formats:
 * - Your a/c no. XXXXXXXXXXXXXXX is debited for Rs.111.00 on 01-09-2025 and credited to a/c no. YYYYYYYYYYYYYYY (UPI Ref no 123456789012)
 * - Your a/c no. XXXXXXXXXXXXXXX is credited for Rs.111.00 on 01-09-2025 and debited from a/c no. YYYYYYYYYYYYYYY (UPI Ref no 123456789012)
 * - Savings No XXXXXXXXXXXXXXX credited with INR 111.00 towards BY NEFT TRF:AMBANI YYYYYYYYYYYYYYY: on 01-SEP-2025. Avl Bal 120.00
 */
class CityUnionBankParser : BaseIndianBankParser() {

    override fun getBankName() = "City Union Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("CUBANK") ||
                normalizedSender.contains("CUBLTD") ||
                normalizedSender.contains("CUB")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // List of amount patterns for City Union Bank
        val amountPatterns = listOf(
            // "debited for Rs.111.00"
            Regex("""debited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "credited for Rs.111.00"
            Regex("""credited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "credited with INR 111.00"
            Regex("""credited\s+with\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
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
            // Check for debit patterns
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited for") -> TransactionType.EXPENSE
            lowerMessage.contains("debited from") -> TransactionType.EXPENSE

            // Check for credit patterns
            lowerMessage.contains("is credited") -> TransactionType.INCOME
            lowerMessage.contains("credited for") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("credited to") -> TransactionType.INCOME

            // NEFT/Transfer patterns
            lowerMessage.contains("neft trf") -> TransactionType.INCOME

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // NEFT Transfer pattern
        if (lowerMessage.contains("neft trf")) {
            // Extract sender name from "BY NEFT TRF:NAME"
            val neftPattern = Regex(
                """BY\s+NEFT\s+TRF:([^:]+)""",
                RegexOption.IGNORE_CASE
            )
            neftPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                return "NEFT - $merchant"
            }
            return "NEFT Transfer"
        }

        // UPI Transaction
        if (message.contains("UPI Ref", ignoreCase = true)) {
            // Try to extract the other account details
            val toAccountPattern = Regex(
                """credited\s+to\s+a/c\s+no\.\s+([A-Z0-9]+)""",
                RegexOption.IGNORE_CASE
            )
            val fromAccountPattern = Regex(
                """debited\s+from\s+a/c\s+no\.\s+([A-Z0-9]+)""",
                RegexOption.IGNORE_CASE
            )

            toAccountPattern.find(message)?.let { match ->
                val accountLast4 = if (match.groupValues[1].length >= 4) {
                    match.groupValues[1].takeLast(4)
                } else {
                    match.groupValues[1]
                }
                return "UPI Transfer to A/C XX$accountLast4"
            }

            fromAccountPattern.find(message)?.let { match ->
                val accountLast4 = if (match.groupValues[1].length >= 4) {
                    match.groupValues[1].takeLast(4)
                } else {
                    match.groupValues[1]
                }
                return "UPI Transfer from A/C XX$accountLast4"
            }

            return "UPI Transfer"
        }

        // Generic transfer
        if (lowerMessage.contains("credited to a/c") || lowerMessage.contains("debited from a/c")) {
            return "Account Transfer"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Your a/c no. XXXXXXXXXXXXXXX" or "Savings No XXXXXXXXXXXXXXX"
        val accountPatterns = listOf(
            Regex("""Your\s+a/c\s+no\.\s+[X]*(\d{3,4})""", RegexOption.IGNORE_CASE),
            Regex("""Savings\s+No\s+[X]*(\d{3,4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                val digits = match.groupValues[1]
                return if (digits.length >= 4) digits.takeLast(4) else digits
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Avl Bal 120.00"
        val balancePattern = Regex(
            """Avl\s+Bal\s+([0-9,]+(?:\.\d{2})?)""",
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
        // Pattern: "(UPI Ref no 123456789012)"
        val upiRefPattern = Regex(
            """\(UPI\s+Ref\s+no\s+(\d+)\)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // NEFT transaction ID if present
        val neftRefPattern = Regex(
            """NEFT[:/]\s*([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        neftRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and non-transaction messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("verification") ||
            lowerMessage.contains("request")
        ) {
            return false
        }

        // Check for City Union Bank specific transaction patterns
        if (lowerMessage.contains("is debited for") ||
            lowerMessage.contains("is credited for") ||
            lowerMessage.contains("credited with") ||
            lowerMessage.contains("neft trf")
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}