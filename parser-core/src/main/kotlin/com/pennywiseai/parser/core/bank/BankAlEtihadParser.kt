package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Bank al Etihad (بنك الاتحاد, Jordan) English SMS. Sender: "BankEtihad".
 *
 * Supported formats:
 * - Card purchase (EXPENSE, isFromCard = true):
 *   "0.802 JOD at ORACLE IRE.\nCard 123.\nAvailable balance: 0.308 JOD."
 * - Card refund (INCOME): "Refund of 0.100 JOD at CAREEM PLU.\nAvailable balance: 0.100 JOD."
 * - CliQ transfer (from = INCOME, to = EXPENSE):
 *   "1.202 JOD CliQ transfer to John Smith.\nAvailable balance: 0.000 JOD."
 *
 * Declined transactions, OTPs and prepaid-card top-up/withdraw notices (money moving
 * between the customer's own account and card) match none of these and are ignored.
 */
class BankAlEtihadParser : BankParser() {

    override fun getBankName() = "Bank al Etihad"

    override fun getCurrency() = "JOD"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "") == "BANKETIHAD"

    private val purchasePattern = Regex("""^$AMT\s+$CCY\s+at\s+(.+?)\.\s+Card\s+(\d+)""")
    // The name runs to the "." that ends its line, not the first "." — "Dr. Jane Doe", "ACME Co. Ltd".
    private val refundPattern = Regex("""^Refund\s+of\s+$AMT\s+$CCY\s+at\s+(.+?)\.$NAME_END""")
    private val cliqPattern = Regex("""^$AMT\s+$CCY\s+CliQ\s+transfer\s+(from|to)\s+(.+?)\.$NAME_END""")
    private val balancePattern = Regex("""Available\s+balance:\s*(-?[\d,]*\.?\d+)\s*$CCY""")

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val body = smsBody.trim()
        val purchase = purchasePattern.find(body)
        val card = purchase?.groupValues?.get(4)
        val (m, type, merchant) = purchase?.let { m ->
            Triple(m, TransactionType.EXPENSE, m.groupValues[3])
        } ?: refundPattern.find(body)?.let { m ->
            Triple(m, TransactionType.INCOME, m.groupValues[3])
        } ?: cliqPattern.find(body)?.let { m ->
            val type = if (m.groupValues[3] == "from") TransactionType.INCOME else TransactionType.EXPENSE
            Triple(m, type, m.groupValues[4])
        } ?: return null

        val currency = m.groupValues[2]
        val balance = balancePattern.find(body)
        return ParsedTransaction(
            amount = m.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null,
            type = type,
            merchant = merchant.trim(),
            reference = null,
            accountLast4 = card,
            balance = balance?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull(),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = card != null,
            // Refunds and CliQ transfers carry no card/account number: keep their balance on
            // one bank-level account rather than guessing a card.
            isMobileWallet = card == null,
            currency = currency,
            accountCurrency = balance?.groupValues?.get(2)?.takeIf { it != currency }
        )
    }

    private companion object {
        const val AMT = """([\d,]*\.?\d+)"""
        const val CCY = """([A-Z]{3})"""
        const val NAME_END = """(?=\s*\n|\s+Available\s+balance|\s*$)"""
    }
}
