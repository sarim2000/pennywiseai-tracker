package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Millennium BIM (Mozambique) SMS messages (Portuguese).
 *
 * Amounts use a dot decimal separator (e.g. "1000.00") and currency "MZN".
 * Dates are DD/MM/YY, times HH:MM (24h).
 *
 * Supported formats:
 * - Debit (EXPENSE):
 *   "A conta 123456789 foi debitada no valor de 50.00 MZN as 10:14 do dia 31/05/26. Em caso de duvida, ligue 8003500. Millennium bim"
 * - Credit (INCOME):
 *   "A conta 123456789 recebeu o valor de 1000.00 MZN as 12:16 do dia 26/04/26. Em  caso de duvida, ligue 8003500. Millennium bim"
 * - Debit with commission/fee:
 *   "A conta 123456789 foi debitada no valor de 1000.00 MZN as 12:25 do dia 27/01/26, comissao 30.00 MZN. Em caso de duvida, ligue 8003500. Millennium bim"
 *
 * Portuguese keys:
 * - "foi debitada" = debit  (EXPENSE)
 * - "recebeu"      = credit (INCOME)
 * - "no valor de X MZN" = amount
 * - "comissao X MZN"    = fee (when present)
 * - "A conta NNN"       = account number
 *
 * These messages have no merchant and no balance — that is expected.
 * Only the "foi debitada / recebeu" formats are handled; anything else returns null.
 */
class MillenniumBimParser : BankParser() {

    override fun getBankName() = "Millennium BIM"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        return sender.equals("Mbim", ignoreCase = true)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("foi debitada") || lower.contains("recebeu o valor")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "no valor de 50.00 MZN" (debit) or "o valor de 1000.00 MZN" (credit)
        val pattern = Regex(
            """\bvalor\s+de\s+([0-9]+\.[0-9]{2})\s*MZN""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1])
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("foi debitada") -> TransactionType.EXPENSE
            lower.contains("recebeu o valor") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // "A conta 123456789 ..."
        val pattern = Regex(
            """A\s+conta\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? = null

    override fun extractBalance(message: String): BigDecimal? = null

    override fun extractReference(message: String): String? = null
}
