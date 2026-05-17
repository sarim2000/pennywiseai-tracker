package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Sparkasse Rhein-Maas (Germany) SMS notifications.
 *
 * Sender: "Sparkasse"
 * Currency: EUR
 *
 * Supported message types ("Wecker" = alarm / push notification):
 *  - Kartenwecker      -> card purchase (EXPENSE), signed negative amount.
 *      Kartenwecker:
 *      1 neuer Kartenumsatz auf dem Konto *NNNN:
 *      MERCHANT: -70,85 EUR
 *      Neuer Saldo: 991,84 EUR
 *      Ihre Sparkasse
 *
 *  - Gehaltswecker     -> salary credit (INCOME), unsigned amount.
 *      Gehaltswecker:
 *      Gehalt ist auf Konto *NNNN eingegangen:
 *      SOURCE: 1.415,62 EUR
 *      Neuer Saldo: 1.415,67 EUR
 *      Ihre Sparkasse
 *
 *  - Kontostandswecker -> pure balance notification, no transaction amount/merchant.
 *      Rejected via isTransactionMessage so the base parse() returns null.
 *
 * Notes on number formatting (German locale):
 *  - `.` is the thousands separator, `,` is the decimal separator.
 *  - Strip `.` and replace `,` with `.` before constructing BigDecimal.
 */
class SparkasseRheinMaasParser : BankParser() {

    override fun getBankName() = "Sparkasse Rhein-Maas"

    override fun getCurrency() = "EUR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("SPARKASSE")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Skip OTP / verification codes if they ever appear.
        if (lower.contains("otp") ||
            lower.contains("tan") && lower.contains("code") ||
            lower.contains("verifizierungscode")
        ) {
            return false
        }

        // Kontostandswecker = balance-only push, no parseable transaction.
        if (lower.contains("kontostandswecker")) {
            return false
        }

        // Must be one of the known transaction "Wecker" variants.
        return lower.contains("kartenwecker") || lower.contains("gehaltswecker")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Find the first transaction line of the form "<label>: [+/-]<number> EUR"
        // and explicitly skip the "Neuer Saldo:" balance line.
        val transactionLine = findTransactionLine(message) ?: return null
        val amountMatch = TRANSACTION_AMOUNT_REGEX.find(transactionLine) ?: return null
        val raw = amountMatch.groupValues[2]
        return parseGermanNumber(raw)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        if (lower.contains("gehaltswecker")) {
            return TransactionType.INCOME
        }

        // Fall back to the sign on the amount line.
        val transactionLine = findTransactionLine(message)
        if (transactionLine != null) {
            val signMatch = TRANSACTION_AMOUNT_REGEX.find(transactionLine)
            val sign = signMatch?.groupValues?.get(1)
            if (sign == "+") return TransactionType.INCOME
            if (sign == "-") return TransactionType.EXPENSE
        }

        if (lower.contains("kartenwecker")) {
            return TransactionType.EXPENSE
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val transactionLine = findTransactionLine(message) ?: return null
        // Merchant is everything before the colon on the transaction line.
        val colonIdx = transactionLine.indexOf(':')
        if (colonIdx <= 0) return null
        val candidate = transactionLine.substring(0, colonIdx).trim()
        val cleaned = cleanMerchantName(candidate)
        return if (isValidMerchantName(cleaned)) cleaned else null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Konto *1832"
        ACCOUNT_REGEX.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        BALANCE_REGEX.find(message)?.let { match ->
            return parseGermanNumber(match.groupValues[1])
        }
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        return message.lowercase().contains("kartenwecker") ||
                message.lowercase().contains("kartenumsatz")
    }

    /**
     * Returns the first non-balance line that contains a `<label>: <amount> EUR` payload.
     */
    private fun findTransactionLine(message: String): String? {
        for (rawLine in message.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.lowercase().startsWith("neuer saldo")) continue
            if (TRANSACTION_AMOUNT_REGEX.containsMatchIn(line)) {
                return line
            }
        }
        return null
    }

    private fun parseGermanNumber(raw: String): BigDecimal? {
        // German format: "1.415,62" -> "1415.62"; "70,85" -> "70.85"
        val normalized = raw
            .replace(".", "")
            .replace(",", ".")
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }

    companion object {
        // Matches "[+/-]<german-number> EUR" anywhere on the line.
        // Group 1: optional sign. Group 2: the numeric body (with German separators).
        private val TRANSACTION_AMOUNT_REGEX = Regex(
            """([+-])?\s*(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:,\d{1,2})?)\s*EUR""",
            RegexOption.IGNORE_CASE
        )

        // "Neuer Saldo: 991,84 EUR" or "Neuer Saldo 991,84 EUR"
        private val BALANCE_REGEX = Regex(
            """Neuer\s+Saldo:?\s*([+-]?\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:,\d{1,2})?)\s*EUR""",
            RegexOption.IGNORE_CASE
        )

        // "Konto *1832" - capture the digits after the asterisk.
        private val ACCOUNT_REGEX = Regex(
            """Konto\s*\*+\s*(\d{3,})""",
            RegexOption.IGNORE_CASE
        )
    }
}
