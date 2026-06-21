package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Arab Bank SMS messages.
 *
 * Arab Bank is a single bank operating in multiple countries. The SMS currency
 * token (EGP / JOD / USD) is the differentiator between markets — there is ONE
 * parser for all of them. The base/default currency stays EGP (Egypt), while
 * per-message currency detection picks EGP, JOD or USD.
 *
 * Supported formats:
 * - English card spend (EXPENSE), Egypt + Jordan (isFromCard = true):
 *   "A Trx using Card XXXX2020 from <MERCHANT> for <CCY> <amount> on <date> at <time>.
 *    Available balance is <CCY> <balance>."
 *   - merchant: text between "from " and " for <CCY>"
 *   - amount currency: the CCY token after "for " (EGP, USD or JOD)
 *   - JOD uses 3 fractional digits.
 * - Arabic credit to the card (INCOME), Egypt (isFromCard = true):
 *   "تم قيد مبلغ <amount> جنيه لبطاقتك الائتمانية رقم #<last4>"
 *   - amount in EGP ("جنيه"), no merchant, no balance
 * - Jordan account transfer (CliQ), NOT a card (isFromCard = false):
 *   - DEBIT (EXPENSE):
 *     "<CCY><amount> has been debited from <acct> to <name> as CliQ transfer Balance <bal><CCY>"
 *   - CREDIT (INCOME):
 *     "<CCY><amount> has been credited to <acct>from <name> as CliQ transfer Balance <bal><CCY>"
 *   - currency token may be a PREFIX ("JOD50.000") or SUFFIX on balance ("37.920JOD").
 *
 * Sender: ArabBank (token ARABBANK) or DLT-style "AB-ARABBK-S".
 */
class ArabBankParser : BankParser() {

    override fun getBankName() = "Arab Bank"

