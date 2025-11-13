package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Priorbank (Belarus) SMS messages
 *
 * Handles formats like:
 * - "Karta 6***6666 29-10-25 18:34:25. Oplata 12.90 BYN. BLR RBO N77 "KFC Zavod". Dostupno: 947.09 BYN."
 *
 * Common keywords:
 * - "Karta" = Card
 * - "Oplata" = Payment (expense)
 * - "Dostupno" = Available (balance)
 * - Currency: BYN (Belarusian Ruble)
 */
class PriorbankParser : BankParser() {

    override fun getBankName() = "Priorbank"

    override fun getCurrency() = "BYN"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("PRIORBANK") ||
                normalizedSender == "PRIORBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "Oplata 12.90 BYN" or "Oplata 8.00 BYN"
        val oplataPattern = Regex(
            """Oplata\s+([0-9]+(?:\.\d{2})?)\s+BYN""",
            RegexOption.IGNORE_CASE
        )
        oplataPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1]
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

        // "Oplata" means payment/expense in Belarusian/Russian
        if (lowerMessage.contains("oplata")) {
            return TransactionType.EXPENSE
        }

        // For future: could add support for income transactions
        // "Popolnenie" or "Zachislenie" typically mean credit/income
        if (lowerMessage.contains("popolnenie") || lowerMessage.contains("zachislenie")) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: Merchant in quotes "KFC Zavod"
        val quotedPattern = Regex(
            """"([^"]+)"""",
            RegexOption.IGNORE_CASE
        )
        quotedPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: Location/merchant after "BYN. " and before ". Dostupno"
        // Example: "BYN. BLR AZS N55. Dostupno"
        val locationPattern = Regex(
            """BYN\.\s+([^.]+?)\.\s+Dostupno""",
            RegexOption.IGNORE_CASE
        )
        locationPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            // Clean up common prefixes
            merchant = merchant.replace(Regex("""^BLR\s+"""), "") // Remove "BLR " prefix

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Karta 6***6666" - extract digits only
        val kartaPattern = Regex(
            """Karta\s+[6-9][\*]+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        kartaPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Dostupno: 947.09 BYN" or "Dostupno: 250.70 BYN"
        val dostupnoPattern = Regex(
            """Dostupno:\s+([0-9]+(?:\.\d{2})?)\s+BYN""",
            RegexOption.IGNORE_CASE
        )
        dostupnoPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1]
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("kod") || // "kod" = code in Russian
            lowerMessage.contains("parol")
        ) { // "parol" = password in Russian
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "oplata",      // payment
            "karta",       // card
            "dostupno"     // available balance
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}
