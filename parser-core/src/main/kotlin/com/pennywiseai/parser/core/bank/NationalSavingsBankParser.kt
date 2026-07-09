package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for National Savings Bank (NSB, Sri Lanka) SMS messages.
 *
 * Currency is LKR. Sender: NSB.
 *
 * Supported formats:
 * - Credit (INCOME):
 *   "Dear MR <NAME>,LKR 10,000.00 Credited to your A/c XXXXXXXX1234 on DD/MM/YYYY at HH:mm.
 *    AvlBal LKR 12,653.61.Transaction CEFT Inward Transfer Deposit.Thank you for banking with us.Call Centre 1972."
 * - POS debit (EXPENSE):
 *   "Dear MR <NAME>,LKR 3,216.00 Debited from your A/c XXXXXXXX1234 on DD/MM/YYYY at HH:mm.
 *    AvlBal LKR 9,437.61. @ Wetara Pharamcy & Groc Polgasowita. ATM POS Transaction.Thank you for banking with us.Call Centre 1972."
 * - ATM / Internet-Mobile withdrawal (EXPENSE):
 *   "Dear MR <NAME>,LKR 2,800.00 Debited from your A/c XXXXXXXX1234 on DD/MM/YYYY at HH:mm.
 *    AvlBal LKR 24,037.61.Internet-Mobile Withdrawal.Thank you for banking with us.Call Centre 1972."
 *
 * The account number is masked with X's but exposes the last 4 digits ("XXXXXXXX1234");
 * the last 4 are extracted. (Digits and names shown here are synthetic — real account
 * numbers and customer names are PII and must never be committed.)
 */
class NationalSavingsBankParser : BankParser() {

    override fun getBankName() = "National Savings Bank"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s == "NSB" || s.endsWith("-NSB") || s.contains("-NSB-")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "LKR 10,000.00 Credited" / "LKR 3,216.00 Debited"
        val pattern = Regex(
            """LKR\s+([0-9,]+\.\d{2})\s+(?:Credited|Debited)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credited to") -> TransactionType.INCOME
            lower.contains("debited from") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // POS: "@ Wetara Pharamcy & Groc Polgasowita. ATM POS Transaction."
        val posPattern = Regex("""@\s+(.+?)\.\s""", RegexOption.IGNORE_CASE)
        posPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Description after the balance clause, up to "Thank you".
        // e.g. "...12,653.61.Transaction CEFT Inward Transfer Deposit.Thank you for..."
        //   or "...24,037.61.Internet-Mobile Withdrawal.Thank you for..."
        val descPattern = Regex(
            """AvlBal\s+LKR\s+[0-9,]+\.\d{2}\.\s*(.+?)\.\s*Thank you""",
            RegexOption.IGNORE_CASE
        )
        descPattern.find(message)?.let { match ->
            // Strip a leading "Transaction " marker for a cleaner description.
            val merchant = match.groupValues[1].trim().removePrefix("Transaction ").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "AvlBal LKR 12,653.61"
        val pattern = Regex("""AvlBal\s+LKR\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "A/c XXXXXXXX6146 on ..." — last 4 digits after the X-masked prefix.
        val pattern = Regex("""A/c\s+X+(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)
    }
}
