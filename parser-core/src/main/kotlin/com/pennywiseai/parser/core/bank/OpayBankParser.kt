package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Opay (Nigeria) SMS messages.
 *
 * Supported format (sentence-style, NO account number and NO balance — Opay omits both):
 * ```
 * Dear OPay user, N2,300.00 has been debited for Card Payment via POS on 14-May-2026 19:28.
 * Dear OPay user, N150.00 has been credited ... (best-effort credit handling)
 * ```
 * Amounts carry an "N" prefix. Merchant/description is the "<purpose> via <channel>"
 * text captured between "debited/credited for " and " on ".
 *
 * Sender: Opay
 */
class OpayBankParser : BankParser() {

    override fun getBankName() = "Opay"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("OPAY")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        return Regex("""(?i)Dear\s+OPay\s+user""").containsMatchIn(message) &&
                Regex("""(?i)has\s+been\s+(?:debited|credited)""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?i)has\s+been\s+debited""").containsMatchIn(message) -> TransactionType.EXPENSE
            Regex("""(?i)has\s+been\s+credited""").containsMatchIn(message) -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val match = Regex("""(?i)N\s*([0-9,]+(?:\.\d{1,2})?)\s+has\s+been""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Anchor the trailing " on " to the actual date (DD-Mon-YYYY) so a purpose that
        // itself contains " on " (e.g. "Payment on Account") isn't truncated — the lazy
        // capture backtracks past any earlier " on " that isn't followed by a date.
        val match = Regex(
            """(?i)has\s+been\s+(?:debited|credited)\s+for\s+(.+?)\s+on\s+\d{1,2}-[A-Za-z]{3}-\d{2,4}"""
        ).find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }
}
