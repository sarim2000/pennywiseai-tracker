package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Orange Money (Jordan) mobile wallet SMS. Sender: "OrangeMoney". Amounts are JOD.
 *
 * Supported formats:
 * - Purchase (EXPENSE): "تمت عملية شراء من محفظتك <wallet> بمبلغ <amt> دينار ... من <merchant> بالرقم المرجعي <ref> ..."
 * - Wallet transfer out (EXPENSE): "تمت عملية التحويل المالي الى المحفظة <alias> بمبلغ <amt> دينار ..."
 * - Transfer in, Arabic (INCOME): "تم استقبال حوالة مالية من <name> من مزود الخدمة: <bank> إلى محفظتك بمبلغ <amt> دينار ..."
 * - Transfer in, English (INCOME): "A money transfer transaction from <name> with Service Provider: <bank> has been credited
 *   to your wallet <wallet> with the amount <amt> JD ..."
 *
 * Only some shapes carry the wallet number, so every transaction lands on one bank-level
 * wallet account (isMobileWallet). OTPs and promos match none of the shapes.
 */
class OrangeMoneyJordanParser : BankParser() {

    override fun getBankName() = "Orange Money"

    override fun getCurrency() = "JOD"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "") == "ORANGEMONEY"

    private val shapes = listOf(
        Regex("""تمت\s+عملية\s+شراء\s+من\s+محفظتك.*?بمبلغ\s+$AMT\s+دينار.*?\s+من\s+(?<m>.+?)\s+بالرقم\s+المرجعي""") to TransactionType.EXPENSE,
        Regex("""تمت\s+عملية\s+التحويل\s+المالي\s+الى\s+المحفظة\s+(?<m>.+?)\s+بمبلغ\s+$AMT\s+دينار""") to TransactionType.EXPENSE,
        Regex("""تم\s+استقبال\s+حوالة\s+مالية\s+من\s+(?<m>.+?)\s+من\s+مزود\s+الخدمة.*?بمبلغ\s+$AMT\s+دينار""") to TransactionType.INCOME,
        Regex("""money\s+transfer\s+transaction\s+from\s+(?<m>.+?)\s+with\s+Service\s+Provider.*?has\s+been\s+credited.*?with\s+the\s+amount\s+$AMT\s+JD""", RegexOption.IGNORE_CASE) to TransactionType.INCOME,
    )

    private val referencePattern = Regex("""(?:بالرقم\s+المرجعي|reference\s+number)\s+([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val balancePattern = Regex("""(?:رصيد\s+المحفظة\s+المتاح\s+هو|Available\s+Wallet\s+Balance:)\s*(-?[\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        for ((regex, type) in shapes) {
            val m = regex.find(smsBody) ?: continue
            return ParsedTransaction(
                amount = m.groups["amt"]!!.value.replace(",", "").toBigDecimalOrNull() ?: return null,
                type = type,
                merchant = m.groups["m"]!!.value.trim(),
                reference = referencePattern.find(smsBody)?.groupValues?.get(1),
                accountLast4 = null,
                balance = balancePattern.find(smsBody)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull(),
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                currency = getCurrency(),
                isMobileWallet = true
            )
        }
        return null
    }

    private companion object {
        const val AMT = """(?<amt>[\d,]+(?:\.\d+)?)"""
    }
}
