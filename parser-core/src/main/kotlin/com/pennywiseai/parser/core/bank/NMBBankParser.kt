package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for NMB Bank (Nabil Bank - Nepal) SMS messages
 *
 * Handles formats like:
 * - "Fund transfer of NPR 250.00 to A/C 01000000055 was successful"
 * - "A/C 0#16 withdrawn NPR 700.00 on 24/05/2025"
 * - "Your Esewa Wallet Load for 9850000007 of 300.00 is successful"
 *
 * Common sender: NMB_ALERT
 * Currency: NPR (Nepalese Rupee)
 */
class NMBBankParser : BankParser() {

    override fun getBankName() = "NMB Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("NMB") ||
                normalizedSender == "NMB_ALERT" ||
                normalizedSender == "NMBBANK" ||
                normalizedSender.contains("NABIL")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "NPR 250.00" or "NPR 700.00"
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

        // Pattern 2: "of 300.00" (for wallet loads without NPR prefix)
        val ofPattern = Regex(
            """of\s+([0-9,]+(?:\.\d{2})?)\s+is successful""",
            RegexOption.IGNORE_CASE
        )
        ofPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Fund transfer = expense (money sent)
        if (lowerMessage.contains("fund transfer") ||
            lowerMessage.contains("transfer") && lowerMessage.contains("to a/c")
        ) {
            return TransactionType.EXPENSE
        }

        // Withdrawn = expense
        if (lowerMessage.contains("withdrawn")) {
            return TransactionType.EXPENSE
        }

        // Wallet load = expense (loading money into wallet)
        if (lowerMessage.contains("wallet load") || lowerMessage.contains("esewa wallet")) {
            return TransactionType.EXPENSE
        }

        // Deposit = income
        if (lowerMessage.contains("deposited") || lowerMessage.contains("credited")) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "Fund transfer to A/C ..."
        if (message.contains("Fund transfer", ignoreCase = true) ||
            message.contains("transfer", ignoreCase = true)
        ) {
            return "Fund Transfer"
        }

        // Pattern 2: ATM/Cash withdrawal (contains "withdrawn" and account pattern)
        if (message.contains("withdrawn", ignoreCase = true)) {
            // Check if it mentions ATM or specific location
            val atmPattern = Regex("""at\s+([^.\n]+?)(?:\s+on|\.)""", RegexOption.IGNORE_CASE)
            atmPattern.find(message)?.let { match ->
                val location = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(location)) {
                    return "ATM - $location"
                }
            }
            return "ATM Withdrawal"
        }

        // Pattern 3: "Esewa Wallet Load for 9850000007"
        val esewaPattern = Regex(
            """Esewa Wallet Load for\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        esewaPattern.find(message)?.let { match ->
            return "Esewa Wallet Load"
        }

        // Pattern 4: Generic wallet load
        if (message.contains("Wallet Load", ignoreCase = true)) {
            return "Wallet Load"
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "A/C 01000000055" - extract last 4 digits
        val accountLongPattern = Regex(
            """A/C\s+(\d{8,})""",
            RegexOption.IGNORE_CASE
        )
        accountLongPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            return if (accountStr.length >= 4) {
                accountStr.takeLast(4)
            } else {
                accountStr
            }
        }

        // Pattern 2: "A/C 0#16" - special format with # separator
        val accountHashPattern = Regex(
            """A/C\s+(\d+)#(\d+)""",
            RegexOption.IGNORE_CASE
        )
        accountHashPattern.find(message)?.let { match ->
            // Combine both parts for last 4 digits
            val part1 = match.groupValues[1]
            val part2 = match.groupValues[2]
            val combined = part1 + part2
            // Pad with leading zeros if needed to get 4 digits
            return if (combined.length >= 4) {
                combined.takeLast(4)
            } else {
                combined.padStart(4, '0')
            }
        }

        // Pattern 3: For transfers, extract destination account
        val toAccountPattern = Regex(
            """to A/C\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        toAccountPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            return if (accountStr.length >= 4) {
                accountStr.takeLast(4)
            } else {
                accountStr
            }
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "(FBS:D:FPQR:523396049)" - transaction reference
        val fbsPattern = Regex(
            """\(FBS:D:FPQR:(\d+)\)"""
        )
        fbsPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Generic reference number pattern
        val refPattern = Regex(
            """Ref(?:erence)?[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("password") ||
            lowerMessage.contains("click here to learn more") && !lowerMessage.contains("withdrawn")
        ) {
            // Exception: "Enjoy the new features... A/C withdrawn" is still a transaction
            if (!lowerMessage.contains("withdrawn")) {
                return false
            }
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "fund transfer",
            "withdrawn",
            "deposited",
            "wallet load",
            "successful",
            "credited"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}
