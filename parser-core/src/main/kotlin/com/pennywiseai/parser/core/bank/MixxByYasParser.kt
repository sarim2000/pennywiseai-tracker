package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Mixx by Yas (Tanzania) digital wallet / mobile money SMS.
 *
 * Mixx by Yas (sender "MixxByYas") is the rebranded Yas wallet. Its message
 * formats differ from the legacy Tigo Pesa templates, so this parser claims the
 * specific Mixx shapes while leaving the legacy Tigo Pesa / TIPS-transfer
 * formats to [TigoPesaParser] (both share the MIXXBYYAS sender; the factory's
 * content-aware dispatch picks whichever parses).
 *
 * Currency: TSh (Tanzanian Shilling, same as TZS).
 *
 * Twin-message dedup: each economic event arrives as a PRIMARY message (carrying
 * the real amount field) and a SECONDARY confirmation ack that shares the same
 * TxnID. The secondaries ("You have sent ... Please wait for confirmation" and
 * the content-less "your transaction is successfull" ack) are rejected so the
 * same TxnID is not booked twice.
 *
 * No PII: recipient phone numbers and agent personal names are never emitted as
 * the merchant — generic labels ("Mobile Money Transfer", "Agent Cash Out") are
 * used instead.
 *
 * Country: Tanzania
 */
class MixxByYasParser : BankParser() {

    override fun getBankName() = "Mixx by Yas"

    override fun getCurrency() = "TZS"  // TSh is the same as TZS (Tanzanian Shilling)

    override fun canHandle(sender: String): Boolean {
        // Normalize by stripping spaces, dashes and underscores so "MIXX BY YAS",
        // "MIXX-BY-YAS" and "MixxByYas" all collapse to MIXXBYYAS.
        val normalized = sender.uppercase().replace(Regex("""[\s\-_]"""), "")
        return normalized.contains("MIXXBYYAS")
    }

    // Amount in TSh form: "TSh 52,000", "TSh 0", "TSh 12,366.80".
    private val tshNumber = """TSh\s*([0-9][0-9,]*(?:\.[0-9]+)?)"""

