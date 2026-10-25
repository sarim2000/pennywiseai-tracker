package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Dubai First (UAE credit cards, a First Abu Dhabi Bank brand) — app notifications
 * from com.bankfab.pbg.ae.dubaifirst, reached through the "DubaiFirst" alias (see
 * BankNotificationConfig):
 *
 * "Card Transaction\nCard ending with: 1111\nAmount: AED 280\nAt: TEST STORE\nDate: 25/10/2026\n
 *  Available Balance: AED 10247.04"
 *
 * It's a credit card, so "Available Balance" is the remaining limit. Fields are matched by
 * label, not by line, because a collapsed notification can arrive on one line.
 */
class DubaiFirstParser : UAEBankParser() {

    override fun getBankName() = "Dubai First"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "") == "DUBAIFIRST"

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Gate on the labelled fields, not the "Card Transaction" heading: the heading may sit in
        // the notification title, which isn't always part of the text we receive.
        val card = CARD.find(smsBody) ?: return null
        val amount = AMOUNT.find(smsBody) ?: return null
        val value = amount.groupValues[2].replace(",", "").toBigDecimalOrNull() ?: return null
        val currency = amount.groupValues[1].uppercase()
        val available = AVAILABLE.find(smsBody)
        // Refund wording only counts in the heading (before the card line), never in the merchant.
        val refund = REFUND.containsMatchIn(smsBody.substring(0, card.range.first))
        return ParsedTransaction(
            amount = value,
            type = if (refund) TransactionType.INCOME else TransactionType.CREDIT,
            merchant = MERCHANT.find(smsBody)?.groupValues?.get(1)?.trim()?.ifEmpty { null },
            reference = null,
            accountLast4 = card.groupValues[1],
            balance = null,
            creditLimit = available?.groupValues?.get(2)?.replace(",", "")?.toBigDecimalOrNull(),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = true,
            currency = currency,
            // A foreign-currency spend still reports the limit in AED.
            accountCurrency = available?.groupValues?.get(1)?.uppercase()?.takeIf { it != currency }
        )
    }

    private companion object {
        const val NUM = """([0-9,]+(?:\.\d{1,2})?)"""
        val AMOUNT = Regex("""\bAmount:\s*([A-Z]{3})\s*$NUM""", RegexOption.IGNORE_CASE)
        val CARD = Regex("""Card\s+ending\s+with:\s*(\d{4})""", RegexOption.IGNORE_CASE)
        val MERCHANT = Regex("""\bAt:\s*(.+?)\s*(?=\n|\bDate:|\bAvailable\s+Balance:|$)""", RegexOption.IGNORE_CASE)
        val AVAILABLE = Regex("""Available\s+Balance:\s*([A-Z]{3})\s*$NUM""", RegexOption.IGNORE_CASE)
        val REFUND = Regex("""\b(?:refund|reversal|reversed)\b""", RegexOption.IGNORE_CASE)
    }
}
