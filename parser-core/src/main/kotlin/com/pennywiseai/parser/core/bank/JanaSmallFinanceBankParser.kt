package com.pennywiseai.parser.core.bank

/**
 * Parser for Jana Small Finance Bank (JANA SFB) SMS messages.
 *
 * Sender IDs look like JM-JANABK-S (DLT-prefixed) and the body is signed "JANA SFB".
 * The message shape is the standard Indian SFB UPI format, e.g.:
 *   "Dear Customer, Your acct XX005 is credited with INR 8.00 on 13-Jun-26 from
 *    NPCI BHIM. UPI Ref no 103475395201 . JANA SFB"
 * so amount (INR), account last-4 ("acct XX005"), reference ("UPI Ref no ...") and
 * the credited/debited type all resolve from [BaseIndianBankParser]; this parser only
 * needs to claim the sender.
 */
class JanaSmallFinanceBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Jana Small Finance Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("JANABK") ||
                normalizedSender.contains("JANASFB")
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // "...credited with INR 8.00 ... from NPCI BHIM. UPI Ref no ..." (payer) and
        // "...debited with INR ... to name@okaxis. UPI Ref no ..." (payee). The base
        // FROM/TO patterns require the name to butt up against " UPI", but here a
        // period separates them ("BHIM. UPI"), so the payer/payee is dropped. Capture
        // up to the sentence / "UPI" boundary instead, keeping the VPA handle before '@'.
        val keyword = if (message.contains("credited", ignoreCase = true)) "from" else "to"
        val pattern = Regex(
            """\b$keyword\s+(.+?)(?:\.\s|\s+UPI\b|$)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            var name = match.groupValues[1].trim()
            if (name.contains("@")) name = name.substringBefore("@")
            name = name.trimEnd('.', ',', ';')
            val merchant = cleanMerchantName(name)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }
        return super.extractMerchant(message, sender)
    }
}
