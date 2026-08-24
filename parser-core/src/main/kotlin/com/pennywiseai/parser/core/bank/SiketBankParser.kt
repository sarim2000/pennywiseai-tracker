package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parser for Siket Bank (Ethiopia) - handles ETB currency transactions.
 *
 * Supported formats:
 *  - Credit:   "... Your Account 1****XXXX has been Credited with ETB 30,000.00. Your Current Balance is ETB ..."
 *  - Debit:    "... Your Account 1****XXXX has been Debited with ETB 50,000.00. Your Current Balance is ETB ..."
 *  - Transfer: "... You have transferred ETB 10,012.00 from your account 1****XXXX to telebirr account ...
 *               with Reference number FT.... The Service Charge is ETB10.00 and VAT of ETB1.50.
 *               Your Current Balance is ETB ..."
 */
class SiketBankParser : BankParser() {

    override fun getBankName() = "Siket Bank"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().trim()
        return normalized == "SIKET BANK" ||
                normalized == "SIKETBANK" ||
                normalized == "SIKET" ||
                normalized.matches(Regex("""^[A-Z]{2}-SIKET-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Prefer the verb-linked amount so we never capture Service Charge / VAT / balance.
        val patterns = listOf(
            Regex("""Credited\s+with\s+ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Debited\s+with\s+ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""transferred\s+ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return parseScaledAmount(match.groupValues[1])
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            // Outgoing transfers are money leaving the account -> EXPENSE
            lowerMessage.contains("you have transferred") -> TransactionType.EXPENSE
            lowerMessage.contains("transferred etb") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Transfer counterparty: "to telebirr account 251900000000 on ..."
        val telebirrPattern = Regex(
            """to\s+(telebirr account\s+[+\d]+)""",
            RegexOption.IGNORE_CASE
        )
        telebirrPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Siket masks accounts as "1****XXXX"
        val patterns = listOf(
            Regex("""Account\s+([\d*]+)""", RegexOption.IGNORE_CASE),
            Regex("""your account\s+([\d*]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                extractLast4Digits(match.groupValues[1])?.let { return it }
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """Current\s+Balance\s+is\s+ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Transfer messages: "with Reference number FT..."
        val refPattern = Regex(
            """Reference\s+number\s+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            val ref = match.groupValues[1]
            if (ref.isNotEmpty()) return ref
        }

        return super.extractReference(message)
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
