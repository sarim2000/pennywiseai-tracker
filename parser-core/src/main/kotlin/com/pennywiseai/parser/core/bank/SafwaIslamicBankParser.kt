package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType

/**
 * Parser for Safwa Islamic Bank (بنك صفوة الإسلامي, Jordan) Arabic SMS. Sender: "Safwa Bank".
 *
 * Shape: "<description> بمبلغ <amt> دينار الرصيد المتوفر <bal> دينار". The SMS carries no
 * account number. Only descriptions with a known direction are imported:
 * - "دفعة نقدية(فرع آلي)" — cash deposit at a self-service branch (INCOME)
 * - "تغطية عمولات البنك" — bank fees (EXPENSE)
 */
class SafwaIslamicBankParser : BankParser() {

    override fun getBankName() = "Safwa Islamic Bank"

    override fun getCurrency() = "JOD"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s\-_]"""), "") == "SAFWABANK"

    private val pattern = Regex(
        """^(.+?)\s*بمبلغ\s+([\d,]+(?:\.\d+)?)\s+دينار(?:\s+[اأ]ردني)?\s+الرصيد\s+المتوفر\s+(-?[\d,]+(?:\.\d+)?)"""
    )

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val m = pattern.find(smsBody.trim()) ?: return null
        val description = m.groupValues[1]
        val (type, merchant) = when {
            description.contains("دفعة نقدية") -> TransactionType.INCOME to "Cash Deposit"
            description.contains("عمولات") -> TransactionType.EXPENSE to "Bank Fees"
            else -> return null
        }
        return ParsedTransaction(
            amount = m.groupValues[2].replace(",", "").toBigDecimalOrNull() ?: return null,
            type = type,
            merchant = merchant,
            reference = null,
            accountLast4 = null,
            balance = m.groupValues[3].replace(",", "").toBigDecimalOrNull(),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            currency = getCurrency(),
            // No account number: track the running balance as one bank-level account
            // (the app's numberless-account path), or the balance would never be saved.
            isMobileWallet = true
        )
    }
}
