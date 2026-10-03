package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Trading 212 — app notifications only.
 *
 * - Interest: "💸 You earned £0.24 interest on uninvested cash!"
 *
 * GBP only — the reported format. Trading 212 also offers EUR/USD accounts, but
 * those need their own account currency (getCurrency labels the account) and a
 * real sample; until then a non-£ notice is skipped rather than booked as pounds.
 * Reached through the "Trading212" alias of the com.avuscapital.trading212 app.
 */
class Trading212Parser : BankParser() {

    override fun getBankName() = "Trading 212"

    override fun getCurrency() = "GBP"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "") == "TRADING212"

    private val interestPattern = Regex(
        """earned\s+£\s*([0-9,]+(?:\.\d{1,2})?)\s+interest""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val m = interestPattern.find(smsBody) ?: return null
        val amount = m.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        return ParsedTransaction(
            amount = amount,
            type = TransactionType.INCOME,
            merchant = "Trading 212 Interest",
            reference = null,
            accountLast4 = null,
            balance = null,
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            currency = getCurrency()
        )
    }
}
