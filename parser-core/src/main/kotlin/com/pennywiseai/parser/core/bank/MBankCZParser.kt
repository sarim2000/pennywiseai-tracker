package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for mBank CZ (Czech Republic) SMS messages
 *
 * Supported formats:
 * - Card payment: "Nová platba kartou\n100,00 CZK v obchodě MERCHANT."
 * - Incoming transfer: "Příchozí platba\n500,00 CZK od odesílatele SENDER."
 * - Outgoing transfer: "Odchozí platba\n250,00 CZK na účet ACCOUNT."
 *
 * Notes:
 * - Czech uses comma as decimal separator (100,00)
 * - Currency: CZK (Czech Koruna)
 */
class MBankCZParser : BankParser() {

    override fun getBankName() = "mBank CZ"

    override fun getCurrency() = "CZK"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("MBANK") && normalized.contains("CZ") ||
                normalized == "MBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "100,00 CZK" or "1 500,00 CZK"
        val amountPattern = Regex(
            """(\d[\d\s]*(?:,\d{1,2})?)\s*CZK""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1]
                .replace(" ", "")
                .replace(",", ".")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()

        return when {
            lower.contains("příchozí") -> TransactionType.INCOME       // incoming
            lower.contains("nová platba kartou") -> TransactionType.EXPENSE // card payment
            lower.contains("odchozí") -> TransactionType.EXPENSE       // outgoing
            lower.contains("výběr") -> TransactionType.EXPENSE         // withdrawal
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "v obchodě MERCHANT." (card payment - "at store")
        val storePattern = Regex(
            """v obchodě\s+(.+?)\.""",
            RegexOption.IGNORE_CASE
        )
        storePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "od odesílatele SENDER." (incoming - "from sender")
        val fromPattern = Regex(
            """od odesílatele\s+(.+?)\.""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "na účet ACCOUNT." (outgoing - "to account")
        val toPattern = Regex(
            """na účet\s+(.+?)\.""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            if (raw.isNotBlank()) {
                return raw
            }
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        return message.lowercase().contains("platba kartou")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        if (lower.contains("otp") || lower.contains("heslo") || lower.contains("kód")) {
            return false
        }

        val keywords = listOf(
            "platba kartou",  // card payment
            "příchozí platba", // incoming payment
            "odchozí platba",  // outgoing payment
            "výběr"            // withdrawal
        )
        return keywords.any { lower.contains(it) }
    }
}
