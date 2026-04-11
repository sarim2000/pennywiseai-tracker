package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.Normalizer

class DOPBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Department of Post"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("DOPBNK") ||
                upper.contains("DEPARTMENT OF POST") ||
                upper.contains("DOP-") ||
                upper.endsWith("-DOP") ||
                upper == "DOP"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val normalizedBody = normalizeUnicodeText(smsBody)
        return super.parse(normalizedBody, sender, timestamp)
    }

    private fun normalizeUnicodeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("[^\\p{ASCII}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun extractAmount(message: String): BigDecimal? {
        val amountPattern = Regex(
            """amount\s+(?:Rs\.?|INR)?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractAccountLast4(message: String): String? {
        val accountPattern = Regex(
            """Acc(?:ount)?\s*(?:No\.?)?\s+(?:[X*]+)?(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credit") -> TransactionType.INCOME
            lower.contains("debit") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """Bal(?:ance)?\s*(?::)?\s*(?:Rs\.?|INR)?\s*([\d,]+(?:\.\d{2})?)""",
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

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        val refPattern = Regex(
            """\[([A-Z0-9]+)\]""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        val hasKeyword = lower.contains("account") || lower.contains("a/c") || lower.contains("dop")
        val hasType = lower.contains("credit") || lower.contains("debit")
        return hasKeyword && hasType
    }
}