    override fun getCurrency() = "EGP"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        if (normalized.contains("ARABBANK")) return true
        // DLT-style "AB-ARABBK-S": strip spaces/dashes/underscores then match.
        val stripped = normalized.replace(Regex("""[\s\-_]"""), "")
        return stripped.contains("ARABBANK") ||
            stripped.matches(Regex("""^[A-Z]{2}ARABBK[A-Z]?$"""))
    }

    /**
     * Override parse to surface the per-transaction currency (EGP, JOD or USD).
     * isFromCard is decided by detectIsCard (card spends + Arabic credit = true,
     * account transfers = false).
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val transaction = super.parse(smsBody, sender, timestamp) ?: return null
        val currency = extractCurrency(smsBody) ?: getCurrency()
        return transaction.copy(currency = currency)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Reject OTP / verification / failed-transaction noise.
        // The failure check uses specific status phrases rather than a bare
        // "failed", so a successful SMS that merely references a prior failed
        // attempt is not dropped.
        if (lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("verification code") ||
            lower.contains("passcode") ||
            lower.contains("declined") ||
            lower.contains("transaction failed") ||
            lower.contains("trx failed") ||
            lower.contains("has failed") ||
            lower.contains("was unsuccessful") ||
            lower.contains("not successful") ||
            message.contains("رمز التحقق") ||      // Arabic: "verification code"
            message.contains("كلمة المرور")        // Arabic: "password"
        ) {
            return false
        }

        // English card spend marker.
        if (Regex("""using\s+Card""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return true
        }

        // Jordan account transfer markers.
        if (lower.contains("has been debited") || lower.contains("has been credited")) {
            return true
        }

        // Arabic credit marker: "تم قيد مبلغ" ("an amount has been credited").
        if (message.contains("تم قيد")) {
            return true
        }

        return false
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()

        // Arabic credit to the card.
        if (message.contains("تم قيد")) {
            return TransactionType.INCOME
        }

        // English card spend.
        if (Regex("""using\s+Card""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return TransactionType.EXPENSE
        }

        // Jordan account transfers.
        if (lower.contains("has been debited")) {
            return TransactionType.EXPENSE
        }
        if (lower.contains("has been credited")) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractAmount(message: String): BigDecimal? {
        // English card spend: "for <CCY> <amount>" e.g. "for EGP 123.45" / "for JOD 2.750".
        val cardPattern = Regex(
            """for\s+[A-Z]{3}\s+([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Jordan transfer: leading "<CCY><amount>" e.g. "JOD50.000" / "USD 50.000".
        val transferPattern = Regex(
            """^(?:JOD|USD|EGP)\s*([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        transferPattern.find(message.trim())?.let { match ->
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
        // English card spend: the CCY token after "for " (EGP, USD or JOD).
        val cardPattern = Regex(
            """for\s+([A-Z]{3})\s+[0-9,]+(?:\.\d+)?""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1].uppercase()
        }

        // Jordan transfer: leading "<CCY><amount>".
        val transferPattern = Regex(
            """^(JOD|USD|EGP)\s*[0-9,]+(?:\.\d+)?""",
            RegexOption.IGNORE_CASE
        )
        transferPattern.find(message.trim())?.let { match ->
            return match.groupValues[1].uppercase()
        }

        // Arabic amount is always in Egyptian pounds ("جنيه").
        if (message.contains("جنيه")) {
            return "EGP"
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // English card spend: merchant is the text between "from " and " for <CCY>".
        val cardPattern = Regex(
            """from\s+(.+?)\s+for\s+[A-Z]{3}\s""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        val lower = message.lowercase()

        // Jordan transfer DEBIT: name after "to " up to " as " / " Balance" / end.
        if (lower.contains("has been debited")) {
            val debitPattern = Regex(
                """\bto\s+(.+?)(?:\s+as\b|\s+Balance\b|$)""",
                RegexOption.IGNORE_CASE
            )
            debitPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Jordan transfer CREDIT: name after "from " up to " as " / " Balance" / end.
        // Handle the no-space "0156*500from Jane Doe" case.
        if (lower.contains("has been credited")) {
            val creditPattern = Regex(
                """from\s*(.+?)(?:\s+as\b|\s+Balance\b|$)""",
                RegexOption.IGNORE_CASE
            )
            creditPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Arabic credit SMS has no merchant.
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // English card spend: "Card XXXX2020" -> 2020.
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

        val lower = message.lowercase()

        // Jordan transfer DEBIT: "debited from <acct>" e.g. "from 0156*500".
        if (lower.contains("has been debited")) {
            val debitAcct = Regex(
                """debited\s+from\s+([0-9*]+)""",
                RegexOption.IGNORE_CASE
            )
            debitAcct.find(message)?.let { match ->
                extractLast4Digits(match.groupValues[1])?.let { return it }
            }
        }

        // Jordan transfer CREDIT: "credited to <acct>" e.g. "to 0156*500from".
        if (lower.contains("has been credited")) {
            val creditAcct = Regex(
                """credited\s+to\s+([0-9*]+)""",
                RegexOption.IGNORE_CASE
            )
            creditAcct.find(message)?.let { match ->
                extractLast4Digits(match.groupValues[1])?.let { return it }
            }
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Card spend (Egypt + Jordan): "Available balance is <CCY> <amount>".
        val cardBalance = Regex(
            """Available\s+balance\s+is\s+[A-Z]{3}\s+([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        cardBalance.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Jordan transfer suffix form: "Balance <amount><CCY>" e.g. "Balance 37.920JOD".
        val suffixBalance = Regex(
            """Balance\s+([0-9,]+(?:\.\d+)?)\s*(?:JOD|USD|EGP)""",
            RegexOption.IGNORE_CASE
        )
        suffixBalance.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Jordan transfer prefix form: "Balance <CCY> <amount>".
        val prefixBalance = Regex(
            """Balance\s+(?:JOD|USD|EGP)\s+([0-9,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        prefixBalance.find(message)?.let { match ->
            parseAmount(match.groupValues[1])?.let { return it }
        }

        // Arabic credit SMS carries no balance.
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()

        // Account transfers are NOT card transactions.
        if (lower.contains("has been debited") ||
            lower.contains("has been credited") ||
            lower.contains("debited from") ||
            lower.contains("credited to")
        ) {
            return false
        }

        // English card spend.
        if (lower.contains("trx using card")) {
            return true
        }

        // Arabic card credit ("لبطاقتك" = "to your card").
        if (message.contains("تم قيد") || message.contains("لبطاقتك")) {
            return true
        }

        return false
    }

    private fun parseAmount(raw: String): BigDecimal? {
        return try {
            BigDecimal(raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }
}
