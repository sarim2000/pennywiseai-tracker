package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Nations Trust Bank (Sri Lanka) SMS messages.
 *
 * Currency is LKR. Sender: NationsSMS.
 *
 * Supported formats:
 * - Credit-card purchase (CREDIT):
 *   "Transaction Approved on your Card 123456******1234 for LKR 500.00 at BILL PAYMENT VIA NATIONS
 *    Available Bal LKR 12345.73  Call 0114315315 for any inquiry."
 *
 * Not treated as a transaction:
 * - Credit-card bill settlement ("...payment of LKR 57,018.67 made to Card # 123456*****1234 on ..."):
 *   money paid TO the card to reduce the outstanding balance. There is no clean transaction
 *   type for it and it must not be misclassified as a spend, so we skip it — consistent with
 *   how ICICIBankParser skips credit-card bill payments.
 *
 * Cards expose the last 4 digits with a masked middle ("123456******1234"); the last 4 are
 * extracted. (Digits shown here are synthetic — real card numbers are PII, never committed.)
 */
class NationsTrustBankParser : BankParser() {

    override fun getBankName() = "Nations Trust Bank"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("NATIONSSMS")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        // Skip credit-card bill settlement ("...made to Card #...").
        if (lower.contains("made to card")) {
            return false
        }
        return lower.contains("approved on your card")
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // Card purchases post as spend on the credit card.
        return if (message.contains("approved on your card", ignoreCase = true)) {
            TransactionType.CREDIT
        } else {
            null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "for LKR 500.00 at ..." — tolerate optional thousand separators.
        val pattern = Regex("""for\s+LKR\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // "at BILL PAYMENT VIA NATIONS Available Bal LKR ..."
        val pattern = Regex("""\bat\s+(.+?)\s+Available\s+Bal""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Available Bal LKR 12345.73"
        val pattern = Regex("""Available\s+Bal\s+LKR\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "Card 123456******1234 for LKR ..." — last 4 digits after the masked middle.
        val pattern = Regex("""Card\s+\d{4,6}\*+(\d{4})""", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)
    }
}
