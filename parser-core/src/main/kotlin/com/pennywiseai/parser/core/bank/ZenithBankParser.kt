package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Zenith Bank (Nigeria) SMS messages.
 *
 * Supported format (line-based):
 * ```
 * Acct:421****577
 * DT:09/06/2026 03:23:17 PM
 * <description>
 * DR Amt:650.00          (debit -> EXPENSE)  or  CR Amt:40,000.00 (credit -> INCOME)
 * Bal:289.69
 * Dial *966# for quick airtime/Data purchase   (promo — ignored)
 * ```
 * Amounts have no NGN prefix.
 *
 * Sender: ZENITHBANK
 */
class ZenithBankParser : BankParser() {

    override fun getBankName() = "Zenith Bank"

    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("ZENITH")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) {
            return false
        }
        return Regex("""(?i)(?:DR|CR)\s*Amt:""").containsMatchIn(message) &&
                Regex("""(?i)Acct:""").containsMatchIn(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            Regex("""(?i)DR\s*Amt:""").containsMatchIn(message) -> TransactionType.EXPENSE
            Regex("""(?i)CR\s*Amt:""").containsMatchIn(message) -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val match = Regex("""(?i)(?:DR|CR)\s*Amt:\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val match = Regex("""(?i)Bal:\s*([0-9,]+(?:\.\d{1,2})?)""").find(message) ?: return null
        return try {
            BigDecimal(match.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Narration sits on the line between the DT: timestamp and the amount line.
        // When a message has no narration line, this regex would otherwise capture the
        // amount/balance/promo line — guard against persisting those as the merchant.
        val match = Regex("""(?im)^DT:.*\n(.+)""").find(message) ?: return null
        val desc = match.groupValues[1].trim()
        if (desc.isBlank()) return null
        val isNonNarration = Regex("""(?i)^((DR|CR)\s*Amt:|Bal:|Dial\b)""").containsMatchIn(desc)
        return if (isNonNarration) null else desc
    }

    override fun extractAccountLast4(message: String): String? {
        val match = Regex("""(?i)Acct:\s*[0-9]*\*+([0-9]+)""").find(message)
        if (match != null) {
            return extractLast4Digits(match.groupValues[1])
        }
        val plain = Regex("""(?i)Acct:\s*([0-9]+)""").find(message) ?: return null
        return extractLast4Digits(plain.groupValues[1])
    }
}
