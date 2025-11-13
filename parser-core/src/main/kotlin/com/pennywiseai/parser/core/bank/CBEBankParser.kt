package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Commercial Bank of Ethiopia (CBE) - handles ETB currency transactions
 */
class CBEBankParser : BankParser() {

    override fun getBankName() = "Commercial Bank of Ethiopia"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "CBE" ||
                upperSender.contains("COMMERCIALBANK") ||
                upperSender.contains("CBEBANK") ||
                // DLT patterns for Ethiopia might be different
                upperSender.matches(Regex("""^[A-Z]{2}-CBE-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // CBE patterns: "ETB 3,000.00", "ETB 25.00", "ETB250"
        val patterns = listOf(
            Regex("""ETB\s+([0-9,]+(?:\.[0-9]{2})?)\s""", RegexOption.IGNORE_CASE),
            Regex("""ETB\s*([0-9,]+(?:\.[0-9]{2})?)(?:\s|$|\.)""", RegexOption.IGNORE_CASE),
            Regex(
                """(?:Credited|debited|transfered)\s+(?:with\s+)?ETB\s+([0-9,]+(?:\.[0-9]{2})?)""",
                RegexOption.IGNORE_CASE
            )
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

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit transactions are income
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME

            // Debit transactions are expenses
            lowerMessage.contains("has been debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE

            // Transfer transactions are expenses (money going out)
            lowerMessage.contains("you have transfered") -> TransactionType.EXPENSE
            lowerMessage.contains("transferred") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "from Be***" (credit transaction)
        val fromPattern = Regex("""from\s+([^,\s]+\*{0,3}[^,\s]*)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant.replace("*", ""))
            }
        }

        // Pattern 2: "to Se*****" (transfer transaction)
        val toPattern = Regex("""to\s+([^,\s]+\*{0,5}[^,\s]*)""", RegexOption.IGNORE_CASE)
        toPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant.replace("*", ""))
            }
        }

        // Pattern 3: Service charge or general debit
        if (message.contains("s.charge", ignoreCase = true) ||
            message.contains("service charge", ignoreCase = true)
        ) {
            return "Service Charge"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Account 1*********9388" - extract last 4 digits
        val accountPattern = Regex("""Account\s+\d?\*+(\d{4})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Alternative pattern: "from your account 1*********9388"
        val yourAccountPattern = Regex("""your account\s+\d?\*+(\d{4})""", RegexOption.IGNORE_CASE)
        yourAccountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Your Current Balance is ETB 3,104.87"
        val balancePattern =
            Regex("""Current Balance is ETB\s+([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
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
        // Look for reference numbers: "with Ref No *********"
        val refPattern = Regex("""Ref No\s+(\*{0,9}[A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        refPattern.find(message)?.let { match ->
            val ref = match.groupValues[1].replace("*", "")
            if (ref.isNotEmpty()) {
                return ref
            }
        }

        // Look for transaction ID in URL: "id=FT25256RP1FK27799388"
        val urlIdPattern = Regex("""id=([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        urlIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Look for date and time: "on 13/09/2025 at 12:37:24"
        val dateTimePattern =
            Regex("""on\s+(\d{2}/\d{2}/\d{4}\s+at\s+\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
        dateTimePattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // CBE specific transaction keywords
        val cbeTransactionKeywords = listOf(
            "dear",
            "your account",
            "has been credited",
            "has been debited",
            "you have transfered",
            "current balance",
            "thank you for banking with cbe",
            "etb"
        )

        if (cbeTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}