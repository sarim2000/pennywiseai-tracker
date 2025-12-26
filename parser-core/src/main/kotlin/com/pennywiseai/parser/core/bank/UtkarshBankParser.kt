package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Utkarsh Small Finance Bank (SFBL) SuperCard credit card transactions.
 * Handles messages from UTKSPR and similar senders.
 */
class UtkarshBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Utkarsh Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("UTKSPR") ||
                normalizedSender.contains("UTKARSH") ||
                normalizedSender.contains("UTKSFB")
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "for UPI - merchant/reference"
        val upiPattern = Regex("""for\s+UPI\s*[-–]\s*([^\s.]+)""", RegexOption.IGNORE_CASE)
        upiPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Check if it's just a reference number (all digits or with x's)
            if (!merchant.matches(Regex("""[x0-9]+"""))) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: "for merchant on date"
        val forPattern =
            Regex("""for\s+([^0-9][^\s]+?)(?:\s+on\s+|\s+at\s+|$)""", RegexOption.IGNORE_CASE)
        forPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (!merchant.equals("UPI", ignoreCase = true) &&
                !merchant.equals("INR", ignoreCase = true)
            ) {
                return cleanMerchantName(merchant)
            }
        }

        // Check for specific patterns
        return when {
            lowerMessage.contains("supercard") && lowerMessage.contains("upi") -> "UPI Payment"
            else -> super.extractMerchant(message, sender) ?: "Utkarsh SuperCard"
        }
    }

    override fun extractTransactionType(message: String): TransactionType {
        // Utkarsh SuperCard is a credit card product, all transactions are credit
        return TransactionType.CREDIT
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern for SuperCard xxxx
        val cardPattern = Regex("""SuperCard\s+[xX*]*(\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern for account XXXX
        val accountPattern = Regex("""(?:account|a/c)\s+[xX*]*(\d{4})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }
}