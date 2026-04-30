package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Altana Federal Credit Union (USA)
 *
 * Supported format:
 * - Debit card pending charge:
 *   "Pending charge for $43.92 on 04/24 20:39 CDT at MERCHANT, CITY, STATE for Debit Consumer card ending in 1234."
 *
 * Common senders: "Altana FCU", or the toll-free number (877) 590-5546.
 */
class AltanaFCUParser : BankParser() {

    override fun getBankName() = "Altana Federal Credit Union"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        if (upper.contains("ALTANA")) return true

        // Match the toll-free number across common formats: "(877) 590-5546", "877-590-5546",
        // "8775905546", "+18775905546" (and digit-only variants).
        val digits = sender.filter { it.isDigit() }
        return digits == "8775905546" || digits == "18775905546"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "charge for $43.92 on" — captures both pending and posted charge variants.
        val pattern = Regex(
            """charge\s+for\s+\$([0-9,]+(?:\.\d{2})?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("charge for") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // " at MERCHANT, CITY, STATE for Debit Consumer card "
        val pattern = Regex(
            """\bat\s+(.+?)\s+for\s+(?:Debit|Credit)\s+Consumer""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim().trimEnd(','))
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // "card ending in 1234"
        val pattern = Regex(
            """ending\s+in\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(message)?.groupValues?.get(1)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("charge for") &&
            lower.contains("ending in") &&
            lower.contains("card")
        ) {
            return true
        }
        return super.isTransactionMessage(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        return when {
            lower.contains("debit consumer card") -> true
            lower.contains("credit consumer card") -> true
            else -> super.detectIsCard(message)
        }
    }
}