    // PRIMARY amount anchors, in priority order. Each anchors on the field that
    // carries the real transaction amount for a given format.
    private val amountAnchors = listOf(
        // #1 Outbound P2P: "Money sent successfully ... Amount TSh 52,000"
        Regex("""Amount\s+$tshNumber""", RegexOption.IGNORE_CASE),
        // #3 Bill payment: "Txn Amt TSh 150,000 sent to ..."
        Regex("""Txn\s+Amt\s+$tshNumber""", RegexOption.IGNORE_CASE),
        // #6 GePG: "Amt TSh 150,000 sent to ..."
        Regex("""\bAmt\s+$tshNumber""", RegexOption.IGNORE_CASE),
        // #5 Agent cash-out: "Cash Out of TSh 30,000 from Agent"
        Regex("""Cash\s*Out\s+of\s+$tshNumber""", RegexOption.IGNORE_CASE),
        // #4 Bustisha repayment: "paid your Bustisha Balance by TSh 12,366.80"
        Regex("""Bustisha\s+Balance\s+by\s+$tshNumber""", RegexOption.IGNORE_CASE),
        // #2/#8 Inbound: "You have received TSh 15,000 from ..."
        Regex("""received\s+$tshNumber""", RegexOption.IGNORE_CASE),
        // #7 LUKU token: amount is on the "TOTAL <amt>" line (no TSh prefix)
        Regex("""TOTAL\s+([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    )

    override fun extractAmount(message: String): BigDecimal? {
        for (pattern in amountAnchors) {
            pattern.find(message)?.let { match ->
                return parseAmount(match.groupValues[1])
            }
        }
        return null
    }

    private fun parseAmount(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(",", ""))
    } catch (e: NumberFormatException) {
        null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            // Inbound: push transfer, interest reward
            lower.contains("you have received") -> TransactionType.INCOME
            // Outbound flows
            lower.contains("money sent successfully") -> TransactionType.EXPENSE
            lower.contains("txn amt") -> TransactionType.EXPENSE          // bill payment
            lower.contains("amt tsh") -> TransactionType.EXPENSE          // GePG
            lower.contains("cash out of") -> TransactionType.EXPENSE      // agent cash-out
            lower.contains("bustisha balance by") -> TransactionType.EXPENSE  // loan repayment
            // LUKU electricity token: keyed on the receipt markers, not just the
            // "Payment Successful" prefix.
            lower.contains("payment successful") ||
                lower.contains("kwh") ||
                lower.contains("ewura") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()

        // #1 Outbound P2P to a (masked) phone number — never emit the number.
        if (lower.contains("money sent successfully")) {
            return "Mobile Money Transfer"
        }

        // #5 Agent cash-out — never emit the agent's personal name.
        if (lower.contains("cash out of")) {
            return "Agent Cash Out"
        }

        // #4 Bustisha micro-loan repayment.
        if (lower.contains("bustisha")) {
            return "Bustisha"
        }

        // #7 LUKU electricity token. Keyed on the electricity-receipt markers
        // ("kwh" units or the "EWURA" regulator line) rather than a single
        // keyword, so a LUKU receipt that omits one still resolves.
        if (lower.contains("kwh") || lower.contains("ewura")) {
            return "LUKU"
        }

        // #8 Mixx interest reward.
        if (lower.contains("mixx interest")) {
            return "Mixx Interest"
        }

        // #2 Inbound push transfer: "received TSh X from CRDB; JOHN DOE ..."
        // The sending institution is the token before ';'. Never emit the person.
        Regex("""received\s+TSh\s*[0-9,]+(?:\.[0-9]+)?\s+from\s+([A-Za-z0-9 .&]+?)\s*;""",
            RegexOption.IGNORE_CASE).find(message)?.let { match ->
            val institution = match.groupValues[1].trim()
            if (institution.isNotEmpty()) return institution
        }

        // #3 Bill payment / #6 government payment: "sent to <Payee> (code)".
        Regex("""sent\s+to\s+([A-Za-z0-9 ]+?)\s*\(""",
            RegexOption.IGNORE_CASE).find(message)?.let { match ->
            val payee = match.groupValues[1].trim()
            if (payee.isNotEmpty()) return payee
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "New balance is TSh X" / "New Bal: TSh X" / "New Bal TSh X" / "New balance: TSh X"
        val balancePatterns = listOf(
            Regex("""New\s+balance\s+is\s+$tshNumber""", RegexOption.IGNORE_CASE),
            Regex("""New\s+Bal(?:ance)?:?\s+$tshNumber""", RegexOption.IGNORE_CASE)
        )
        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                return parseAmount(match.groupValues[1])
            }
        }
        return null
    }

    override fun extractReference(message: String): String? {
        // Prefer "TxnID:/TxnId: <digits>".
        Regex("""Txn\s*ID:?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { return it.groupValues[1] }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Must look like a Mixx wallet message.
        if (!lower.contains("tsh") && !lower.contains("payment successful")) {
            return false
        }

        // Reject OTP / verification noise that the broad "tsh" gate would
        // otherwise admit (this method accepts Mixx-specific shapes directly,
        // so the base-class promotional/OTP filter is never reached).
        if (lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("verification code") ||
            lower.contains("do not share")
        ) {
            return false
        }

        // ----- Reject the SECONDARY twin messages (dedup) -----

        // Twin of an outbound transfer: "You have sent TSh ... Please wait for confirmation".
        // (Sample #3, a PRIMARY, says "Wait for confirmation" but does NOT start with
        // "You have sent", so it is not caught here.)
        if (lower.contains("you have sent") && lower.contains("please wait for confirmation")) {
            return false
        }

        // Content-less success ack: "your transaction is successfull" with no amount field.
        if (lower.contains("your transaction is successf") && extractAmount(message) == null) {
            return false
        }

        // ----- Leave legacy Tigo Pesa / TIPS formats to TigoPesaParser -----
        if (lower.contains("from tips.")) {
            return false
        }

        return true
    }
}
