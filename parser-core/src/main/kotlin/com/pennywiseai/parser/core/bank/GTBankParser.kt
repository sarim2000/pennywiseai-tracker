package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Guaranty Trust Bank / GTBank (Nigeria) SMS messages.
 *
 * Supported format (line-based, DR/CR suffix on the amount):
 * ```
 * Acct:******4321
 * Amt:NGN15,000.00 DR
 * Desc:OUTWARD TRANSFER TO OPAY - JANE DOE
 * Bal:NGN20,000.00
 * Date:2026-01-15 9:36AM
 * ```
 * Direction comes from the DR (EXPENSE) / CR (INCOME) suffix on the Amt line,
 * unlike Access Bank which puts Debit/Credit on its own first line.
 *
 * Note the label differences from [AccessBankParser]: "Acct:" not "Acc:",
 * and "Bal:" not "Avail Bal:".
 *
 * Sender: GTBank
 */
class GTBankParser : BankParser() {

    override fun getBankName() = "GTBank"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("GTBANK") ||
                upper.contains("GTB") ||
                upper.contains("GUARANTY")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        // Must look like the GTBank line-based alert: an Amt line carrying a DR/CR suffix.
        return Regex("""(?i)Amt:\s*NGN\s*[0-9,]+(?:\.\d{1,2})?\s*(DR|CR)\b""")
            .containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val match = Regex("""(?i)Amt:\s*NGN\s*[0-9,]+(?:\.\d{1,2})?\s*(DR|CR)\b""")
            .find(message) ?: return null
        return when (match.groupValues[1].uppercase()) {
            "DR" -> TransactionType.EXPENSE
            "CR" -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val match = Regex("""(?i)Amt:\s*NGN\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Bal:" only — anchored so it cannot also match a future "Avail Bal:" variant twice.
        val match = Regex("""(?i)\bBal:\s*NGN\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Use [ \t]* (not \s*) so an empty "Desc:" line can't let the capture cross the
        // newline and grab the following Bal:/Date: line as the merchant.
        val match = Regex("""(?i)Desc:[ \t]*(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        return desc.ifBlank { null }
    }

    override fun extractAccountLast4(message: String): String? {
        // Account is fully masked, e.g. "******4321" — take the trailing digit group.
        val match = Regex("""(?i)Acct:\s*\**([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(match.groupValues[1])
    }
}
