package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Bankino (digital arm of Middle East Bank / بانک خاورمیانه), Iran.
 *
 * Handles line-based Persian SMS such as:
 *   بانک خاورمیانه
 *   خرید با کارت 7284
 *   -3,300,000
 *   XXX/XXXXXXXX
 *   مانده 14,412,600
 *   03/22
 *   12:49
 *
 * Notes:
 * - Amounts use Western digits with comma grouping; only date/time use Persian
 *   digits, which we do not parse (timestamp comes from SMS metadata).
 * - The leading sign on the amount is the reliable type signal:
 *   "-" => EXPENSE, "+" => INCOME.
 * - Currency is Iranian Rial (IRR).
 */
class BankinoBankParser : BaseIranianBankParser() {

    override fun getBankName() = "Bankino"

    // "بانک خاورمیانه" = Middle East Bank (Bankino).
    private val bankNameMarker = "بانک خاورمیانه"

    // "کارت" = card; followed by the masked last 4 digits.
    private val cardPattern = Regex("""کارت\s+(\d{3,})""")

    // Signed amount on its own line: leading +/- then a comma-grouped number.
    private val signedAmountPattern = Regex("""([+-])\s*([0-9][0-9,]*)""")

    // "مانده" = balance, followed by a comma-grouped number.
    private val balancePattern = Regex("""مانده\s+([0-9][0-9,]*)""")

    override fun canHandle(sender: String): Boolean {
        val digits = sender.filter { it.isDigit() }
        return digits.contains("20004861")
    }

    override fun isTransactionMessage(message: String): Boolean {
        // Must look like a Bankino SMS and carry a signed amount.
        return message.contains(bankNameMarker) &&
                signedAmountPattern.containsMatchIn(message)
    }

    override fun extractAmount(message: String): BigDecimal? {
        signedAmountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[2].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        signedAmountPattern.find(message)?.let { match ->
            return if (match.groupValues[1] == "-") {
                TransactionType.EXPENSE
            } else {
                TransactionType.INCOME
            }
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        cardPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Bankino SMS carries no merchant/payee field.
        return null
    }
}
