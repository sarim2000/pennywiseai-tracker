package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Parser for Union Bank of India SMS messages
 *
 * Supported formats:
 * - Debit: "A/c *1234 Debited for Rs:100.00 on 11-08-2025 18:28:02 by Mob Bk ref no 123456789000 Avl Bal Rs:12345.67"
 * - Credit transactions
 * - ATM withdrawals
 * - UPI transactions
 *
 * Sender patterns: XX-UNIONB-S/T, UNIONB, UNIONBANK, etc.
 */
class UnionBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Union Bank of India"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("UNIONB") ||
                normalizedSender.contains("UNIONBANK") ||
                normalizedSender.contains("UBOI") ||
                // DLT patterns for transactions (-S, -T suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-UNIONB-[ST]$")) ||
                // Other DLT patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-UNIONB-[TPG]$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-UNIONB$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-UNIONBANK$"))
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Union Bank includes "Never Share OTP/PIN/CVV" warning in transaction messages
        // Check if it's actually a transaction first before rejecting due to OTP keyword
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid"
        )

        if (transactionKeywords.any { lowerMessage.contains(it) }) {
            // It's a transaction message, even if it contains OTP in warning text
            return true
        }

        // Fall back to parent logic for non-transaction messages
        return super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "Rs:100.00" or "Rs.100.00" (Union Bank format with colon)
        val amountPattern1 = Regex("""Rs[:.]?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        amountPattern1.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "INR 500" format
        val amountPattern2 = Regex("""INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        amountPattern2.find(message)?.let { match ->
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
        // Pattern 1: Mobile Banking - "by Mob Bk"
        if (message.contains("Mob Bk", ignoreCase = true)) {
            return "Mobile Banking Transfer"
        }

        // Pattern 2: ATM transactions
        if (message.contains("ATM", ignoreCase = true)) {
            val atmPattern = Regex(
                """at\s+([^.\s]+(?:\s+[^.\s]+)*)(?:\s+on|\s+Avl|$)""",
                RegexOption.IGNORE_CASE
            )
            atmPattern.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
            return "ATM Withdrawal"
        }

        // Pattern 3: UPI transactions - "UPI/merchant" or "VPA merchant@bank"
        if (message.contains("UPI", ignoreCase = true)) {
            val upiPattern = Regex("""UPI[/:]?\s*([^,.\s]+)""", RegexOption.IGNORE_CASE)
            upiPattern.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        if (message.contains("VPA", ignoreCase = true)) {
            val vpaPattern = Regex("""VPA\s+([^@\s]+)""", RegexOption.IGNORE_CASE)
            vpaPattern.find(message)?.let { match ->
                val vpaName = match.groupValues[1].trim()
                return parseUPIMerchant(vpaName)
            }
        }

        // Pattern 4: "to <merchant>" for transfers
        val toPattern = Regex("""to\s+([^.\n]+?)(?:\s+on|\s+Avl|$)""", RegexOption.IGNORE_CASE)
        toPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (!merchant.contains("Avl", ignoreCase = true)) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 5: "from <sender>" for credits
        val fromPattern = Regex("""from\s+([^.\n]+?)(?:\s+on|\s+Avl|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (!merchant.contains("Avl", ignoreCase = true)) {
                return cleanMerchantName(merchant)
            }
        }

        // Fall back to base class extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        // Union Bank format: "ref no 123456789000"
        val refPatterns = listOf(
            Regex("""ref\s+no\s+([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""ref[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""reference[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""txn[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in refPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        return super.extractReference(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Union Bank format: "A/c *1234" or "A/C X1234"
        val accountPatterns = listOf(
            Regex("""A/[Cc]\s*[*X](\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""Account\s*[*X](\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""Acc\s*[*X](\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""A/[Cc]\s+(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Union Bank format: "Avl Bal Rs:12345.67" or "Avl Bal Rs.12345.67"
        val balancePatterns = listOf(
            Regex("""Avl\s+Bal\s+Rs[:.]?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex(
                """Available\s+Balance[:.]?\s*Rs[:.]?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""Balance[:.]?\s*Rs[:.]?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Bal[:.]?\s*Rs[:.]?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
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

    private fun parseUPIMerchant(vpa: String): String {
        val cleanVPA = vpa.lowercase()

        return when {
            // Common payment apps and merchants
            cleanVPA.contains("paytm") -> "Paytm"
            cleanVPA.contains("phonepe") -> "PhonePe"
            cleanVPA.contains("googlepay") || cleanVPA.contains("gpay") -> "Google Pay"
            cleanVPA.contains("bharatpe") -> "BharatPe"
            cleanVPA.contains("amazon") -> "Amazon"
            cleanVPA.contains("flipkart") -> "Flipkart"
            cleanVPA.contains("swiggy") -> "Swiggy"
            cleanVPA.contains("zomato") -> "Zomato"
            cleanVPA.contains("uber") -> "Uber"
            cleanVPA.contains("ola") -> "Ola"

            // Individual transfers (just numbers)
            cleanVPA.matches(Regex("\\d+")) -> "Individual"

            // Default - clean up the VPA name
            else -> {
                val parts = cleanVPA.split(".", "-", "_")
                parts.firstOrNull { it.length > 3 && !it.all { char -> char.isDigit() } }
                    ?.replaceFirstChar { it.uppercase() } ?: "Merchant"
            }
        }
    }
}