package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Chartered Bank Nepal SMS messages.
 *
 * Handles formats like:
 * - "NPR 95,000.00 has been debited from your account 3301."
 * - "NPR 80,000.00 has been deposited into your account 1234."
 *
 * Common senders: SC_ALERT, SCB_ALERT, SCBNL
 * Currency: NPR (Nepalese Rupee)
 */
class StandardCharteredNepalParser : BankParser() {

    override fun getBankName() = "Standard Chartered Nepal"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("SCBNL") ||
                upperSender.contains("SCB_NP") ||
                upperSender.contains("SCBNP") ||
                upperSender == "SC_ALERT" ||
                upperSender == "SCB_ALERT"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "NPR 95,000.00"
        val nprPattern = Regex(
            """NPR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        nprPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("has been debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited from") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE

            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("credited to") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }

        // Pattern: "your account 3301" or "your account 3301."
        val accountPattern = Regex(
            """your account\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Available Bal: 123,456.78" or "Av Bal: 123456.78"
        val balancePatterns = listOf(
            Regex("""Available Bal(?:ance)?[:\s]+(?:NPR\s*)?([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Av Bal[:\s]+(?:NPR\s*)?([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
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

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val hasAmount = lowerMessage.contains("npr")
        val hasTransactionKeyword = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("deposited")

        return hasAmount && hasTransactionKeyword
    }
}
