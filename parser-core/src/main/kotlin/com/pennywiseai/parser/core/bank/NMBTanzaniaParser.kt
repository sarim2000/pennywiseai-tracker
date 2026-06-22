package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for NMB Bank Plc (Tanzania) SMS messages.
 *
 * NMB Tanzania shares the "NMB" sender with NMB Bank (Nepal), so this parser is
 * registered immediately BEFORE the Nepal parser in [BankParserFactory] and gates
 * tightly on Tanzania-specific markers (Swahili words, TSh/TZS notation,
 * "Mshiko Fasta", "NMB Pesa Fasta", "NMB karibu yako"). The content-aware factory
 * dispatch (firstNotNullOfOrNull) tries Tanzania first; this parser returns null for
 * Nepal-format messages so they fall through to the Nepal parser.
 *
 * Currency: TZS (Tanzanian Shilling). Amount notation varies: "TZS", "TSH", "Tsh"
 * (with/without space), comma thousands, optional decimals, plus a doubled
 * "TZS TZS" form seen in loan-repayment SMS.
 *
 * Supported formats (bilingual Swahili/English):
 * - Loan repayment (Swahili): "Kiasi cha TZS TZS <amt> kimetolewa ... kurejesha Mshiko Fasta ..."
 * - P2P transfer out (Swahili): "Kiasi cha TSH<amt> kimetumwa kutoka ... kwenda <NAME> <phone>"
 * - Merchant payment (English): "You have paid TZS <amt> ... to <MERCHANT> <id> on <date>"
 * - Inbound voucher (Swahili): "Umepokea Tsh <amt> kupitia NMB Pesa Fasta ..."
 * - Loan disbursement (Swahili): "Umepokea kiasi cha TZS <amt> kupitia Mshiko Fasta ..."
 */
class NMBTanzaniaParser : BankParser() {

