package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parser for Dashen Bank - handles ETB currency transactions
 */
class DashenBankParser : BankParser() {

    override fun getBankName() = "Dashen Bank"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().trim()
        return normalized == "DASHENBANK"
    }

    /**
     * Extracts the transaction amount. Always picks the first ETB amount, not the balance.
     */
    override fun extractAmount(message: String): BigDecimal? {
        val amountPattern =
            Regex("""ETB\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

        amountPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].replace(",", "")
            return parseScaledAmount(raw)
        }

        return super.extractAmount(message)
    }

    /**
     * Extracts transaction type from Dashen Bank messages.
     */
    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit transactions are income
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("you have received") -> TransactionType.INCOME

            // Debit transactions are expenses
            lowerMessage.contains("has been debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            lowerMessage.contains("debited from") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // 1) Telebirr account (expense) - "credited to the Telebirr account +251922222222"
        val telebirrToPattern = Regex(
            """credited to the (Telebirr account [+\d]+)""",
            RegexOption.IGNORE_CASE
        )
        telebirrToPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // 2) Transfer credit - "from PERSON NAME on on"
        val fromPersonPattern = Regex(
            """from\s+([A-Z][A-Z\s]*?)\s+on\s+on""",
            RegexOption.IGNORE_CASE
        )
        fromPersonPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && isValidMerchantName(merchant)) return merchant
        }

        // 3) Telebirr account (income) - "from telebirr account number 251922222222 Ref"
        val telebirrFromPattern = Regex(
            """from\s+(telebirr account number \d+\s)Ref""",
            RegexOption.IGNORE_CASE
        )
        telebirrFromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1]
            if (merchant.isNotEmpty()) return merchant
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Dashen masks accounts as "5387********011" or "5387*****9011"
        val pattern = Regex("""(\d{4})\*+\d+""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // 1) "Your current balance is ETB 1,846.06"
        val currentBalancePattern = Regex(
            """Your\s+current\s+balance\s+is\s+ETB\s+([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        currentBalancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        // 2) "Your account balance is ETB 543.49"
        val accountBalancePattern = Regex(
            """Your\s+account\s+balance\s+is\s+ETB\s+([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        accountBalancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // 1) Receipt URL - "https://receipt.dashensuperapp.com/receipt/..."
        val receiptUrlPattern = Regex(
            """(https://receipt\.dashensuperapp\.com/receipt/[^\s]+)""",
            RegexOption.IGNORE_CASE
        )
        receiptUrlPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // 2) "Ref No:2209012000164277"
        val refNoPattern = Regex(
            """Ref\s+No:(\d+)""",
            RegexOption.IGNORE_CASE
        )
        refNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
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
