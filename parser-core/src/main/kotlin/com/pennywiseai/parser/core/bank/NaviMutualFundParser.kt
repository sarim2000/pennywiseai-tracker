package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Navi Mutual Fund parser for SIP / unit-allotment SMS.
 *
 * Handles senders containing "NAVAMC" (Navi Asset Management Company),
 * e.g. "AD-NAVAMC-S", "VK-NAVAMC-T".
 *
 * Sample SMS:
 *   "Unit Allotment Update:
 *    Your SIP purchase of Rs.499.98 in Navi Nifty Next 50 Index Fund DG has been
 *    processed at applicable NAV. The units will be alloted in 1-2 working days.
 *    For further queries, please visit the Navi app.
 *    Team Navi Mutual Fund"
 *
 * Notes:
 * - Account number is not present in AMC unit-allotment SMS; left null.
 * - Underlying bank-side SIP debit (NACH/mandate) is parsed by the user's bank
 *   parser and will book a separate transaction. Manual dedupe is expected for v1.
 * - Mandate / autopay creation messages are NOT covered here; those originate
 *   from the user's bank, not the AMC.
 */
class NaviMutualFundParser : BankParser() {

    override fun getBankName() = "Navi Mutual Fund"

    override fun canHandle(sender: String): Boolean {
        // Match DLT-style senders like "AD-NAVAMC-S", "VK-NAVAMC-T".
        // Keyed on "NAVAMC" specifically so we don't false-positive on
        // generic "NAVI" senders that may belong to Navi's banking arm.
        return sender.uppercase().contains("NAVAMC")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        // Gate strictly on unit-allotment + SIP purchase phrasing so we ignore
        // NAV updates, account statements, marketing, etc.
        return lower.contains("unit allotment") && lower.contains("sip purchase")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "purchase of Rs.499.98 in ..." — also tolerate optional space after Rs
        // and Indian-style comma grouping (e.g. "Rs. 1,49,999.98").
        val pattern = Regex(
            """purchase\s+of\s+Rs\.?\s*([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Capture the fund name between "in " and " has been processed".
        // Kept generic so Navi Largecap / Liquid / ELSS / etc. all flow through.
        val pattern = Regex(
            """\bin\s+(.+?)\s+has\s+been\s+processed""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val fund = match.groupValues[1].trim()
            if (fund.isNotEmpty()) return fund
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // Unit allotment from an AMC is always an investment outflow.
        return TransactionType.INVESTMENT
    }

    // AMC SMS doesn't carry a bank account number.
    override fun extractAccountLast4(message: String): String? = null

    // No running balance in AMC SMS.
    override fun extractBalance(message: String): BigDecimal? = null
}
