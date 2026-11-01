package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * City Bank PLC, Bangladesh (BDT). Verified against anonymized samples of
 * E-COMM/POS purchases and CITYTOUCH/ATM withdrawals.
 *
 * SMS layout (sender: CITY BANK):
 *   02-Oct-2026
 *   E-COMM/POS TXN
 *   Tk. 685 Purchased
 *   Tk. 96,080 Balance
 *   A/C: 7777***1234
 *   Please rate us: https://example.invalid/rate
 *
 * Only a completed transaction on line three is counted. The separate balance
 * line is the post-transaction account balance, not a second transaction.
 * Masked/non-numeric balances are deliberately returned as null.
 *
 * Timestamp is sourced from SMS metadata by BankParser, not from the date line.
 * Unknown transaction formats and OTPs are excluded rather than guessed.
 */
class CityBankBangladeshParser : BankParser() {
    companion object {
        private const val NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?:,[0-9]{2})+,[0-9]{3}|[0-9]+)(?:\\.[0-9]{1,2})?"
        private val dateLine = Regex("""\d{2}-[A-Za-z]{3}-\d{4}""")
        private val headingLine = Regex("""(E-COMM/POS|CITYTOUCH|ATM)\s+TXN""", RegexOption.IGNORE_CASE)
        private val actionLine = Regex("""Tk\.?\s+($NUMBER)\s+(Purchased|Withdrawal)""", RegexOption.IGNORE_CASE)
        private val anyBalanceLine = Regex("""Tk\.?\s+\S.*\s+Balance""", RegexOption.IGNORE_CASE)
        private val numericBalanceLine = Regex("""Tk\.?\s+($NUMBER)\s+Balance""", RegexOption.IGNORE_CASE)
        // Both A/C: ***1234 and A/C: 7777***1234 are observed structures.
        private val accountLine = Regex("""A/C\s*:\s*[0-9]*\*+([0-9]{4})""", RegexOption.IGNORE_CASE)
    }

    private data class Fields(
        val amount: BigDecimal,
        val accountLast4: String,
        val balance: BigDecimal?
    )

    override fun getBankName(): String = "City Bank (Bangladesh)"
    override fun getCurrency(): String = "BDT"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "") == "CITYBANK"

    private fun fields(message: String): Fields? {
        if (isNonTransactionMessage(message)) return null
        val lines = message.trim().lines().map { it.trim() }
        if (lines.size < 5 || !dateLine.matches(lines[0])) return null

        val channel = headingLine.matchEntire(lines[1])?.groupValues?.get(1)?.uppercase()
            ?: return null
        val action = actionLine.matchEntire(lines[2]) ?: return null
        val verb = action.groupValues[2].uppercase()

        // Do not count unexpected channel/verb combinations as valid transactions.
        val valid = when (channel) {
            "E-COMM/POS" -> verb == "PURCHASED"
            "CITYTOUCH", "ATM" -> verb == "WITHDRAWAL"
            else -> false
        }
        if (!valid || !anyBalanceLine.matches(lines[3])) return null

        val account = accountLine.matchEntire(lines[4])?.groupValues?.get(1) ?: return null
        val amount = action.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null

        val balance = numericBalanceLine.matchEntire(lines[3])
            ?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()

        return Fields(amount, account, balance)
    }

    override fun isTransactionMessage(message: String): Boolean = fields(message) != null
    override fun extractAmount(message: String): BigDecimal? = fields(message)?.amount
    override fun extractTransactionType(message: String): TransactionType? =
        if (fields(message) != null) TransactionType.EXPENSE else null
    override fun extractAccountLast4(message: String): String? = fields(message)?.accountLast4
    override fun extractBalance(message: String): BigDecimal? = fields(message)?.balance

    // Feedback URLs and generic channel labels are not payment references/merchants.
    override fun extractReference(message: String): String? = null
    override fun extractMerchant(message: String, sender: String): String? = null
}
