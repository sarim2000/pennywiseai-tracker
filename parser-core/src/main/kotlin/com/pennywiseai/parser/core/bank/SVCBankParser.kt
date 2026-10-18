package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * SVC Co-operative Bank (Shamrao Vitthal, India, INR) — #928.
 *
 * UPI debit:
 * "SVC Bank A/c *1111 DEBITED for Rs.30.00 Clr Bal Rs.696.96. CR UPI/DR/600000000001/TEST PAYEE/.
 *  Not You? SMS STOPUPI to …"
 */
class SVCBankParser : BaseIndianBankParser() {

    override fun getBankName() = "SVC Co-operative Bank"

    // "SVC" or a DLT header like "VM-SVCBNK-S"; segment match so unrelated headers containing "SVC" don't match.
    override fun canHandle(sender: String): Boolean =
        sender.uppercase().split(Regex("""[\s_-]+""")).any { it == "SVC" || it.startsWith("SVCB") }

    override fun extractAmount(message: String): BigDecimal? =
        Regex("""(?:DEBITED|CREDITED)\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractAmount(message)

    // "Clr Bal Rs.696.96"
    override fun extractBalance(message: String): BigDecimal? =
        Regex("""Clr\s+Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractBalance(message)

    // "UPI/DR/600000000001/TEST PAYEE/"
    private val upiInfo = Regex("""UPI/(?:DR|CR)/(\d+)/([^/]+)/""", RegexOption.IGNORE_CASE)

    override fun extractMerchant(message: String, sender: String): String? {
        upiInfo.find(message)?.let { m ->
            val merchant = cleanMerchantName(m.groupValues[2].trim())
            if (isValidMerchantName(merchant)) return merchant
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? =
        upiInfo.find(message)?.groupValues?.get(1) ?: super.extractReference(message)
}
