package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Diamond Trust Bank (DTB) Tanzania.
 *
 * Sender ID: "DTB" (also DLT-style prefixes like AB-DTB-S).
 * Default currency: Tanzanian Shilling (TZS). Messages are mostly English.
 *
 * Supported formats:
 * - TIPS outgoing success:
 *   "Dear NAME, TIPS transaction of TZS <amt> from XXX to 07XXX has been
 *    successfully processed Ref <ref> Diamond Trust Bank"
 * - Generic account ALERT (the workhorse pattern):
 *   "ALERT: Your account no. XXX has been (debited|credited) with TZS <amt>
 *    for <PURPOSE> on <date>..."
 * - LUKU electricity token (multi-line); amount anchored on the "TOTAL TZS <amt>" line.
 *
 * Rejected (return null):
 * - TIPS request intake ("...transfer request for... is being processed") — would
 *   double-count against the success message.
 * - TANQR / GEPG confirmations that carry no amount.
 * - OTP / verification messages.
 */
class DiamondTrustBankParser : BankParser() {

    override fun getBankName() = "Diamond Trust Bank"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        // Plain "DTB" sender, or DLT-style "AB-DTB-S" where DTB is its own token.
        if (normalized == "DTB") return true
        return SENDER_PATTERN.containsMatchIn(normalized)
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val transaction = super.parse(smsBody, sender, timestamp) ?: return null
        return transaction.copy(isFromCard = detectCardForDtb(smsBody))
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Reject OTP / verification noise.
        if (lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("verification code")
        ) {
            return false
        }

        // Reject TIPS request intake — precedes the success SMS with the same Ref.
        if (lower.contains("is being processed") ||
            (lower.contains("transfer request for") && lower.contains("has been received"))
        ) {
            return false
        }

        // Accept the known transaction forms.
        val isTipsSuccess = lower.contains("has been successfully processed")
        val isAlert = ALERT_PATTERN.containsMatchIn(message)
        val isLuku = lower.contains("luku") && TOTAL_PATTERN.containsMatchIn(message)

        return isTipsSuccess || isAlert || isLuku
    }

    override fun extractAmount(message: String): BigDecimal? {
        // LUKU multi-line: anchor on the TOTAL line, never the COST/VAT/EWURA sub-amounts.
        if (message.contains("LUKU", ignoreCase = true)) {
            TOTAL_PATTERN.find(message)?.let { match ->
                return parseAmount(match.groupValues[1])
            }
        }

        // ALERT pattern: amount sits after "with TZS".
        ALERT_PATTERN.find(message)?.let { match ->
            return parseAmount(match.groupValues[2])
        }

        // Generic "TZS <amount>" (TIPS success).
        TZS_AMOUNT_PATTERN.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        ALERT_PATTERN.find(message)?.let { match ->
            return when (match.groupValues[1].lowercase()) {
                "debited" -> TransactionType.EXPENSE
                "credited" -> TransactionType.INCOME
                else -> null
            }
        }

        val lower = message.lowercase()
        // TIPS outgoing success and LUKU token purchase are both expenses.
        if (lower.contains("tips transaction of") ||
            lower.contains("luku")
        ) {
            return TransactionType.EXPENSE
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // LUKU electricity token.
        if (message.contains("LUKU", ignoreCase = true)) {
            return "LUKU"
        }

        // ALERT pattern: merchant is the <PURPOSE> text, title-cased.
        ALERT_PATTERN.find(message)?.let { match ->
            val purpose = match.groupValues[3].trim()
            if (purpose.isNotEmpty()) return toTitleCase(purpose)
        }

        // TIPS outgoing success — recipient is a masked phone (PII), so use a label.
        if (message.contains("TIPS transaction of", ignoreCase = true)) {
            return "TIPS Transfer"
        }

        return null
    }

    override fun extractReference(message: String): String? {
        REFERENCE_PATTERNS.forEach { pattern ->
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    /**
     * Card detection is purpose-driven for DTB: POS and ATM cash withdrawals are
     * card transactions, everything else (mobile/internet banking, transfers,
     * standing instructions, LUKU, TIPS) is account-based.
     */
    private fun detectCardForDtb(message: String): Boolean {
        val purpose = ALERT_PATTERN.find(message)?.groupValues?.get(3)?.uppercase() ?: return false
        return purpose.contains("POS") || purpose.contains("ATM")
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun toTitleCase(text: String): String {
        return text.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private companion object {
        // DLT-style sender where DTB is its own dash/dot-delimited token (e.g. AB-DTB-S).
        val SENDER_PATTERN = Regex("""(^|[-.])DTB($|[-.])""")

        // "has been debited with TZS 49900 for POS TRANSACTION on 22/06/2026"
        // Groups: 1=direction, 2=amount, 3=purpose.
        val ALERT_PATTERN = Regex(
            """has been (debited|credited) with TZS\s*([0-9][0-9,]*(?:\.\d+)?)\s+for\s+(.+?)\s+on\s+\d""",
            RegexOption.IGNORE_CASE
        )

        // "TOTAL TZS 20,000" (LUKU multi-line total line).
        val TOTAL_PATTERN = Regex(
            """TOTAL\s+TZS\s*([0-9][0-9,]*(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )

        // Generic "TZS <amount>" used for TIPS success.
        val TZS_AMOUNT_PATTERN = Regex(
            """TZS\s*([0-9][0-9,]*(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )

        val REFERENCE_PATTERNS = listOf(
            Regex("""Ref\s*No\.?\s*:?\s*([0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""Reference\s*:?\s*([0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""Ref\s*:?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
        )
    }
}
