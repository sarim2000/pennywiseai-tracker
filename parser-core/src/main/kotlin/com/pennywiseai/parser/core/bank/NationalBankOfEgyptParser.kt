package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for National Bank of Egypt (NBE / البنك الأهلي المصري) Arabic SMS.
 *
 * Both "جم" and "EGP" are the same currency token, and the bank ships the
 * messages with inconsistent spacing (tokens are often glued to the value), so
 * every separator below is optional whitespace.
 *
 * Supported formats:
 * - Card spend (EXPENSE, isFromCard = true):
 *   "تم خصم <amount> جم من بطاقة الائتمان رقم <last4> عند <merchant> يوم <date>
 *    الساعة <time> المتاح <available> جم"
 *   - بطاقة الائتمان (credit card)      -> "المتاح" is the available limit  -> creditLimit
 *   - بطاقة الخصم المباشر (debit card)  -> "المتاح" is the account balance  -> balance
 * - Instant transfer in (INCOME, not a card):
 *   "تم إضافة تحويل لحظي لحسابكم رقم <last4> بمبلغ <amount> جم من <name>
 *    رقم مرجعي <reference> يوم <date> الساعة <time>"
 *
 * Sender: "BanK-AlAhly" (and DLT-style variants containing ALAHLY).
 */
class NationalBankOfEgyptParser : BankParser() {

    override fun getBankName() = "National Bank of Egypt"

    override fun getCurrency() = "EGP"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "").contains("ALAHLY")

    private val cardPattern = Regex(
        """تم\s*خصم\s*([\d,]+(?:\.\d+)?)\s*(?:جم|EGP)\s*من\s*بطاقة\s*(الائتمان|الخصم\s*المباشر)\s*""" +
            """رقم\s*(\d+)\s*عند\s*(.+?)\s*يوم.*?المتاح\s*([\d,]+(?:\.\d+)?)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val transferPattern = Regex(
        """تم\s*[إا]ضافة\s*تحويل\s*لحظي\s*لحسابكم\s*رقم\s*(\d+)\s*بمبلغ\s*([\d,]+(?:\.\d+)?)\s*""" +
            """(?:جم|EGP)\s*من\s*(.+?)\s*رقم\s*مرجعي\s*(\d+)""",
        RegexOption.DOT_MATCHES_ALL
    )

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        cardPattern.find(smsBody)?.let { m ->
            val isCreditCard = m.groupValues[2].startsWith("الائتمان")
            val available = amount(m.groupValues[5])
            return ParsedTransaction(
                amount = amount(m.groupValues[1]) ?: return null,
                type = TransactionType.EXPENSE,
                merchant = m.groupValues[4].trim(),
                reference = null,
                accountLast4 = m.groupValues[3].takeLast(4),
                balance = if (isCreditCard) null else available,
                creditLimit = if (isCreditCard) available else null,
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                isFromCard = true,
                currency = getCurrency()
            )
        }

        transferPattern.find(smsBody)?.let { m ->
            return ParsedTransaction(
                amount = amount(m.groupValues[2]) ?: return null,
                type = TransactionType.INCOME,
                merchant = m.groupValues[3].trim(),
                reference = m.groupValues[4],
                accountLast4 = m.groupValues[1].takeLast(4),
                balance = null,
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                currency = getCurrency()
            )
        }

        return null
    }

    private fun amount(raw: String): BigDecimal? = raw.replace(",", "").toBigDecimalOrNull()
}
