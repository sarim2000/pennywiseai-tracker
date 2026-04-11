package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for STC Bank (Saudi Arabia).
 *
 * Handles English purchase / transfer formats such as:
 *   **4561 Purchase
 *   Via:4561
 *   Amount: 3 SAR
 *   From: ABDULLAH SALEM MUEEN
 *   At: 26/07/25 21:58
 *   STC Bank
 *
 * Sender examples: STC Bank, STCBank, STC-Bank, STC
 */
class STCBankParser : BankParser() {

    override fun getBankName() = "STC Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("[\\s\\-_]"), "")
        return normalized.contains("STCBANK") || normalized == "STC" || normalized == "STCPAY"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "Amount: 3 SAR" or "Amount:3 SAR" or "Amount: 3.50 SAR"
        val amountPattern = Regex(
            """Amount\s*:?\s*([0-9,]+(?:\.\d{1,2})?)\s*SAR""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        // "SAR 3.00" fallback
        val sarFirstPattern = Regex(
            """SAR\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        sarFirstPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("purchase") -> TransactionType.EXPENSE
            lower.contains("withdrawal") || lower.contains("withdraw") -> TransactionType.EXPENSE
            lower.contains("payment") -> TransactionType.EXPENSE
            lower.contains("debit") -> TransactionType.EXPENSE
            lower.contains("transfer out") || lower.contains("sent to") -> TransactionType.EXPENSE
            lower.contains("refund") -> TransactionType.INCOME
            lower.contains("deposit") -> TransactionType.INCOME
            lower.contains("credit") && !lower.contains("credit card") -> TransactionType.INCOME
            lower.contains("received") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // "From: MERCHANT NAME" — merchant for Purchase, sender for incoming
        val fromPattern = Regex(
            """From\s*:\s*([^\n]+?)(?:\n|At\s*:|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // "To: RECIPIENT NAME" — recipient for outgoing transfers
        val toPattern = Regex(
            """To\s*:\s*([^\n]+?)(?:\n|At\s*:|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "**4561 Purchase" / "*4561 Purchase"
        val starPattern = Regex("""\*+(\d{4})\b""")
        starPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        // "Via:4561" / "Via: 4561"
        val viaPattern = Regex("""Via\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE)
        viaPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        return super.extractAccountLast4(message)
    }

    override fun detectIsCard(message: String): Boolean {
        // Presence of masked card (**XXXX) or Via:XXXX indicates card transaction
        if (Regex("""\*+\d{4}""").containsMatchIn(message)) return true
        if (Regex("""Via\s*:\s*\d{4}""", RegexOption.IGNORE_CASE).containsMatchIn(message)) return true
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        if (lower.contains("otp") || lower.contains("verification code") ||
            lower.contains("one time password")
        ) {
            return false
        }

        val keywords = listOf(
            "purchase",
            "amount",
            "withdraw",
            "transfer",
            "payment",
            "refund",
            "deposit",
            "debit",
            "credit",
            "sar"
        )
        return keywords.any { lower.contains(it) }
    }
}
