package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Kerala Gramin Bank (India) SMS messages
 *
 * Handles formats like:
 * - "Your a/c no. XXXX12345 is debited for Rs.160.00 on 28/7/25 05:06 PM and credited to a/c no. XXXXX00019 (UPI Ref no 170632692557)"
 * - "Dear Customer, Account XXXX123 is credited with INR 3000 on 20-10-2025 08:15:26 from 7025784485@upi. UPI Ref. no. 529807237409"
 *
 * Common senders: AD-KGBANK-S, BX-KGBANK-S
 * Currency: INR (Indian Rupee)
 */
class KeralaGraminBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Kerala Gramin Bank"

    override fun getCurrency() = "INR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("KGBANK") ||
                normalizedSender.contains("KERALA GRAMIN") ||
                normalizedSender.contains("KERALAGR")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "debited for Rs.160.00" or "credited with INR 3000"
        val debitCreditPattern = Regex(
            """(?:debited for|credited with)\s+(?:Rs\.?|INR)\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitCreditPattern.find(message)?.let { match ->
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

        // Debited = expense
        if (lowerMessage.contains("debited for") ||
            lowerMessage.contains("is debited")
        ) {
            return TransactionType.EXPENSE
        }

        // Credited = income
        if (lowerMessage.contains("credited with") ||
            lowerMessage.contains("is credited")
        ) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: UPI debit - "credited to a/c no. XXXXX00019 (UPI Ref"
        // This is money sent via UPI
        if (message.contains("debited", ignoreCase = true) &&
            message.contains("credited to", ignoreCase = true)
        ) {
            return "UPI Transfer"
        }

        // Pattern 2: UPI credit - "from 7025784485@upi" or "from merchant@paytm"
        val upiFromPattern = Regex(
            """from\s+([^.\s]+@[a-z]+)""",
            RegexOption.IGNORE_CASE
        )
        upiFromPattern.find(message)?.let { match ->
            val upiId = match.groupValues[1].trim()
            // If it's a phone number@provider, return generic UPI Payment
            val namePart = upiId.substringBefore("@")
            if (namePart.matches(Regex("""\d+"""))) {
                return "UPI Payment"
            }
            // Otherwise extract the name part before @
            if (namePart.isNotEmpty()) {
                return cleanMerchantName(namePart)
            }
            return "UPI Payment"
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Your a/c no. XXXX12345" or "Account XXXX123"
        val accountPattern = Regex(
            """(?:a/c no\.|Account)\s+(?:XXXX|XX)(\d{3,5})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            val digits = match.groupValues[1]
            return if (digits.length >= 4) {
                digits.takeLast(4)
            } else {
                digits.padStart(4, '0')
            }
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "UPI Ref no 170632692557" or "UPI Ref. no. 529807237409"
        val upiRefPattern = Regex(
            """UPI Ref\.?\s*no\.?\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("password")
        ) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited for",
            "is debited",
            "credited with",
            "is credited"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}