    override fun getBankName() = "NMB Bank Tanzania"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("NMB")
    }

    // Currency token allowing the doubled "TZS TZS" form: "TZS", "TSH", "Tsh",
    // "TShs" optionally followed by a second currency token, then the amount.
    // Kept in sync with the currency signal in isTransactionMessage (TZS|TShs?)
    // so a message that passes the gate can always have its amount extracted.
    private val amountRegex = Regex(
        """(?:TZS|TSHS?)\s*(?:(?:TZS|TSHS?)\s*)?([0-9][0-9,]*(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )

    override fun extractAmount(message: String): BigDecimal? {
        // Anchor the amount on the transaction phrase. We never anchor on
        // "Salio la mkopo" (loan balance) or "Gharama" (fee), so search forward
        // from the first transaction marker.
        val anchors = listOf(
            "kiasi cha",      // "Kiasi cha TZS [TZS] <amt>" (loan repayment / disbursement)
            "you have paid",  // "You have paid TZS <amt>"
            "umepokea",       // "Umepokea Tsh <amt>" / "Umepokea kiasi cha TZS <amt>"
            "kimetumwa"       // fallback; amount usually precedes this in Swahili sends
        )

        val lower = message.lowercase()
        for (anchor in anchors) {
            val idx = lower.indexOf(anchor)
            if (idx < 0) continue
            // For Swahili sends the amount precedes "kimetumwa", so for that anchor
            // scan the whole message; for the others scan from the anchor onward.
            val region = if (anchor == "kimetumwa") message else message.substring(idx)
            amountRegex.find(region)?.let { match ->
                parseAmount(match.groupValues[1])?.let { return it }
            }
        }

        // Generic fallback: first TZS/TSH amount in the message.
        amountRegex.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun parseAmount(raw: String): BigDecimal? {
        return try {
            BigDecimal(raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            // INCOME — first-person "received".
            lower.contains("umepokea") -> TransactionType.INCOME

            // EXPENSE — sent / paid / taken out.
            lower.contains("kimetumwa") -> TransactionType.EXPENSE        // sent
            lower.contains("you have paid") -> TransactionType.EXPENSE     // merchant payment
            lower.contains("kimetolewa") -> TransactionType.EXPENSE        // taken out (loan repayment)

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // P2P transfer out: "... kwenda <NAME> <phone>". A lookahead terminates the
        // name before a trailing phone/masked number (07XXXXXXXX), a date keyword,
        // a period, or end of message — so the phone can never leak into the name
        // even when the message ends without a period or "Tarehe".
        val kwendaPattern = Regex(
            """kwenda\s+(.+?)(?=\s+0?[0-9X]{4,}|\s+Tarehe\b|\.|$)""",
            RegexOption.IGNORE_CASE
        )
        kwendaPattern.find(message)?.let { match ->
            val name = match.groupValues[1].trim().trimEnd('.').trim()
            if (name.isNotEmpty() && name.any { it.isLetter() }) return name
        }

        // Merchant payment (English): "... to <MERCHANT> <id> on <date>".
        // Drop the trailing numeric merchant id and the "on <date>" tail.
        val toPattern = Regex(
            """\bto\s+(.+?)(?:\s+\d{4,})?\s+on\s+""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotEmpty() && name.any { it.isLetter() }) return name
        }

        // Loan flows (repayment / disbursement) -> "Mshiko Fasta".
        if (message.contains("Mshiko Fasta", ignoreCase = true)) {
            return "Mshiko Fasta"
        }

        // Inbound voucher / remittance: "kupitia <CHANNEL>" e.g. "NMB Pesa Fasta".
        val kupitiaPattern = Regex(
            """kupitia\s+(.+?)(?:\s+\d|\.|$)""",
            RegexOption.IGNORE_CASE
        )
        kupitiaPattern.find(message)?.let { match ->
            val channel = match.groupValues[1].trim().trimEnd('.').trim()
            if (channel.isNotEmpty() && channel.any { it.isLetter() }) return channel
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // "Kumb: <code>" (Swahili "reference").
        Regex("""Kumb:\s*([A-Z0-9_]+)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { return it.groupValues[1] }

        // Leading code at the very start: "201NDGL261360514." or "GWX_1780199859027."
        Regex("""^([A-Z0-9_]{6,})\.""")
            .find(message.trim())?.let { return it.groupValues[1] }

        return null
    }

    override fun extractBalance(message: String) = null

    override fun extractAccountLast4(message: String): String? {
        // "akaunti yako inayoishia na XXXXX" / "akaunti inayoishia na XXXX".
        // The samples mask the account tail, so we only capture digit tails.
        val pattern = Regex(
            """inayoishia na\s+([0-9X]{3,})""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Reject OTP / secret-code noise (never emit "namba ya siri").
        if (lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("verification code")
        ) {
            return false
        }

        // Tight Tanzania gating: require both a Tanzania signal AND a transaction verb.
        // This ensures Nepal-format NMB messages (English "fund transfer", NPR, etc.)
        // fall through to the Nepal parser.
        // The currency signal matches an actual TZS/TSH *amount* token (e.g. "TSH8,000",
        // "TZS 263"), not a bare "tsh" substring — so a Nepal message whose reference
        // happens to contain those letters cannot be mis-claimed (Nepal uses NPR).
        val hasTanzanianCurrency =
            Regex("""\b(?:TZS|TShs?)\s*[0-9]""", RegexOption.IGNORE_CASE).containsMatchIn(message)
        val tanzaniaSignals = listOf(
            "mshiko fasta",
            "nmb pesa",
            "nmb karibu yako",
            "kiasi cha",
            "umepokea",
            "kimetumwa",
            "kimetolewa",
            "kwenda"
        )
        if (!hasTanzanianCurrency && tanzaniaSignals.none { lower.contains(it) }) return false

        val transactionVerbs = listOf(
            "umepokea",       // received
            "kimetumwa",      // sent
            "kimetolewa",     // taken out
            "you have paid"   // paid (English)
        )
        return transactionVerbs.any { lower.contains(it) }
    }
}
