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
}
