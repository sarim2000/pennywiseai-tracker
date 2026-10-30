package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Commercial Bank of Ceylon (ComBank, Sri Lanka) SMS alerts. (#848)
 *
 * Card purchases are announced as an *authorisation* rather than a settlement,
 * so the base class's verb list ("debited", "spent", …) never matched them:
 * - "Dear Cardholder, Purchase at <Merchant> for LKR 672.00 on 27/09/26 09:32 PM
 *    has been authorised on your debit card ending #5555."
 *
 * Credits use an inverted structure, amount before account:
 * - "Credit for Rs. 25,000.00 to A/C #1234 ..."
 *
 * The amount carries either a 3-letter code (LKR for local, USD/GBP/… for
 * foreign purchases) or "Rs.", which here always means LKR.
 *
 * Sender: "ComBank" (matched case-insensitively, with or without an
 * operator prefix/suffix).
 */
class ComBankParser : BankParser() {

    override fun getBankName() = "Commercial Bank of Ceylon"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean =
        // Deliberately NOT "COMMERCIALBANK" — that sender belongs to the
        // Commercial Bank of Ethiopia parser.
        sender.uppercase().contains("COMBANK")

    /** "LKR 672.00" / "Rs. 1,250.50" — amount with its currency token. */
    private val amountToken = """(?:(?<code>[A-Z]{3})|Rs\.?)\s*(?<amount>[0-9,]+(?:\.\d{1,2})?)"""

    private val purchasePattern = Regex(
        """Purchase\s+at\s+(?<merchant>.+?)\s+for\s+$amountToken\b.*?""" +
            """has\s+been\s+authorised\s+on\s+your\s+(?<kind>debit|credit)\s+card\s+ending\s+#?(?<card>\d{4})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val creditPattern = Regex(
        """Credit\s+for\s+$amountToken\s+to\s+(?:A/C|Account)?\s*#?\*?(?<account>\d{3,})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        purchasePattern.find(smsBody)?.let { m ->
            val amount = amountOf(m) ?: return null
            return ParsedTransaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                merchant = cleanMerchantName(m.groups["merchant"]!!.value.trim()),
                reference = extractReference(smsBody),
                accountLast4 = m.groups["card"]!!.value,
                balance = extractBalance(smsBody),
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                isFromCard = true,
                currency = currencyOf(m)
            )
        }

        creditPattern.find(smsBody)?.let { m ->
            val amount = amountOf(m) ?: return null
            return ParsedTransaction(
                amount = amount,
                type = TransactionType.INCOME,
                merchant = null,
                reference = extractReference(smsBody),
                accountLast4 = m.groups["account"]!!.value.takeLast(4),
                balance = extractBalance(smsBody),
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                isFromCard = false,
                currency = currencyOf(m)
            )
        }

        return null
    }

    private fun amountOf(match: MatchResult): BigDecimal? =
        match.groups["amount"]?.value?.replace(",", "")?.toBigDecimalOrNull()

    /** "Rs." is LKR; an explicit code wins so foreign purchases keep their currency. */
    private fun currencyOf(match: MatchResult): String =
        match.groups["code"]?.value?.uppercase() ?: getCurrency()
}
