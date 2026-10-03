package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Chase UK (GBP) — app notifications only; Chase UK sends no transaction SMS.
 *
 * - Money in: "🎉 £0.01 just landed in Dean's Account from Jane Doe"
 *
 * Reached through the "ChaseUK" alias of the com.chase.intl app (see
 * BankNotificationConfig). Kept apart from [ChaseBankParser], which is the US
 * bank (USD, SMS shortcode 24273).
 */
class ChaseUKParser : BankParser() {

    override fun getBankName() = "Chase UK"

    override fun getCurrency() = "GBP"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "") == "CHASEUK"

    private val moneyInPattern = Regex(
        """£\s*([0-9,]+(?:\.\d{1,2})?)\s+just\s+landed\s+in\s+.+?\s+from\s+(.+?)\s*[.!]?\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val m = moneyInPattern.find(smsBody) ?: return null
        val amount = m.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        return ParsedTransaction(
            amount = amount,
            type = TransactionType.INCOME,
            merchant = m.groupValues[2].trim().ifEmpty { null },
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
