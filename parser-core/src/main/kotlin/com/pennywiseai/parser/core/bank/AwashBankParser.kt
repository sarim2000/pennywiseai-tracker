package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parser for Awash Bank (Ethiopia) - handles ETB currency transactions.
 *
 * Supported formats:
 *  - Credit  : "ETB <amt> has been credited to your account from <NAME> ... with Txn ID: <id>. Your available balance is now ETB <bal>."
 *  - Telebirr: "Telebirr Transfer of <amt> ETB to <NAME> - <phone> from <acct>/BANK, ... Charge <c> VAT: <v>. Your Balance is ETB <bal>."
 *  - Transfer: "You have sent ETB <amt> To (<acct>) - <NAME> by Transaction ID: <id> charge- <c> VAT- <v> ... Your Available Balance is <bal>."
 *
 * The transaction amount appears both as "ETB <amt>" and "<amt> ETB", and must never be the
 * Charge or VAT value. Balance labels vary and the last format omits the ETB prefix on the number.
 */
class AwashBankParser : BankParser() {

    override fun getBankName() = "Awash Bank"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().trim()
        return normalized == "AWASH BANK" ||
                normalized == "AWASHBANK" ||
                normalized == "AWASH" ||
                normalized.matches(Regex("""^[A-Z]{2}-AWASH-[A-Z]$"""))
    }

    /**
     * Extracts the transaction amount (never the Charge or VAT). Handles both
     * "ETB <amt>" and "<amt> ETB" positioning, anchored to the action verb.
     */
    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // Credit: "ETB 200 has been credited"
            Regex("""ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)\s+has\s+been\s+credited""", RegexOption.IGNORE_CASE),
            // Telebirr transfer: "Transfer of 30,000.00 ETB"
            Regex("""Transfer\s+of\s+([0-9,]+(?:\.[0-9]{1,2})?)\s*ETB""", RegexOption.IGNORE_CASE),
            // Bank transfer: "You have sent ETB 1,000"
            Regex("""sent\s+ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return parseScaledAmount(match.groupValues[1])
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("has been credited") -> TransactionType.INCOME
            lower.contains("credited to your account") -> TransactionType.INCOME

            lower.contains("telebirr transfer") -> TransactionType.EXPENSE
            lower.contains("you have sent") -> TransactionType.EXPENSE
            lower.contains("has been debited") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Bank transfer: "To (<acct>) - <NAME> by Transaction"
        val bankTransferPattern = Regex(
            """To\s*\([0-9]+\)\s*-\s*([A-Za-z][A-Za-z\s]+?)\s+by\s+Transaction""",
            RegexOption.IGNORE_CASE
        )
        bankTransferPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Telebirr transfer: "to <NAME> - <phone> from"
        val telebirrPattern = Regex(
            """to\s+([A-Za-z][A-Za-z\s]+?)\s*-\s*[0-9]+\s+from""",
            RegexOption.IGNORE_CASE
        )
        telebirrPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Credit: "from <NAME> on:"
        val creditPattern = Regex(
            """from\s+([A-Za-z][A-Za-z\s]+?)\s+on:?""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Credit: "Txn ID: <digits>"
        val txnIdPattern = Regex("""Txn\s+ID:\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        txnIdPattern.find(message)?.let { return it.groupValues[1] }

        // Bank transfer: "Transaction ID: <digits>"
        val transactionIdPattern = Regex("""Transaction\s+ID:\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        transactionIdPattern.find(message)?.let { return it.groupValues[1] }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Only the Telebirr format exposes the customer's own account: "from <acct>/BANK".
        // The "To (<acct>)" in bank transfers is the recipient, so it is deliberately ignored.
        val ownAccountPattern = Regex("""from\s+(\d{5,})/BANK""", RegexOption.IGNORE_CASE)
        ownAccountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Handles all three labels, with or without the ETB prefix on the number:
        //  "available balance is now ETB 12,269.09", "Your Balance is ETB 10,151.17",
        //  "Your Available Balance is 11,818.92"
        val balancePattern = Regex(
            """Balance\s+is\s+(?:now\s+)?(?:ETB\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseScaledAmount(match.groupValues[1])
        }
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        val keywords = listOf(
            "has been credited",
            "has been debited",
            "telebirr transfer",
            "you have sent",
            "your balance is",
            "available balance"
        )
        if (keywords.any { lower.contains(it) }) return true
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
