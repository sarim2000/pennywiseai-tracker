package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for bKash (Bangladesh) mobile money SMS messages.
 *
 * Currency is BDT; the symbol is written as "Tk". Sender: bKash.
 * Amounts always carry two decimals and may use thousand separators.
 *
 * Supported formats:
 * - Received (INCOME):
 *   "You have received Tk 6,400.00 from xxxx. Fee Tk 0.00. Balance Tk 20,288.41. TrxID xxxx at 26/05/2026 10:58."
 * - Cash In (INCOME):
 *   "Cash In Tk 500.00 from XXXXXXXXXX successful. Fee Tk 0.00. Balance Tk 506.91. TrxID XXXXX at 29/05/2026 19:00. Download App: ..."
 * - Payment (EXPENSE):
 *   "Payment of Tk 20.00 to xxxx is successful. Balance Tk 20,268.41. TrxID xxxx at 26/05/2026 15:07"
 * - Send Money (EXPENSE):
 *   "Send Money Tk 0.20 to XXXXXXXXXX successful. Ref 2. Fee Tk 0.00. Balance Tk 0.08. TrxID XXXXX at 07/06/2026 22:45."
 *
 * TODO: Bill Payment format is not yet covered (sample was truncated in the source issue).
 *
 * The counterparty (from/to) is masked in bKash SMS, so no merchant is extracted.
 */
class BkashParser : BankParser() {

    override fun getBankName() = "bKash"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("BKASH")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("you have received") ||
                lower.contains("cash in") ||
                lower.contains("payment of") ||
                lower.contains("send money")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // The transaction amount is always the first "Tk <amount>" in the message
        // (Fee and Balance amounts appear later).
        val pattern = Regex("""Tk\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("you have received") -> TransactionType.INCOME
            lower.contains("cash in") -> TransactionType.INCOME
            lower.contains("payment of") -> TransactionType.EXPENSE
            lower.contains("send money") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Balance Tk 20,288.41."
        val pattern = Regex("""Balance\s+Tk\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractReference(message: String): String? {
        // "TrxID ABC12345 at ..."
        val pattern = Regex("""TrxID\s+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { return it.groupValues[1] }
        return null
    }

    // Counterparty is masked; no merchant is available.
    override fun extractMerchant(message: String, sender: String): String? = null

    override fun extractAccountLast4(message: String): String? = null
}
