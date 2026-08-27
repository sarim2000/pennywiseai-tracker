package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Apollo (Ethiopia) mobile wallet - handles ETB currency transactions.
 * Shares the same SMS template as Bank of Abyssinia (verb-linked amount,
 * "Available Balance", masked account) but is dispatched on its own `apollo` sender.
 *
 * Sample formats:
 *  - Credit: "Dear <NAME>, your account 1*34 was credited with ETB 550.00 by
 *    <SENDER NAME>. Available Balance: ETB 12,357.73. Receipt: <link> For help, call ..."
 *  - Debit:  "Dear <NAME>, your account 1*34 was debited with ETB 7,000.00.
 *    Available Balance: ETB 5,595.43. Receipt: <link> For help, call ..."
 */
class ApolloParser : BankParser() {

    override fun getBankName() = "Apollo"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    // Apollo is a mobile wallet: route its running balance to the single
    // service-level wallet account (upsertWalletBalance → WALLET_ACCOUNT_MARKER)
    // rather than fabricating per-account balance rows from the masked account.
    override fun isMobileWallet() = true

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase().trim()
        return upperSender == "APOLLO" ||
                // DLT-style variants such as "AB-APOLLO-S"
                upperSender.matches(Regex("""^[A-Z]{2}-APOLLO-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Prefer the verb-linked amount so we never capture the balance.
        val verbPattern = Regex(
            """(?:credited|debited)\s+with\s+ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        verbPattern.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        // Fallback: first ETB amount in the message.
        val firstEtbPattern = Regex("""ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        firstEtbPattern.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Credits carry a counterparty: "credited with ETB 550.00 by <NAME>."
        val byPattern = Regex(
            """credited\s+with\s+ETB\s*[0-9,]+(?:\.[0-9]{1,2})?\s+by\s+([^.]+?)\.""",
            RegexOption.IGNORE_CASE
        )
        byPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            if (merchant.isNotEmpty() && isValidMerchantName(merchant)) {
                return cleanMerchantName(merchant)
            }
        }

        return null
    }

    // Apollo consolidates to a single service-level wallet balance (see
    // [isMobileWallet]). The balance routing only kicks in when there is no
    // per-account number, so we deliberately do NOT extract the masked account —
    // otherwise the balance would land in a fabricated per-account row instead of
    // the wallet. (The masked "account 1*75" is the wallet's linked account, not a
    // separate tracked account.)
    override fun extractAccountLast4(message: String): String? = null

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """Available\s+Balance:\s*ETB\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    private fun parseAmount(raw: String): BigDecimal? {
        return try {
            BigDecimal(raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }
}
