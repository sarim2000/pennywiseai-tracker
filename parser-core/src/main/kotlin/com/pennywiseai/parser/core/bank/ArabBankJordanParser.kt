package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Arab Bank (Jordan).
 *
 * Handles Jordanian Dinar (JOD) and USD transactions. JOD amounts use three
 * decimal places (e.g. "JOD 2.750"), so amount/balance extraction accepts 1-3
 * fractional digits and the currency token can appear either before the amount
 * ("JOD 50.000") or after it ("37.920JOD").
 *
 * Example SMS formats:
 * - Card purchase:
 *   "A Trx using Card XXXX9915 from ADANI CORNER for JOD 2.750 on 20-Jun-2026
 *    at 14:21 GMT+3. Available balance is JOD 1649.832."
 * - Account debit (transfer):
 *   "JOD50.000 has been debited from 0156*500 to <name> as CliQ transfer
 *    Balance 37.920JOD"
 * - Account credit (transfer):
 *   "JOD172.000 has been credited to 0156*500from <name> as CliQ transfer
 *    Balance 959.370JOD"
 *
 * Transfer/payee extraction is not tied to any specific scheme (e.g. CliQ): the
 * counterparty name is read up to the transfer description ("... as ..."), the
 * balance section, or end of line, so other transfer types parse the same way.
 */
class ArabBankJordanParser : BankParser() {

    override fun getBankName() = "Arab Bank Jordan"

    override fun getCurrency() = "JOD"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace(Regex("[\\s\\-_]+"), "")
        return normalizedSender.contains("ARABBANK") ||
                // DLT-style senders e.g. "AB-ARABBK-S"
                normalizedSender.matches(Regex("^[A-Z]{2}ARABBK[A-Z]?$"))
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val transaction = super.parse(smsBody, sender, timestamp) ?: return null
        val extractedCurrency = extractCurrency(smsBody)
        return if (extractedCurrency != null) {
            transaction.copy(currency = extractedCurrency)
        } else {
            transaction
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code") ||
            lowerMessage.contains("do not share") ||
            lowerMessage.contains("declined") ||
            lowerMessage.contains("failed")
        ) {
            return false
        }

        val transactionKeywords = listOf(
            "trx using card",
            "has been debited",
            "has been credited"
        )

        if (transactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }

    override fun extractCurrency(message: String): String? {
        // Pick the currency token next to the transaction amount (the first one
        // in the message). Both prefix ("JOD 50.000") and suffix ("37.920JOD")
        // forms are covered by scanning for the first JOD/USD occurrence.
        CURRENCY_TOKEN.find(message)?.let { return it.groupValues[1].uppercase() }
        return getCurrency()
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // Currency before amount: "JOD 2.750", "USD50.000"
            Regex("""(?:JOD|USD)\s*([0-9,]+(?:\.\d{1,3})?)""", RegexOption.IGNORE_CASE),
            // Amount before currency: "50.000 JOD"
            Regex("""([0-9,]+(?:\.\d{1,3})?)\s*(?:JOD|USD)""", RegexOption.IGNORE_CASE)
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

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            // "Available balance is JOD 1649.832"
            Regex("""balance\s+is\s+(?:JOD|USD)\s*([0-9,]+(?:\.\d{1,3})?)""", RegexOption.IGNORE_CASE),
            // "Balance JOD 1649.832"
            Regex("""balance\s+(?:JOD|USD)\s*([0-9,]+(?:\.\d{1,3})?)""", RegexOption.IGNORE_CASE),
            // "Balance 37.920JOD" / "Balance 959.370 JOD"
            Regex("""balance\s+([0-9,]+(?:\.\d{1,3})?)\s*(?:JOD|USD)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit card spend is tracked as CREDIT in this app
            lowerMessage.contains("trx using card") -> TransactionType.CREDIT
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("credited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Card purchase: "... from ADANI CORNER for JOD 2.750 ..."
        if (lowerMessage.contains("trx using card")) {
            Regex("""from\s+(.+?)\s+for\s+(?:JOD|USD)""", RegexOption.IGNORE_CASE)
                .find(message)?.let { match ->
                    val merchant = cleanMerchantName(match.groupValues[1].trim())
                    if (isValidMerchantName(merchant)) return merchant
                }
        }

        // Outgoing transfer: "... debited from <acct> to <name> [as <type> transfer] ..."
        if (lowerMessage.contains("debited")) {
            Regex("""to\s+(.+?)$COUNTERPARTY_TERMINATOR""", RegexOption.IGNORE_CASE)
                .find(message)?.let { match ->
                    val merchant = cleanMerchantName(match.groupValues[1].trim())
                    if (isValidMerchantName(merchant)) return merchant
                }
        }

        // Incoming transfer: "... credited to <acct> from <name> [as <type> transfer] ..."
        if (lowerMessage.contains("credited")) {
            Regex("""from\s+(.+?)$COUNTERPARTY_TERMINATOR""", RegexOption.IGNORE_CASE)
                .find(message)?.let { match ->
                    val merchant = cleanMerchantName(match.groupValues[1].trim())
                    if (isValidMerchantName(merchant)) return merchant
                }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Card: "Card XXXX9915"
        Regex("""Card\s+([X*0-9]+)""", RegexOption.IGNORE_CASE).find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }

        // CliQ account: "from 0156*500 to ..." / "to 0156*500from ..."
        Regex("""(?:from|to)\s+([0-9][0-9*]{2,})""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                extractLast4Digits(match.groupValues[1])?.let { return it }
            }

        return super.extractAccountLast4(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("trx using card")) return true
        // Account-to-account transfers ("debited from ... to", "credited to ... from")
        // are never card transactions, regardless of the transfer scheme.
        if (lowerMessage.contains("debited from") || lowerMessage.contains("credited to")) return false
        return super.detectIsCard(message)
    }

    private companion object {
        val CURRENCY_TOKEN = Regex("""(JOD|USD)""", RegexOption.IGNORE_CASE)

        // Ends a counterparty name at the transfer description ("... as ..."),
        // the balance section, a line break, or end of message. Kept generic so
        // the parser is not tied to a particular transfer scheme (e.g. CliQ).
        const val COUNTERPARTY_TERMINATOR = """(?:\s+as\s+|\s+Balance\b|\n|$)"""
    }
}
