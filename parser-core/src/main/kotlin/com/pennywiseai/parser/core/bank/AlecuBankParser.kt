package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for ALECU (America's Largest Electric Credit Union) SMS messages.
 *
 * Supported formats:
 * - "ALEC Alert - A debit transaction from MERCHANT for $100.00 on account *1=01 was posted on Mar 30, 2026."
 * - "ALEC Alert - A credit transaction from MERCHANT for $50.00 on account *1=01 was posted on Mar 30, 2026."
 *
 * Sender: 39872
 */
class AlecuBankParser : BankParser() {

    override fun getBankName() = "ALECU"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized == "39872" ||
                normalized.contains("ALECU") ||
                normalized.contains("ALEC")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        return lower.contains("alec alert") && lower.contains("transaction from")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val amountPattern = Regex("""\$([0-9,]+(?:\.\d{2})?)""")
        return amountPattern.find(message)?.let {
            val amountStr = it.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("a debit transaction") -> TransactionType.EXPENSE
            lower.contains("a credit transaction") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "transaction from MERCHANT for $"
        val merchantPattern = Regex(
            """transaction\s+from\s+(.+?)\s+for\s+\$""",
            RegexOption.IGNORE_CASE
        )
        return merchantPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            // Clean up semicolon-separated metadata (e.g., "WE EGIES     ;12345 ;AUTOPAY")
            val cleaned = raw.split(";").first().trim()
            val merchant = cleanMerchantName(cleaned)
            if (isValidMerchantName(merchant)) merchant else null
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "account *1=01" — extract all digits around the '=' sign
        val accountPattern = Regex("""account\s+\*(\d+=\d+)""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].replace("=", "")
            if (raw.isNotEmpty()) return raw
        }
        return super.extractAccountLast4(message)
    }
}
