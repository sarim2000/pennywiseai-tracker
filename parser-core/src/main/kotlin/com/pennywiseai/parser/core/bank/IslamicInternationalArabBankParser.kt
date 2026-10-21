package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Islamic International Arab Bank (البنك العربي الإسلامي الدولي, Jordan).
 * Sender: "ARABIISLAMI".
 *
 * Messages are a header line followed by labelled fields:
 *   "حركة شراء <merchant> <city> <country>
 *    المبلغ: 3.500 JOD
 *    حساب: 4321*1234
 *    تاريخ: 01/01/2026 12:00:00
 *    الرصيد: 1,234.567 JOD"
 *
 * The header decides the type: purchase / CliQ / CliQ purchase / ATM withdrawal are
 * EXPENSE; purchase reversal / CliQ reversal / ATM deposit are INCOME. Every message
 * names the account (not a card), so isFromCard = false.
 *
 * The masked account "4321*1234" is laid out right-to-left: the group BEFORE the
 * star is the real last four (the unmasked form "1234…4321" confirms it).
 */
class IslamicInternationalArabBankParser : BankParser() {

    override fun getBankName() = "Islamic International Arab Bank"

    override fun getCurrency() = "JOD"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "") == "ARABIISLAMI"

    private val amountPattern = Regex("""المبلغ:\s*([\d,]+(?:\.\d+)?)\s*([A-Z]{3})""")
    private val accountPattern = Regex("""حساب:\s*(\d+)(?:\*\d+)?""")
    private val balancePattern = Regex("""الرصيد:\s*(-?[\d,]+(?:\.\d+)?)""")
    private val cliqRecipient = Regex("""إلى:?\s*(.+)""")

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val header = smsBody.lineSequence().first().trim()
        val amount = amountPattern.find(smsBody) ?: return null
        val recipient = cliqRecipient.find(smsBody)?.groupValues?.get(1)?.trim()

        val (type, merchant) = when {
            header.startsWith("عكس حركة شراء من ") ->
                TransactionType.INCOME to cleanLocation(header.removePrefix("عكس حركة شراء من "))
            header.startsWith("حركة شراء ") ->
                TransactionType.EXPENSE to cleanLocation(header.removePrefix("حركة شراء "))
            header.startsWith("عكس دفعة CliQ من ") ->
                TransactionType.INCOME to header.removePrefix("عكس دفعة CliQ من ").trim()
            header == "حركة كليك" && recipient != null -> TransactionType.EXPENSE to recipient
            header == "مشتريات CliQ" && recipient != null -> TransactionType.EXPENSE to recipient
            header.startsWith("سحب صراف") -> TransactionType.EXPENSE to "ATM Withdrawal"
            header.startsWith("إيداع صراف") -> TransactionType.INCOME to "ATM Deposit"
            else -> return null
        }

        return ParsedTransaction(
            amount = amount.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null,
            type = type,
            merchant = merchant,
            reference = null,
            accountLast4 = accountPattern.find(smsBody)?.groupValues?.get(1)?.takeLast(4),
            balance = balancePattern.find(smsBody)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull(),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            currency = amount.groupValues[2]
        )
    }

    // ponytail: the bank collapses the fixed-width card field's padding, so the
    // name/city boundary is lost. Strips the country code, a trailing phone number
    // and the common Jordanian cities; anything else keeps its city suffix.
    private fun cleanLocation(raw: String): String =
        raw.trim()
            .replace(Regex("""\s*[A-Z]{2}$"""), "")
            .replace(Regex("""\s+[\d*+\-]+$"""), "")
            .replace(Regex("""\s*(?:AMMAN|ZARQA|IRBID|AQABA|JUBEIHA)(?:\s+JORDAN)?$""", RegexOption.IGNORE_CASE), "")
            .trim()
}
