package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Standard Chartered Bank Bangladesh, sender StanChartBD.
 *
 * Card purchases/finance charges use CREDIT (PennyWise's credit-card spending
 * type), and available credit is creditLimit, never a deposit-account balance.
 * Card bill repayments are TRANSFER, never income. eTAC, statements, and NPS
 * confirmations without source-account identification are intentionally ignored.
 * All sample identifiers in tests are synthetic.
 */
class StandardCharteredBangladeshParser : BankParser() {
    companion object {
        private const val RAW = "[0-9][0-9,.]*"
        private const val DATE = "[0-9]{1,2}-[A-Za-z]{3}-[0-9]{2}"
        private val VALID_MONEY = Regex("""(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?:,[0-9]{2})+,[0-9]{3}|[0-9]+)(?:\.[0-9]{1,2})?""")
        private val CARD_PURCHASE = Regex(
            """^(?:Intl\.\s*e-trxn\s+of\s+)?BDT\s+($RAW)\s+(?:transaction\s+was\s+made|was\s+made)\s+on\s+card\s+ending\s+([0-9]{4})\s+at\s+(.+?)\s+on\s+$DATE\.\s+Available\s+credit:\s*BDT\s+($RAW)\.\s+Enquiries:\s*[0-9]+\.?\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val CARD_PAYMENT = Regex(
            """^Dear\s+Client,\s*BDT\s+($RAW)\s+has\s+been\s+credited\s+to\s+card\s+ending\s+([0-9]{4})\s+on\s+$DATE\s+pertaining\s+to\s+PAYMENT\s+RECD\.-CASH\.\s+Available\s+Credit:\s*BDT\s+($RAW)\.\s+Enquiries:\s*[0-9]+\.?\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val FINANCE_CHARGE = Regex(
            """^Dear\s+Client,\s*BDT\s+($RAW)\s+has\s+been\s+debited\s+from\s+card\s+ending\s+([0-9]{4})\s+on\s+$DATE\s+pertaining\s+to\s+FINANCE\s+CHARGES\.{1,2}\s+Available\s+Credit:\s*BDT\s+($RAW)\.\s+Enquiries:\s*[0-9]+\.?\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val ACCOUNT_MOVEMENT = Regex(
            """^Dear\s+Client,\s*BDT\s+($RAW)\s+was\s+(deposited\s+to|debited\s+from)\s+Acc\.\s+ending\s+[xX*]*([0-9]{4})\s+on\s+$DATE\.\s+Available\s+balance\s+is\s+now\s+BDT\s+($RAW)\.\s+For\s+enquiries,\s+please\s+call\s+[0-9]+\.?\s*$""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun getBankName(): String = "Standard Chartered Bank (Bangladesh)"
    override fun getCurrency(): String = "BDT"
    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "") == "STANCHARTBD"

    private fun money(raw: String): BigDecimal? =
        if (VALID_MONEY.matches(raw)) raw.replace(",", "").toBigDecimalOrNull() else null

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (!canHandle(sender) || isNonTransactionMessage(smsBody)) return null
        val body = smsBody.trim()
        val lower = body.lowercase()
        if (lower.contains("etac") || lower.contains("statement of your credit card") ||
            lower.contains("nps transfer request") || lower.contains("online banking initiated")) return null

        fun build(amount: BigDecimal?, type: TransactionType, suffix: String,
            merchant: String?, availableCredit: BigDecimal? = null, balance: BigDecimal? = null,
            card: Boolean = false): ParsedTransaction? {
            if (amount == null || amount <= BigDecimal.ZERO) return null
            return ParsedTransaction(
                amount = amount, type = type, merchant = merchant, reference = null,
                accountLast4 = suffix, balance = balance,
                creditLimit = availableCredit,
                smsBody = smsBody, sender = sender, timestamp = timestamp,
                bankName = getBankName(), isFromCard = card, currency = getCurrency()
            )
        }

        CARD_PURCHASE.matchEntire(body)?.let { m ->
            val merchant = m.groupValues[3].trim().ifEmpty { return null }
            return build(money(m.groupValues[1]), TransactionType.CREDIT, m.groupValues[2],
                merchant = merchant, availableCredit = money(m.groupValues[4]), card = true)
        }
        CARD_PAYMENT.matchEntire(body)?.let { m ->
            return build(money(m.groupValues[1]), TransactionType.TRANSFER, m.groupValues[2],
                merchant = "Credit Card Payment", availableCredit = money(m.groupValues[3]), card = true)
        }
        FINANCE_CHARGE.matchEntire(body)?.let { m ->
            return build(money(m.groupValues[1]), TransactionType.CREDIT, m.groupValues[2],
                merchant = "Finance Charges", availableCredit = money(m.groupValues[3]), card = true)
        }
        ACCOUNT_MOVEMENT.matchEntire(body)?.let { m ->
            val incoming = m.groupValues[2].startsWith("deposited", ignoreCase = true)
            return build(money(m.groupValues[1]),
                if (incoming) TransactionType.INCOME else TransactionType.EXPENSE,
                m.groupValues[3], merchant = null, balance = money(m.groupValues[4]))
        }
        return null
    }
}
