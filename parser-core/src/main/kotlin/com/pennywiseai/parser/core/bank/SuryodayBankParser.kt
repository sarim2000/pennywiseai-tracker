package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Suryoday Small Finance Bank (India, INR). Sender: "…-SSFBNK-T".
 *
 * Credit card spend (incl. the Stable Money co-brand):
 * "Rs 553.00 spent on Stable Money Suryoday SFB CC 1111 on 20-10-2026 at 23:38:50. Avlb Rs 41628.18
 *  Not you SMS BLOCK CC 1111 to … Convert to EMI? …"
 *
 * The SMS names no merchant ("at" is the time), and "Avlb" is the remaining card limit, not a balance.
 */
class SuryodayBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Suryoday Small Finance Bank"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("SSFBNK") || upper.contains("SURYODAY")
    }

    private fun isCardSpend(message: String): Boolean =
        CARD_SPEND.containsMatchIn(message)

    override fun extractTransactionType(message: String): TransactionType? =
        if (isCardSpend(message)) TransactionType.CREDIT else super.extractTransactionType(message)

    override fun extractAmount(message: String): BigDecimal? =
        AMOUNT.find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractAmount(message)

    override fun extractMerchant(message: String, sender: String): String? =
        if (isCardSpend(message)) null else super.extractMerchant(message, sender)

    override fun extractAccountLast4(message: String): String? =
        CARD_LAST4.find(message)?.groupValues?.get(1) ?: super.extractAccountLast4(message)

    override fun extractAvailableLimit(message: String): BigDecimal? =
        AVAILABLE.find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractAvailableLimit(message)

    override fun extractBalance(message: String): BigDecimal? =
        if (isCardSpend(message)) null else super.extractBalance(message)

    override fun detectIsCard(message: String): Boolean = isCardSpend(message) || super.detectIsCard(message)

    override fun extractReference(message: String): String? =
        if (isCardSpend(message)) null else super.extractReference(message)

    private companion object {
        val CARD_SPEND = Regex("""\bspent\s+on\s+.*?\bCC\s+\d{4}\b""", RegexOption.IGNORE_CASE)
        val AMOUNT = Regex("""^\s*(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)\s+spent\b""", RegexOption.IGNORE_CASE)
        val CARD_LAST4 = Regex("""\bCC\s+(\d{4})\b""")
        val AVAILABLE = Regex("""\bAvlb\.?\s+(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    }
}
