package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for blu Bank (بلو), Iran.
 *
 * Handles line-based Persian SMS such as:
 *   بلو
 *   برداشت پول
 *   <NAME> عزیز، 2,500,000 ریال از حساب شما پرید.
 *   موجودی: 488,152 ریال
 *   ۷:۲۸
 *   ۱۴۰۵.۰۳.۲۲
 *
 * Notes:
 * - Amounts use Western digits with comma grouping; only date/time use Persian
 *   digits, which we do not parse (timestamp comes from SMS metadata).
 * - Type signals: "برداشت پول" / "پرید" => EXPENSE (money left the account);
 *   "واریز پول" / "نشست" => INCOME (money landed in the account).
 * - blu samples carry no card/account number, so accountLast4 is always null.
 * - Currency is Iranian Rial (IRR).
 *
 * NOTE: blu sender IDs vary widely with no reliable shared prefix (e.g.
 * "0999 998 7641", "+989999987641", "98300087641"). The only stable core
 * across observed samples is the "87641" suffix, plus the "9999987641" core.
 * canHandle matches those; additional sender IDs may need to be added as more
 * samples surface (known limitation).
 */
class BluBankParser : BankParser() {

    override fun getBankName() = "blu Bank"

    override fun getCurrency() = "IRR"

    // "بلو" = blu (bank-name line; strongest in-body signal).
    private val bankNameMarker = "بلو"

    // "ریال" = Rial. Main sentence amount: a comma-grouped number before "ریال".
    private val amountPattern = Regex("""([0-9][0-9,]*)\s*ریال""")

    // "موجودی:" = balance, followed by a comma-grouped number before "ریال".
    private val balancePattern = Regex("""موجودی:\s*([0-9][0-9,]*)\s*ریال""")

    override fun canHandle(sender: String): Boolean {
        val digits = sender.filter { it.isDigit() }
        return digits.endsWith("87641") || digits.contains("9999987641")
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (!message.contains(bankNameMarker)) return false
        // Must carry one of the action signals.
        return message.contains("برداشت پول") || message.contains("پرید") ||
                message.contains("واریز پول") || message.contains("نشست")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // First "<number> ریال" is the transaction amount (balance line comes later).
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        if (message.contains("برداشت پول") || message.contains("پرید")) {
            return TransactionType.EXPENSE
        }
        if (message.contains("واریز پول") || message.contains("نشست")) {
            return TransactionType.INCOME
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
        // blu samples carry no card/account number.
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // blu SMS carries no merchant/payee field.
        return null
    }
}
