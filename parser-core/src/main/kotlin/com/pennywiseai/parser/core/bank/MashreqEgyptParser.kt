package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Mashreq Egypt (Mashreq NEO) SMS, English and Arabic. Sender: "Mashreq NEO".
 *
 * The UAE [MashreqBankParser] also claims this sender (it matches any header containing
 * "SHREQ"), so this parser is registered above it. It only accepts the shapes below and
 * returns null for anything else, so OTPs, promotions, statement and password notices,
 * transfer *requests* ("تم استلام طلب تحويل") and limit warnings are ignored.
 *
 * Supported formats:
 * - Card purchase (EXPENSE, card):
 *   "Your Card ending with <last4> has been used at <merchant> for EGP <amt>"
 * - InstaPay (IPN) transfer received (INCOME, account):
 *   "لقد استقبلت تحويل لحظي على <last4> بمبلغ <amt> جم عبر IPN من <name> يوم <date> الساعة <time> رقم المعاملة <ref> ..."
 * - InstaPay (IPN) transfer sent (EXPENSE, account):
 *   "قمت بتحويل لحظي من <last4> بمبلغ <amt> جم عبر IPN يوم <date> الساعة <time> رقم المعاملة <ref> ..."
 * - Account debit (EXPENSE): "تم خصم مبلغ EGP <amt> من حسابك # ******<digits>. ..."
 * - Account credit (INCOME): "تمت إضافة مبلغ EGP <amt> إلى حسابك # ******<digits>. ..."
 */
class MashreqEgyptParser : BankParser() {

    override fun getBankName() = "Mashreq Egypt"

    override fun getCurrency() = "EGP"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "").contains("MASHREQNEO")

    private data class Shape(
        val regex: Regex,
        val type: TransactionType,
        val isFromCard: Boolean = false,
        val label: String? = null
    )

    private val shapes = listOf(
        Shape(
            Regex("""Your\s+Card\s+ending\s+with\s+(?<acct>\d{4})\s+has\s+been\s+used\s+at\s+(?<m>.+?)\s+for\s+(?<cur>[A-Z]{3})\s*$AMT""", RegexOption.IGNORE_CASE),
            TransactionType.EXPENSE,
            isFromCard = true
        ),
        Shape(
            Regex("""استقبلت\s+تحويل\s+لحظي\s+على\s+(?<acct>\d+)\s+بمبلغ\s+$AMT\s*$EGP\s+عبر\s+IPN\s+من\s+(?<m>.+?)\s+يوم"""),
            TransactionType.INCOME
        ),
        Shape(
            Regex("""قمت\s+بتحويل\s+لحظي\s+من\s+(?<acct>\d+)\s+بمبلغ\s+$AMT\s*$EGP\s+عبر\s+IPN"""),
            TransactionType.EXPENSE,
            label = "InstaPay Transfer"
        ),
        Shape(
            Regex("""${WORD_START}تم\s+خصم\s+مبلغ\s+(?<cur>[A-Z]{3})\s*$AMT\s+من\s+حسابك\s*#?\s*(?<acct>[*Xx\d]+)"""),
            TransactionType.EXPENSE,
            label = "Account Debit"
        ),
        Shape(
            Regex("""${WORD_START}تمت?\s+[إا]ضافة\s+مبلغ\s+(?<cur>[A-Z]{3})\s*$AMT\s+[إا]لى\s+حسابك\s*#?\s*(?<acct>[*Xx\d]+)"""),
            TransactionType.INCOME,
            label = "Account Credit"
        ),
    )

    private val referencePattern = Regex("""رقم\s+المعاملة\s+([A-Za-z0-9]+)""")

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Arabic SMS often carry invisible direction marks around numbers and dates.
        val body = smsBody.replace(DIRECTION_MARKS, "")
        for (shape in shapes) {
            val m = shape.regex.find(body) ?: continue
            val amount = m.groups["amt"]!!.value.replace(",", "").toBigDecimalOrNull() ?: return null
            val digits = m.groups["acct"]!!.value.filter { it.isDigit() }
            val currency = m.groups["cur"]!!.value.uppercase().takeIf { it.matches(ISO_CODE) } ?: getCurrency()
            return ParsedTransaction(
                amount = amount,
                type = shape.type,
                merchant = shape.label ?: m.groups["m"]!!.value.trim(),
                reference = referencePattern.find(body)?.groupValues?.get(1),
                accountLast4 = digits.takeLast(4).ifEmpty { null },
                balance = null,
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                isFromCard = shape.isFromCard,
                currency = currency,
                // The card and accounts are kept in EGP: a USD card spend must not create
                // (or re-currency) the account as USD.
                accountCurrency = if (currency != getCurrency()) getCurrency() else null
            )
        }
        return null
    }

    private companion object {
        const val AMT = """(?<amt>[\d,]+(?:\.\d+)?)"""
        // "تم" must start a word: "لم يتم خصم" / "لم تتم إضافة" ("was not debited/credited")
        // contain "تم خصم" / "تم إضافة" inside "يتم" / "تتم" and must not match.
        const val WORD_START = """(?<![ء-ي])"""
        // Every shape captures the currency as "cur"; the Arabic tokens mean EGP.
        const val EGP = """(?<cur>جم|جنيه|EGP)"""
        val ISO_CODE = Regex("""[A-Z]{3}""")
        val DIRECTION_MARKS = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]")
    }
}
