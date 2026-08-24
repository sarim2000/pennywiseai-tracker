package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parser for ZamZam Bank (Ethiopia) - handles ETB currency transactions.
 */
class ZamZamBankParser : BankParser() {

    override fun getBankName() = "ZamZam Bank"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().trim()
        return normalized == "ZAMZAM BANK" ||
                normalized == "ZAMZAMBANK" ||
                // DLT-style variant, e.g. "AB-ZAMZAM-S"
                normalized.matches(Regex("""^[A-Z]{2}-ZAMZAM-[A-Z]$"""))
    }

    /**
     * Extracts the transaction amount. Always picks the first ETB amount, not the balance.
     * Amounts may be whole with no decimals (e.g. "ETB 32000"); scale is normalised to 2.
     */
    override fun extractAmount(message: String): BigDecimal? {
        val amountPattern =
            Regex("""ETB\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

        amountPattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("credited by") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME

            lowerMessage.contains("has been debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Credit: "has been credited by <NAME> with ETB ..."
        val creditedByPattern =
            Regex("""has\s+been\s+credited\s+by\s+(.+?)\s+with\s+ETB\b""", RegexOption.IGNORE_CASE)
        creditedByPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1]
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Masked accounts like "****NNNNNN" or "***NNNNNN".
        val pattern = Regex("""\*+(\d+)""")
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """current\s+balance\s+is\s+ETB\s+([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    private fun parseScaledAmount(rawAmount: String): BigDecimal? {
        val normalized = rawAmount.replace(",", "")
        return try {
            BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
