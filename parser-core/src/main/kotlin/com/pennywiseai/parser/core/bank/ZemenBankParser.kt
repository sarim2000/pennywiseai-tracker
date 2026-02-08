package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parser for Zemen Bank - handles ETB currency transactions
 */
class ZemenBankParser : BankParser() {

    override fun getBankName() = "Zemen Bank"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().trim()
        // Support plain and DLT-style senders for Zemen Bank
        return normalized == "ZEMEN BANK" ||
                normalized.replace(" ", "") == "ZEMENBANK" ||
                normalized.matches(Regex("""^[A-Z]{2}-ZEMENBANK-[A-Z]$"""))
    }

    /**
     * Zemen Bank messages use both "ETB" and "Birr" and sometimes omit
     * the decimal places (e.g., "Birr 100", "ETB 6000", "Birr 1593.9").
     * Normalise all matched amounts to scale 2 so they match test expectations.
     */
    override fun extractAmount(message: String): BigDecimal? {
        // Always pick the first transaction amount, not the balance
        val amountPattern =
            Regex("""(?:ETB|Birr)\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

        amountPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].replace(",", "")
            return parseScaledAmount(raw)
        }

        return super.extractAmount(message)
    }

    /**
     * Zemen messages have a few phrases that don't use the normal
     * "debited"/"credited" wording, so we extend the standard logic.
     */
    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit transactions are income
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME

            // Debit transactions are expenses
            lowerMessage.contains("has been debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE

            // Phrases used for outgoing transfers/withdrawals
            lowerMessage.contains("fund transfer has been made from") -> TransactionType.EXPENSE
            lowerMessage.contains("pos transaction has been made from") -> TransactionType.EXPENSE
            lowerMessage.contains("atm cash withdrawal has been made from") -> TransactionType.EXPENSE

            // Generic transfer wording (fallback)
            lowerMessage.contains("you have transfered") -> TransactionType.EXPENSE
            lowerMessage.contains("you have transferred") -> TransactionType.EXPENSE
            lowerMessage.contains("transferred") && lowerMessage.contains("from a/c") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // 1) Telebirr wallet transfers (income/expense)
        val telebirrFromPattern = Regex(
            """from\s+(telebirr wallet\s+\d+)\s+with reference""",
            RegexOption.IGNORE_CASE
        )
        telebirrFromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1]
            if (merchant.isNotEmpty()) return merchant
        }

        val telebirrToPattern = Regex(
            """to\s+(telebirr wallet\s+\d+)\s+with reference""",
            RegexOption.IGNORE_CASE
        )
        telebirrToPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1]
            if (merchant.isNotEmpty()) return merchant
        }

        // 2) External bank transfer – merchant is destination account number
        val toAccountPattern = Regex(
            """to\s+A/c\s+of\s+(\d{6,})""",
            RegexOption.IGNORE_CASE
        )
        toAccountPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // 3) Bank transfer income – "from other bank"
        val fromOtherBankPattern = Regex(
            """from\s+([^,\.]+?)\s+with reference""",
            RegexOption.IGNORE_CASE
        )
        fromOtherBankPattern.find(message)?.let { match ->
            val raw = match.groupValues[1]
            val merchant = cleanMerchantName(raw).trim()
            if (merchant.isNotEmpty() && isValidMerchantName(merchant)) return merchant
        }

        // 4) POS purchase – merchant after "at ... on <date>"
        val posPurchasePattern = Regex(
            """pos purchase transaction at\s+(.+?)\s+on\s+\d{1,2}-[A-Za-z]{3}-\d{4}""",
            RegexOption.IGNORE_CASE
        )
        posPurchasePattern.find(message)?.let { match ->
            val raw = match.groupValues[1]
            val merchant = cleanMerchantName(raw).trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // 5) POS transaction with explicit POS location
        val posLocationPattern = Regex(
            """transaction POS location is\s+(.+?)\s*\. """,
            RegexOption.IGNORE_CASE
        )
        posLocationPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // 6) External bank transfer – full beneficiary and bank
        val externalBeneficiaryPattern = Regex(
            """to\s+(.+?)\s+with reference""",
            RegexOption.IGNORE_CASE
        )
        externalBeneficiaryPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // 7) ATM withdrawal – ATM location
        val atmLocationPattern = Regex(
            """transaction ATM location is\s+(.+?)\s*\. """,
            RegexOption.IGNORE_CASE
        )
        atmLocationPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Zemen masks accounts as "109xxxxxxxx7018" or inside parentheses "(109xxxxxxxx7018)"
        val patterns = listOf(
            Regex("""\b\d{3}x+(\d{4})\b""", RegexOption.IGNORE_CASE),
            Regex("""\(\d{3}x+(\d{4})\)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // 1) "Your Current Balance is ETB 10823.37"
        val currentBalancePattern = Regex(
            """Your\s+Current\s+Balance\s+is\s+(?:ETB|Birr)\s+([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        currentBalancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        // 2) "The A/c Available Bal. is Birr  293.06"
        val availableBalancePattern = Regex(
            """A/c\s+Available\s+Bal\.\s+is\s+(?:ETB|Birr)\s+([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        availableBalancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        // 3) "Your available balance is Birr 1942.01"
        val yourAvailableBalancePattern = Regex(
            """Your\s+available\s+balance\s+is\s+(?:ETB|Birr)\s+([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        yourAvailableBalancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // 1) Explicit transaction reference number
        val txnRefPattern = Regex(
            """transaction reference number is\s+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        txnRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // 2) "with reference 109TEIN260350016"
        val withReferencePattern = Regex(
            """with reference\s+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        withReferencePattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // 3) PDF receipt link (used when no explicit reference present)
        val linkPattern = Regex(
            """(https://share\.zemenbank\.com/[^\s]+?/pdf)""",
            RegexOption.IGNORE_CASE
        )
        linkPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Zemen-specific transaction keywords
        val zemenKeywords = listOf(
            "dear customer",
            "your account",
            "has been credited",
            "has been debited",
            "fund transfer has been made from",
            "pos transaction has been made from",
            "atm cash withdrawal has been made from",
            "current balance",
            "available bal.",
            "thank you for banking with zemen bank",
            "etb",
            "birr"
        )

        if (zemenKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
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