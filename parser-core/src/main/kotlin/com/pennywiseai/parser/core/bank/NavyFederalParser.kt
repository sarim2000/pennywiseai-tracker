package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Navy Federal Credit Union (NFCU) - handles USD debit card and credit card transactions
 */
class NavyFederalParser : BankParser() {

    override fun getBankName() = "Navy Federal Credit Union"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "NFCU" ||
                upperSender == "NAVYFED" ||
                upperSender.contains("NAVY FEDERAL") ||
                upperSender.contains("NAVYFEDERAL") ||
                upperSender.matches(Regex("""^[A-Z]{2}-NFCU-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // NFCU pattern: "Transaction for $3.26 was approved"
        val patterns = listOf(
            Regex(
                """Transaction for \$([0-9,]+(?:\.[0-9]{2})?)\s+was approved""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """Transaction for \$([0-9,]+(?:\.[0-9]{2})?)\s+was declined""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""for \$([0-9,]+(?:\.[0-9]{2})?)\s+was approved""", RegexOption.IGNORE_CASE),
            Regex("""for \$([0-9,]+(?:\.[0-9]{2})?)\s+was declined""", RegexOption.IGNORE_CASE)
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

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "at Google One at 08:19 PM" - captures merchant between first "at" and second "at" (for time)
        val merchantPattern = Regex(
            """on (?:debit|credit) card \d{4} at (.+?)\s+at \d{2}:\d{2}""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Alternative pattern without time: "at merchant."
        val simpleMerchantPattern =
            Regex("""on (?:debit|credit) card \d{4} at (.+?)(?:\.|$)""", RegexOption.IGNORE_CASE)
        simpleMerchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Clean up common trailing text
            return merchant.replace(Regex("""Txt STOP.*"""), "").trim()
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("was approved") -> TransactionType.EXPENSE
            lowerMessage.contains("was declined") -> null // Don't track declined transactions
            lowerMessage.contains("payment received") -> TransactionType.CREDIT
            lowerMessage.contains("deposit") -> TransactionType.CREDIT
            else -> null
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "on debit card xxxx" or "on credit card xxxx"
        val patterns = listOf(
            Regex("""on debit card (\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""on credit card (\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:debit|credit) card (\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // NFCU specific transaction keywords
        val nfcuTransactionKeywords = listOf(
            "transaction for",
            "was approved on",
            "was declined on"
        )

        if (nfcuTransactionKeywords.any { lowerMessage.contains(it) }) {
            // Exclude declined transactions
            if (lowerMessage.contains("was declined")) {
                return false
            }
            return true
        }

        return super.isTransactionMessage(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debit card") -> true
            lowerMessage.contains("credit card") -> true
            else -> super.detectIsCard(message)
        }
    }
}
