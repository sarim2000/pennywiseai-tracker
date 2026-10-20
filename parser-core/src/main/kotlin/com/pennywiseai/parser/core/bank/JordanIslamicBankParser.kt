package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Jordan Islamic Bank (البنك الإسلامي الأردني) Arabic SMS. Sender: "JIB".
 *
 * Every message names the account as "حسابكم <number> - <suffix>" and quotes JOD as
 * "دينار اردني". Requiring that token keeps a non-JOD account from being imported
 * with the wrong currency.
 *
 * Supported formats (all are account movements, so isFromCard = false and every
 * message lands on the same account row):
 * - Card authorisation (EXPENSE):
 *   "تفويض حركة على حسابكم <acct> - 003 بقيمة <amt> دينار اردني من <40-char card field> الرصيد المتوفر <bal> دينار اردني"
 *   The card field is the fixed-width ISO 8583 merchant location: 25 name + 13 city + 2 country.
 * - CliQ out (EXPENSE): "حوالة كليك صادرة بمبلغ <amt> دينار اردني من حسابكم <acct> - 003 الى <name> الرصيد المتوفر ..."
 * - CliQ in (INCOME):  "حوالة كليك واردة بمبلغ <amt> دينار اردني لحسابكم <acct> - 003 من <name> الرصيد المتوفر ..."
 * - Electronic transfer in/out, electronic (bill) payment, ATM withdrawal, profit credit.
 *
 * Declined purchases ("رفض حركة مشتريات") and CliQ payment requests match none of
 * the shapes and are ignored.
 */
class JordanIslamicBankParser : BankParser() {

    override fun getBankName() = "Jordan Islamic Bank"

    override fun getCurrency() = "JOD"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "") == "JIB"

    private data class Shape(
        val regex: Regex,
        val type: TransactionType,
        val label: String? = null,
        val isCardField: Boolean = false
    )

    private val shapes = listOf(
        Shape(Regex("""تفويض\s+حركة\s+على\s+$ACCT\s+بقيمة\s+$AMT\s+$JOD\s+من\s+(?<m>.+?)\s+الرصيد"""), TransactionType.EXPENSE, isCardField = true),
        Shape(Regex("""حوالة\s+كليك\s+صادرة\s+بمبلغ\s+$AMT\s+$JOD\s+من\s+$ACCT\s+الى\s+(?<m>.+?)\s+الرصيد"""), TransactionType.EXPENSE),
        Shape(Regex("""حوالة\s+كليك\s+واردة\s+بمبلغ\s+$AMT\s+$JOD\s+ل$ACCT\s+من\s+(?<m>.+?)\s+الرصيد"""), TransactionType.INCOME),
        Shape(Regex("""تحويل\s+الكتروني\s+ل$ACCT\s+بقيمة\s+$AMT\s+$JOD"""), TransactionType.INCOME, "Electronic Transfer"),
        Shape(Regex("""تحويل\s+الكتروني\s+من\s+$ACCT\s+بقيمة\s+$AMT\s+$JOD"""), TransactionType.EXPENSE, "Electronic Transfer"),
        Shape(Regex("""تسديد\s+الكتروني\s+من\s+$ACCT\s+بقيمة\s+$AMT\s+$JOD"""), TransactionType.EXPENSE, "Electronic Payment"),
        Shape(Regex("""حركة\s+صراف\s+آلي\s+على\s+$ACCT\s+بقيمة\s+$AMT\s+$JOD"""), TransactionType.EXPENSE, "ATM Withdrawal"),
        Shape(Regex("""قيد\s+ارباح\s+بمبلغ\s+$AMT\s+$JOD\s+ل$ACCT"""), TransactionType.INCOME, "Profit Credit"),
    )

    private val balancePattern = Regex("""(?:الرصيد\s+المتوفر|ليصبح\s+رصيدكم)\s+(-?[\d,]+(?:\.\d+)?)""")

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        for (shape in shapes) {
            val m = shape.regex.find(smsBody) ?: continue
            return ParsedTransaction(
                amount = m.groups["amt"]!!.value.replace(",", "").toBigDecimalOrNull() ?: return null,
                type = shape.type,
                merchant = shape.label ?: m.groups["m"]!!.value.let { if (shape.isCardField) cardMerchant(it) else it.trim() },
                reference = null,
                accountLast4 = m.groups["acct"]!!.value.takeLast(4),
                balance = balancePattern.find(smsBody)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull(),
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                currency = getCurrency()
            )
        }
        return null
    }

    /** The fixed-width card field keeps only its 25-char name; a collapsed field is kept whole. */
    private fun cardMerchant(field: String): String =
        (if (field.length >= CARD_FIELD_LENGTH) field.take(CARD_NAME_LENGTH) else field).trim()

    private companion object {
        const val AMT = """(?<amt>[\d,]+(?:\.\d+)?)"""
        const val JOD = """دينار\s+[اأ]ردني"""
        const val ACCT = """حسابكم\s+(?<acct>\d+)\s*-\s*\d+"""
        const val CARD_FIELD_LENGTH = 40
        const val CARD_NAME_LENGTH = 25
    }
}
