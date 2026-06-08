package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for eMola (Mozambique) SMS messages (English).
 *
 * Amounts use US-style formatting (thousands separator ',', decimal '.'),
 * e.g. "3,123.45". Currency is written as "MT" which maps to MZN.
 * Dates are DD/MM/YYYY and times HH:MM:SS.
 *
 * Supported formats:
 * - Outgoing transfer (EXPENSE):
 *   "Transaction ID PP260530.0934.w91238. You transfered 100.00MT to 871234566, name: John Doe at 09:34:45 on 30/05/2026. Fee: 0.00MT. Your account balance is 3,123.45MT. ..."
 * - Incoming transfer (INCOME):
 *   "Transaction ID: PP260603.0854.K00983. You received 123.45MT from 871234567, name: JOHN DOE at 08:54:09 on 03/06/2026. Content: campo. Your account balance is 1,234.56MT. ..."
 * - Agent withdrawal (EXPENSE):
 *   "Transaction ID: CO260528.1806.W15992. You withdrew 50.00MT in the Agent with code ID 123456, Name JOHN DOE at 18:06:25 on 28/05/2026. Fee: 3.00 MT. Your account balance is 1,234.23MT. ..."
 *
 * Note: "transfered" is the spelling used in the app's SMS.
 */
class EMolaParser : BankParser() {

    override fun getBankName() = "eMola"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        return sender.equals("eMola", ignoreCase = true)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("you transfered") ||
                lower.contains("you received") ||
                lower.contains("you withdrew")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Amount follows the action verb, e.g. "transfered 100.00MT",
        // "received 123.45MT", "withdrew 50.00MT".
        val pattern = Regex(
            """(?:transfered|received|withdrew)\s+([0-9,]+\.[0-9]{2})\s*MT""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return parseUsAmount(match.groupValues[1])
        }
        return null
    }

    private fun parseUsAmount(raw: String): BigDecimal? {
        val normalized = raw.replace(",", "")
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("you transfered") -> TransactionType.EXPENSE
            lower.contains("you withdrew") -> TransactionType.EXPENSE
            lower.contains("you received") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Counterparty name after "name:" for transfers/receipts.
        // e.g. "to 871234566, name: John Doe at 09:34:45"
        val namePattern = Regex(
            """name:\s*([^,]+?)\s+at\s+\d{2}:\d{2}:\d{2}""",
            RegexOption.IGNORE_CASE
        )
        namePattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Agent withdrawal: "Name JOHN DOE at 18:06:25"
        val agentPattern = Regex(
            """Name\s+([^,]+?)\s+at\s+\d{2}:\d{2}:\d{2}""",
            RegexOption.IGNORE_CASE
        )
        agentPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Your account balance is 3,123.45MT."
        val pattern = Regex(
            """account\s+balance\s+is\s+([0-9,]+\.[0-9]{2})\s*MT""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return parseUsAmount(match.groupValues[1])
        }
        return null
    }

    override fun extractReference(message: String): String? {
        // "Transaction ID PP260530.0934.w91238." or "Transaction ID: PP260603.0854.K00983."
        val pattern = Regex(
            """Transaction\s+ID:?\s*([A-Za-z0-9.]+)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return match.groupValues[1].trim().trimEnd('.')
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? = null
}
