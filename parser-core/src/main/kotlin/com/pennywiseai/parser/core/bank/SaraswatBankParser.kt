package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Saraswat Co-operative Bank
 *
 * Handles formats like:
 * - "Your A/c no. 013460 is credited with INR 115.50 on 13-10-2025 towards ACH Credit:GUJARAT GAS LIMITED. Current Bal is INR 941.23 CR  - Saraswat Bank"
 * - "Dear Customer, Your account no. ending with 013460 is debited with INR 10,000.00 on 25-09-2025  for S.I. Current Bal is INR 8,256.97CR. - Saraswat Bank"
 */
class SaraswatBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Saraswat Co-operative Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()

        // Direct sender IDs
        val saraswatSenders = setOf(
            "SARBNK",
            "SARASWAT",
            "SARASWATBANK"
        )

        if (normalizedSender in saraswatSenders) return true

        // DLT patterns (XX-SARBNK-S/T format)
        return normalizedSender.matches(Regex("^[A-Z]{2}-SARBNK-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-SARASWAT-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-SARBNK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-SARASWAT$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "INR 115.50" or "INR 10,000.00"
        val inrPattern = Regex("""INR\s+(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        inrPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Rs. format
        val rsPattern = Regex("""Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        rsPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
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
            lowerMessage.contains("is credited") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "towards ACH Credit:GUJARAT GAS LIMITED"
        val towardsPattern =
            Regex("""towards\s+(.+?)(?:\.\s*Current|\s*Current|$)""", RegexOption.IGNORE_CASE)
        towardsPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Clean up "ACH Credit:" prefix
            val cleanedMerchant = merchant
                .replace(Regex("""^ACH\s+Credit:\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^ACH\s+Debit:\s*""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (isValidMerchantName(cleanedMerchant)) {
                return cleanMerchantName(cleanedMerchant)
            }
        }

        // Pattern 2: "for S.I." or "for NEFT" etc.
        val forPattern =
            Regex("""for\s+([A-Z.]+?)(?:\.\s+Current|\s+Current|$)""", RegexOption.IGNORE_CASE)
        forPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim().removeSuffix(".")
            return when (merchant.uppercase()) {
                "S.I" -> "Standing Instruction"
                "SI" -> "Standing Instruction"
                "NEFT" -> "NEFT Transfer"
                "RTGS" -> "RTGS Transfer"
                "IMPS" -> "IMPS Transfer"
                else -> merchant
            }
        }

        // Pattern 3: ATM withdrawal
        if (message.contains("ATM", ignoreCase = true) || message.contains(
                "withdrawn",
                ignoreCase = true
            )
        ) {
            return "ATM Withdrawal"
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "A/c no. 013460" or "A/c no. ending with 013460"
        val accountNoPattern =
            Regex("""A/c\s+no\.\s+(?:ending\s+with\s+)?(\d{4,6})""", RegexOption.IGNORE_CASE)
        accountNoPattern.find(message)?.let { match ->
            val accountNumber = match.groupValues[1]
            // Return last 4 digits
            return accountNumber.takeLast(4)
        }

        // Pattern 2: "account no. ending with 013460"
        val endingWithPattern =
            Regex("""account\s+no\.\s+ending\s+with\s+(\d{4,6})""", RegexOption.IGNORE_CASE)
        endingWithPattern.find(message)?.let { match ->
            val accountNumber = match.groupValues[1]
            return accountNumber.takeLast(4)
        }

        // Pattern 3: "A/c *1234"
        val pattern3 = Regex("""A/c\s+\*(\d{4})""", RegexOption.IGNORE_CASE)
        pattern3.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: "Current Bal is INR 941.23 CR" or "Current Bal is INR 8,256.97CR"
        val currentBalPattern = Regex(
            """Current\s+Bal\s+is\s+INR\s+(\d+(?:,\d{3})*(?:\.\d{2})?)\s*(?:CR|DR)?""",
            RegexOption.IGNORE_CASE
        )
        currentBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Bal: Rs. 1000.00"
        val balPattern =
            Regex("""Bal[:\s]+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        balPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and verification messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Saraswat Bank specific transaction keywords
        val saraswatTransactionKeywords = listOf(
            "is credited with",
            "is debited with",
            "credited with inr",
            "debited with inr",
            "current bal is"
        )

        if (saraswatTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }
}
