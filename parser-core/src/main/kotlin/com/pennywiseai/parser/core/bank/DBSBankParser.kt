package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for DBS Bank (Development Bank of Singapore) SMS messages
 */
class DBSBankParser : BankParser() {

    override fun getBankName() = "DBS Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("DBSBNK") ||
                normalizedSender.contains("DBS") ||
                normalizedSender == "DBSBANK" ||
                // DLT patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-DBSBNK-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-DBS-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-DBSBANK-[ST]$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "debited with INR 11" or "credited with INR 100"
        val patterns = listOf(
            Regex(
                """(?:debited|credited)\s+with\s+INR\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""INR\s*([0-9,]+(?:\.\d{2})?)\s+(?:debited|credited)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
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

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "account no ********1234" or "a/c ****1234"
        val patterns = listOf(
            Regex("""account\s+no\s+\*+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""a/c\s+\*+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""account\s+\*+(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Current Balance is INR37888.45" or "Balance: INR 1000"
        val patterns = listOf(
            Regex(
                """Current\s+Balance\s+is\s+INR\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""Balance[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Avl\s+Bal[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
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

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }
}