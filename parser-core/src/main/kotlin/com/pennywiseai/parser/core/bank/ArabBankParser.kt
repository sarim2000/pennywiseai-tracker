package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Arab Bank (Egypt) SMS messages.
 *
 * Arab Bank sends both English and Arabic SMS for a multi-currency card account.
 * Default / base currency is the Egyptian Pound (EGP). The TRANSACTION can be in
 * EGP or USD, but the account's available balance is always reported in EGP.
 * The transaction amount/currency reflect the spent currency (EGP or USD) while
 * the balance currency stays EGP (the parser's base currency). See [parse].
 *
 * Supported formats:
 * - English card spend (EXPENSE):
 *   "A Trx using Card XXXX2020 from <MERCHANT> for <CCY> <amount> on <date> at <time>.
 *    Available balance is EGP <balance>."
 *   - merchant: text between "from " and " for "
 *   - amount currency: the CCY token after "for " (EGP or USD)
 * - Arabic credit to the card (INCOME):
 *   "تم قيد مبلغ <amount> جنيه لبطاقتك الائتمانية رقم #<last4>"
 *   - amount in EGP ("جنيه"), no merchant, no balance
 *
 * Sender: ArabBank (token ARABBANK, case-insensitive).
 */
class ArabBankParser : BankParser() {

    override fun getBankName() = "Arab Bank"

    override fun getCurrency() = "EGP"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("ARABBANK")
    }

    /**
     * Override parse to surface the per-transaction currency.
     *
     * English transactions carry their own currency token after "for " (EGP or USD).
     * Arabic credits are always EGP. The balance figure stays EGP regardless of the
     * transaction currency — that is handled by [extractBalance] returning the EGP
     * "Available balance" amount.
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val transaction = super.parse(smsBody, sender, timestamp) ?: return null
        val currency = extractCurrency(smsBody) ?: getCurrency()
        return transaction.copy(
            currency = currency,
            isFromCard = true
        )
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Reject OTP / verification noise.
        if (lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("verification code") ||
            lower.contains("passcode") ||
            message.contains("رمز التحقق") ||      // Arabic: "verification code"
            message.contains("كلمة المرور")        // Arabic: "password"
        ) {
            return false
        }

        // English card spend marker.
        if (Regex("""using\s+Card""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return true
        }

        // Arabic credit marker: "تم قيد مبلغ" ("an amount has been credited").
        if (message.contains("تم قيد")) {
            return true
        }

        return false
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // Arabic credit to the card.
        if (message.contains("تم قيد")) {
            return TransactionType.INCOME
        }

        // English card spend.
        if (Regex("""using\s+Card""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return TransactionType.EXPENSE
        }

        return null
    }

    override fun extractAmount(message: String): BigDecimal? {
        // English: "for <CCY> <amount>" e.g. "for EGP 123.45" / "for USD 9.84".
        val englishPattern = Regex(
            """for\s+[A-Z]{3}\s+([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        englishPattern.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Arabic: "<amount> جنيه" e.g. "500.00 جنيه".
        val arabicPattern = Regex("""([0-9,]+(?:\.\d+)?)\s*جنيه""")
        arabicPattern.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        return null
    }

    override fun extractCurrency(message: String): String? {
        // English: the CCY token after "for " (EGP or USD).
        val englishPattern = Regex(
            """for\s+([A-Z]{3})\s+[0-9,]+(?:\.\d+)?""",
            RegexOption.IGNORE_CASE
        )
        englishPattern.find(message)?.let { match ->
            return match.groupValues[1].uppercase()
        }

        // Arabic amount is always in Egyptian pounds ("جنيه").
        if (message.contains("جنيه")) {
            return "EGP"
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // English only: merchant is the text between "from " and " for ".
        val merchantPattern = Regex(
            """from\s+(.+?)\s+for\s+[A-Z]{3}\s""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Arabic credit SMS has no merchant.
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // English: "Card XXXX2020" -> 2020.
        val englishCard = Regex(
            """Card\s+[X*]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        englishCard.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Arabic: "رقم #2020" -> 2020.
        val arabicCard = Regex("""رقم\s*#(\d{4})""")
        arabicCard.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // English: "Available balance is EGP <amount>" — always EGP.
        val balancePattern = Regex(
            """Available\s+balance\s+is\s+EGP\s+([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Arabic credit SMS carries no balance.
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        // This is a card account; every supported message is a card transaction.
        return true
    }

    private fun parseAmount(raw: String): BigDecimal? {
        return try {
            BigDecimal(raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }
}
