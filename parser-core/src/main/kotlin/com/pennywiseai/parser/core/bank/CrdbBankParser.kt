package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for CRDB Bank (Tanzania).
 *
 * CRDB sends bilingual Swahili/English SMS across several transaction types.
 * Default currency is Tanzanian Shilling (TZS), but some card payments are
 * billed in a foreign currency (e.g. USD) while the balance stays in TZS.
 * For those, the transaction amount/currency reflect the foreign spend and
 * the balance reflects the TZS figure (see [parse]).
 *
 * Supported formats:
 * - ATM withdrawal (English): "... has been withdrawn using a Card ... Balance is TZS ..."
 * - Card payment / POS / online (English): "Paid:MERCHANT ... CCY 9.99 Card:... Bal:TZS..."
 * - Mobile money send (Swahili): "Muamala umefanikiwa TZS40000 AIRTEL kwenda NAME phone"
 * - Bill payment / utility (Swahili): "Malipo yamekamilika MERCHANT TZS 2000"
 */
class CrdbBankParser : BankParser() {

    override fun getBankName() = "CRDB Bank"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("CRDB")
    }

    /**
     * Override parse to surface per-transaction foreign currency.
     *
     * The base [extractAmount] already picks the spent amount (e.g. USD 9.99 for the
     * Netflix card-payment form). Here we detect when that amount is billed in a
     * non-TZS currency and copy it onto the transaction, while [extractBalance]
     * keeps reporting the TZS balance figure.
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val transaction = super.parse(smsBody, sender, timestamp) ?: return null
        val currency = extractCurrency(smsBody)
        return if (currency != null && currency != getCurrency()) {
            transaction.copy(currency = currency)
        } else {
            transaction
        }
    }

    /**
     * Detects the currency of the SPENT amount (not the balance).
     *
     * Card-payment form: "Paid:NETFLIX.COM, NL USD 9.99 Card:... Bal:TZS923041.06"
     * The currency we want is the one immediately preceding the amount after "Paid:".
     */
    override fun extractCurrency(message: String): String? {
        // Currency + amount right before "Card:" in the card-payment form.
        val paidCurrency = Regex(
            """Paid:.*?\b([A-Z]{3})\s*[0-9][0-9,]*(?:\.\d{1,2})?\s*Card:""",
            RegexOption.IGNORE_CASE
        )
        paidCurrency.find(message)?.let { match ->
            val code = match.groupValues[1].uppercase()
            if (code.all { it.isLetter() }) return code
        }
        return null
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Card-payment form: amount sits between the merchant and "Card:".
        // e.g. "Paid:NETFLIX.COM, NL USD 9.99 Card:..." -> 9.99
        val paidPattern = Regex(
            """Paid:.*?\b[A-Z]{3}\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*Card:""",
            RegexOption.IGNORE_CASE
        )
        paidPattern.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Utility/LUKU token form: anchor on the "TOTAL TZS <amt>" line so the charged
        // total wins over the intermediate Cost/VAT/EWURA/REA/Debt Collected figures.
        val totalPattern = Regex(
            """TOTAL\s+TZS\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        totalPattern.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // "TZS 50000.00" (with space) or "TZS40000" (no space).
        val tzsPattern = Regex(
            """TZS\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        // Use the FIRST TZS amount that is not the balance.
        for (match in tzsPattern.findAll(message)) {
            // Skip the balance figure ("Balance is TZS ..." / "Bal:TZS...").
            val precedingText = message.substring(0, match.range.first).takeLast(20).lowercase()
            if (precedingText.contains("bal")) continue
            parseAmount(match.groupValues[1])?.let { return it }
        }

        return super.extractAmount(message)
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()

        // Keywords are tiered by signal strength so weaker directional words ("kwenda",
        // "received") can't override an unambiguous first-person verb. A deposit SMS may
        // also contain "kwenda" ("to"), and a send confirmation may mention the recipient
        // having "received" — the tiering resolves both correctly.
        return when {
            // Tier 1 — first-person, unambiguous direction.
            lower.contains("umepokea") -> TransactionType.INCOME            // you received
            lower.contains("umefanikiwa kutuma") -> TransactionType.EXPENSE // you sent
            lower.contains("umetuma") -> TransactionType.EXPENSE            // you sent

            // Tier 2 — strong action verbs.
            lower.contains("withdrawn") -> TransactionType.EXPENSE          // ATM withdrawal
            lower.contains("paid:") -> TransactionType.EXPENSE              // card payment
            lower.contains("malipo yamekamilika") -> TransactionType.EXPENSE // payment completed
            lower.contains("imelipwa") -> TransactionType.EXPENSE           // has been paid

            // Tier 3 — weaker income hints.
            lower.contains("received") -> TransactionType.INCOME
            lower.contains("deposited") -> TransactionType.INCOME

            // Tier 4 — weak directional marker (only reached when nothing stronger matched).
            lower.contains("kwenda") -> TransactionType.EXPENSE             // "to" (transfer)

            // Fallback — generic "transaction successful", defaults to expense to match
            // all provided send/payment samples.
            lower.contains("muamala umefanikiwa") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Card-payment form: "Paid:NETFLIX.COM, NL USD 9.99 Card:..."
        // Merchant is everything after "Paid:" up to the currency+amount.
        val paidMerchant = Regex(
            """Paid:\s*(.+?)\s+[A-Z]{3}\s*[0-9]""",
            RegexOption.IGNORE_CASE
        )
        paidMerchant.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim().trimEnd(',').trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // ATM withdrawal.
        if (message.contains("withdrawn using a Card", ignoreCase = true)) {
            return "ATM Withdrawal"
        }

        // Transfer / send (Swahili): "... kwenda <RECIPIENT> ...".
        // The recipient name follows "kwenda" and ends before any of:
        //   - a phone/account number or masked number (digits / "X" runs),
        //   - a structural label ("AC", "Risiti", "REF", "Balance"),
        // so we never leak phone numbers, account numbers or trailing metadata.
        // The terminator intentionally breaks on the FIRST digit (`\d`, not the
        // earlier `\d{6,}`): in CRDB transfers the recipient name is always
        // followed by a phone/account/till number, and CRDB recipient names do
        // not contain digits — so the single-digit break reliably drops the
        // trailing number. Do NOT widen this back to a multi-digit run, or the
        // leading digits of a short till/phone number would leak into the name.
        // "kwenda LIPA <merchant>" is a till/merchant payment — the "LIPA" prefix
        // is dropped and the merchant name kept.
        val kwendaPattern = Regex(
            """kwenda\s+(?:LIPA\s+)?(.+?)""" +
                """(?=\s+(?:\d|[0-9X*]{4,}|AC\b|A/C\b|Risiti\b|REF\b|Balance\b|Bal\b)|[.,]|$)""",
            RegexOption.IGNORE_CASE
        )
        kwendaPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim().trimEnd('.', ',').trim()
            // Guard: a bare "akaunti yako" ("your account") is not a recipient name.
            if (merchant.isNotEmpty() && !merchant.equals("akaunti yako", ignoreCase = true)) {
                return merchant
            }
        }

        // Utility / token purchase (Swahili): "Malipo yamekamilika ... TOTAL TZS 2000".
        // The LUKU electricity form has no merchant name in the body, only a TOKEN —
        // label it generically. The simple bill form keeps the text before the amount.
        if (message.contains("Malipo yamekamilika", ignoreCase = true)) {
            if (message.contains("TOKEN", ignoreCase = true) ||
                message.contains("KWH", ignoreCase = true)
            ) {
                return "LUKU"
            }
            val billPattern = Regex(
                """Malipo yamekamilika\s+(.+?)\s+TZS""",
                RegexOption.IGNORE_CASE
            )
            billPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) return merchant
            }
            return "Bill Payment"
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // SimBanking receipt id: "Risiti:003-19ec660b8a1c1bde".
        Regex("""Risiti:\s*([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { return it.groupValues[1].trim() }

        // Fall back to the generic Ref/REF handling (e.g. "REF: 19ec...").
        super.extractReference(message)?.let { return it }

        // SimBanking transaction id: "KUMB:19ec6ebe82c12bc7" (used when no receipt id).
        Regex("""KUMB:\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { return it.groupValues[1].trim() }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Balance is TZS 550070.90" or "Bal:TZS923041.06".
        val patterns = listOf(
            Regex("""Balance is\s+TZS\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Bal:\s*TZS\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                parseAmount(match.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Card number form: "Card 4232***0581" / "Card:4232***0581".
        val cardPattern = Regex("""Card:?\s*([0-9*]{4,})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }

        // Account-number labels (Swahili + English).
        val accountPattern = Regex(
            """(?:akaunti yako nambari|bank account|account|AC No\.?)[:\s]*([0-9*]{4,})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        // CRDB card forms use "... using a Card 4232***XXXX" / "Card:4232***0581",
        // which the generic detector misses. Treat any "Card <masked-number>" as a card.
        if (Regex("""\bCard:?\s*[0-9*X]{4,}""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Reject OTP / promotional noise.
        if (lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("verification code") ||
            lower.contains("namba ya siri")
        ) {
            return false
        }

        // Accept both English and Swahili transaction markers.
        val markers = listOf(
            "withdrawn",
            "paid:",
            "muamala umefanikiwa",
            "umefanikiwa kutuma",
            "umetuma",
            "malipo yamekamilika",
            "imelipwa",
            "kwenda",
            "umepokea",
            "received",
            "deposited"
        )

        return markers.any { lower.contains(it) }
    }
}
