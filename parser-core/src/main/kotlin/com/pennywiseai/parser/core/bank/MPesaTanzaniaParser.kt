package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for M-Pesa Tanzania (Vodacom) mobile money SMS messages
 *
 * Handles legacy "TZS" notation as well as the real-world "Tsh"/"Tshs" notation:
 * - "SGR1234567 Confirmed. You have received TZS 50,000.00 from JOHN DOE (255754XXXXXX)"
 * - "DFJ9B1FPQ8 Confirmed. Tsh5,000.00 sent to business VODACOM-BUNDLES 2 ..."
 * - "DFJ9B1FU69 Confirmed. Tsh4,000.00 has been deducted ... as a repayment of M-Pesa Overdraft ..."
 * - "DFJ9B1FX2B confirmed. You have received a payment of Tsh4,000.00 from 922756 - TIPS-SELCOM MF ..."
 * - "DFF9B1DPIJ Confirmed. ... Withdraw Tsh100,000.00 from 431836 - AGENT NAME OUTLET ..."
 *
 * Key patterns:
 * - Transaction ID: leading 10-char alphanumeric before "Confirmed."/"confirmed."/"imethibitishwa."
 * - Currency: TZS (Tanzanian Shilling), printed as TZS / Tsh / Tshs / TSh
 * - Balance: "New M-Pesa balance is Tsh X" / "Balance is Tsh X"
 *
 * Currency-amount notation always refers to the PRIMARY/first amount of the shape; later
 * "Total fee" / "M-Pesa fee" / "Government Levy" / "charged" / "Songesha limit" values are ignored.
 *
 * Note: This is distinct from Kenya M-Pesa which uses KES/Ksh. Dispatch is content-aware, so
 * Kenyan-format messages (Ksh) are rejected here and Tanzanian (Tsh/TZS) ones are accepted.
 * Country: Tanzania
 *
 * Twin-dedup decision:
 * The TIPS inbound credit arrives as an English + Swahili twin (same reference, e.g. DFJ9B1FX2B).
 * Both are parsed, but each is tagged with a reference-based transactionHash ("mpesa-tz:<ref>")
 * so the app deduplicates them when both arrive — while still recording the credit if only one
 * twin is delivered (the default body-based hash can't dedup them, as the bodies differ). The
 * thin outbound receipt-match ("<NAME> has received Tsh ... on <date>", no balance, duplicate
 * reference of the real transfer) is rejected outright, since it is a spurious INCOME echo.
 */
class MPesaTanzaniaParser : BankParser() {

    override fun getBankName() = "M-Pesa Tanzania"

    override fun getCurrency() = "TZS"

    // Currency token group: TZS / Tsh / Tshs / TSh, optional whitespace before the number.
    private val currencyToken = """(?:TZS|Tshs|Tsh)"""

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        // M-Pesa Tanzania uses same sender IDs as Kenya; differentiation is by content.
        return normalizedSender.contains("MPESA") ||
                normalizedSender.contains("M-PESA") ||
                normalizedSender == "MPESA" ||
                normalizedSender == "M-PESA" ||
                normalizedSender.contains("VODACOM")
    }

    /**
     * Override parse to gate on Tanzanian currency/context and to reject twin/duplicate shapes.
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Gate on Tanzanian-currency presence (Tsh/Tshs/TZS). This differentiates from Kenya
        // M-Pesa (Ksh/KES). Relaxed from the old hard "!contains(TZS)" reject so Tsh messages pass.
        if (!hasTanzanianCurrency(smsBody)) {
            return null
        }

        // Reject thin outbound receipt-match duplicate: "<NAME> has received Tsh ... on <date>".
        // This is a spurious echo of an outbound transfer (no balance, would mis-type as INCOME);
        // the real transfer is recorded from its own primary SMS.
        if (isThinReceiptDuplicate(smsBody)) {
            return null
        }

        val parsed = super.parse(smsBody, sender, timestamp) ?: return null

        // Dedup the TIPS inbound twin (Swahili "imethibitishwa. Umepokea ..." + English
        // "confirmed. You have received ...") by their shared leading reference code, instead
        // of rejecting one outright. Both notifications carry the same M-Pesa transaction id,
        // so a reference-based hash makes the app drop the duplicate when both arrive — while a
        // lone delivery (e.g. the English twin lost in transit) is still recorded, rather than
        // silently dropped. The default body-based hash can't dedup the twins (different bodies).
        val reference = parsed.reference
        return if (!reference.isNullOrBlank()) {
            parsed.copy(transactionHash = "mpesa-tz:$reference")
        } else {
            parsed
        }
    }

    private fun hasTanzanianCurrency(message: String): Boolean {
        return Regex(currencyToken, RegexOption.IGNORE_CASE).containsMatchIn(message)
    }

    private fun isThinReceiptDuplicate(message: String): Boolean {
        // e.g. "DFE9B1D5UM Confirmed. VALENTINA MAUMBA has received Tsh 40000 on 2026-06-14 19:20:55."
        // Marker: "has received" with NO balance line. The canonical transfer carries a balance.
        val lower = message.lowercase()
        return lower.contains("has received") && extractBalance(message) == null
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Shape-specific primary-amount patterns, in priority order. Each anchors the amount to the
        // transaction keyword so trailing fee/levy/charge amounts are never picked up.
        val amt = """([0-9,]+(?:\.[0-9]{1,2})?)"""
        val cur = currencyToken

        val patterns = listOf(
            // "Umepokea Tshs 4,000.00 kutoka" (Swahili inbound)
            Regex("""Umepokea\s+$cur\s*$amt""", RegexOption.IGNORE_CASE),
            // "received a payment of Tsh4,000.00 from" / "you have received Tsh X"
            Regex("""received(?:\s+a\s+payment\s+of)?\s+$cur\s*$amt""", RegexOption.IGNORE_CASE),
            // "Withdraw Tsh100,000.00 from"
            Regex("""Withdraw\s+$cur\s*$amt""", RegexOption.IGNORE_CASE),
            // "Tsh5,000.00 sent to" / "Tsh7,000.00 paid to"
            Regex("""$cur\s*$amt\s+(?:sent|paid)\s+to""", RegexOption.IGNORE_CASE),
            // "Tsh4,000.00 has been deducted ... repayment"
            Regex("""$cur\s*$amt\s+has\s+been\s+deducted""", RegexOption.IGNORE_CASE),
            // Fallback: first currency amount in the message (legacy TZS shapes).
            Regex("""$cur\s*$amt""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()

        return when {
            // Income: Swahili / English inbound credits.
            lower.contains("umepokea") -> TransactionType.INCOME
            lower.contains("you have received") -> TransactionType.INCOME
            lower.contains("received a payment") -> TransactionType.INCOME
            lower.contains("received tsh") -> TransactionType.INCOME
            lower.contains("received tzs") -> TransactionType.INCOME

            // Expense: outbound transfers, payments, withdrawals, loan repayment.
            lower.contains("sent to") -> TransactionType.EXPENSE
            lower.contains("paid to") -> TransactionType.EXPENSE
            lower.contains("withdraw") -> TransactionType.EXPENSE
            lower.contains("deducted") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Withdrawals: never emit the agent's personal/outlet name. Use a generic label.
        if (Regex("""Withdraw\s+$currencyToken""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return "Agent Withdrawal"
        }

        // Loan repayment: "deducted ... as a repayment of M-Pesa Overdraft service".
        Regex("""repayment of\s+(.+?)\s+service""", RegexOption.IGNORE_CASE).find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // "sent to business VODACOM-BUNDLES 2 on ..." (the "business" qualifier precedes the name).
        Regex("""sent to business\s+(.+?)(?:\s+on\s+\d|\s+for\s+account|\s+Total\s+fee|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanTzMerchant(match.groupValues[1])
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

        // "sent to M-KOBA for account ..." / "sent to TIPS-SELCOM MF for account ..."
        Regex("""sent to\s+(.+?)\s+for\s+account""", RegexOption.IGNORE_CASE).find(message)?.let { match ->
            val merchant = cleanTzMerchant(match.groupValues[1])
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // "sent to NAME (phone)" / "sent to NAME on ..."
        Regex("""sent to\s+(.+?)(?:\s*\(|\s+on\s+\d|\s+Total\s+fee|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanTzMerchant(match.groupValues[1])
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

        // "paid to LIPA KIBUGUMO PHARMACY on ..." / "paid to LUKU for account ..."
        Regex("""paid to\s+(.+?)(?:\s+for\s+account|\s+on\s+\d|\s*\(Merchant|\s+and\s+charged|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanTzMerchant(match.groupValues[1])
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

        // English inbound: "received a payment of Tsh X from 922756 - TIPS-SELCOM MF on ..."
        // Drop the leading numeric short-code, keep the named institution. Never emit a bare number.
        Regex("""received\s+a\s+payment\s+of\s+$currencyToken\s*[0-9,]+(?:\.[0-9]{1,2})?\s+from\s+(.+?)(?:\s+on\s+\d|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                var merchant = match.groupValues[1].trim()
                merchant = merchant.replace(Regex("""^\d+\s*-\s*"""), "").trim()
                merchant = cleanTzMerchant(merchant)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

        // Swahili inbound: "Umepokea Tshs X kutoka SELCOM MF, Akaunti ****1234 - JOHN DOE ..."
        // Use the sending institution after "kutoka" (before the comma / Akaunti); never the person.
        Regex("""kutoka\s+(.+?)(?:\s*,|\s+Akaunti|\s+tarehe|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanTzMerchant(match.groupValues[1])
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

        // Generic English inbound: "received TZS X from NAME (phone)"
        Regex("""received\s+$currencyToken\s*[0-9,]+(?:\.[0-9]{1,2})?\s+from\s+(.+?)(?:\s*\(|\s+on\s+\d|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanTzMerchant(match.groupValues[1])
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "New M-Pesa balance is Tsh X" or "Balance is Tsh X"
        val balancePattern = Regex(
            """(?:New M-Pesa balance is|Balance is)\s+$currencyToken\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Leading transaction code before "Confirmed."/"confirmed."/"imethibitishwa."
        val txnIdPattern = Regex(
            """^\s*([A-Z0-9]{8,12})\s+(?:Confirmed|confirmed|imethibitishwa)""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // TIPS Reference for inter-operator transfers.
        val tipsPattern = Regex(
            """TIPS\s+Reference[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        tipsPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // M-Pesa Tanzania messages confirm with "Confirmed"/"confirmed" or Swahili "imethibitishwa".
        if (!lower.contains("confirmed") && !lower.contains("imethibitishwa")) {
            return false
        }

        if (!hasTanzanianCurrency(message)) {
            return false
        }

        val transactionKeywords = listOf(
            "umepokea",
            "received",
            "sent to",
            "paid to",
            "withdraw",
            "deducted",
            "new m-pesa balance",
            "balance is"
        )

        return transactionKeywords.any { lower.contains(it) }
    }

    /**
     * Trims trailing date/time/fee/account noise off a captured merchant fragment.
     */
    private fun cleanTzMerchant(raw: String): String {
        return raw
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")           // trailing parentheses
            .replace(Regex("""\s+on\s+\d.*""", RegexOption.IGNORE_CASE), "")   // date suffix
            .replace(Regex("""\s+tarehe\s+\d.*""", RegexOption.IGNORE_CASE), "") // Swahili date suffix
            .replace(Regex("""\s+for\s+account.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Total\s+fee.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*,.*$"""), "")                  // trailing comma clause
            .replace(Regex("""\.\s*$"""), "")                   // trailing period
            .replace(Regex("""\s*-\s*$"""), "")                 // trailing dash
            .trim()
    }
}
