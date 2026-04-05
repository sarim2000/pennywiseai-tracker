package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Bank Muscat (Oman) SMS messages
 *
 * Supported formats (Arabic):
 * - Debit: "تم خصم OMR 0.650 من حسابك رقم XXXXX بإستخدام بطاقة الخصم المباشر في MERCHANT بتاريخ DATE. رصيدك الحالي هو BALANCE OMR."
 * - Credit: "تم إيداع OMR X.XXX في حسابك رقم XXXXX بتاريخ DATE. رصيدك الحالي هو BALANCE OMR."
 *
 * Currency: OMR (Omani Rial)
 * Sender: BankMuscat, BKMUSCAT, bank muscat
 */
class BankMuscatParser : BankParser() {

    override fun getBankName() = "Bank Muscat"

    override fun getCurrency() = "OMR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("MUSCAT") ||
                normalized.contains("BKMUSCAT") ||
                normalized.contains("BANKMUSCAT") ||
                normalized.contains("BK MUSCAT") ||
                sender.contains("بنك مسقط")
    }

    override fun isTransactionMessage(message: String): Boolean {
        // Arabic debit/credit keywords
        return message.contains("تم خصم") ||        // deducted
                message.contains("تم إيداع") ||      // deposited
                message.contains("تم تحويل") ||      // transferred
                message.contains("تم سداد")          // payment made
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "تم خصم OMR 0.650" or "OMR 0.100" (currency before amount)
        val omrBeforePattern = Regex(
            """OMR\s+([\d,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )

        // Pattern 2: "0.650 OMR" (amount before currency)
        val omrAfterPattern = Regex(
            """([\d,]+(?:\.\d+)?)\s+OMR""",
            RegexOption.IGNORE_CASE
        )

        // Try currency-before-amount first (more common in the screenshots)
        omrBeforePattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        omrAfterPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            message.contains("تم خصم") -> TransactionType.EXPENSE          // deducted
            message.contains("تم إيداع") -> TransactionType.INCOME         // deposited
            message.contains("تم تحويل") -> TransactionType.TRANSFER       // transferred
            message.contains("تم سداد") -> TransactionType.EXPENSE         // payment made
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Merchant is between "في" (at) and "بتاريخ" (on date)
        val merchantPattern = Regex(
            """في\s+(.+?)\s+بتاريخ""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            // Clean up merchant: remove leading/trailing ID numbers like "757487-" or "-650068"
            val cleaned = raw
                .replace(Regex("""^\d{4,}-"""), "")      // leading "757487-"
                .replace(Regex("""-\d{4,}$"""), "")      // trailing "-650068"
                .replace(Regex("""-\d{4,}\s"""), " ")    // middle "-757487 "
                .trim()
            if (cleaned.isNotEmpty()) {
                return cleanMerchantName(cleaned)
            }
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "حسابك رقم XXXXXXXX1234" or "حسابك رقم XXXXX"
        val accountPattern = Regex(
            """حسابك رقم\s+([X*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "رصيدك الحالي هو 9999.740 OMR" (your current balance is X OMR)
        val balancePattern = Regex(
            """رصيدك الحالي هو\s+([\d,]+(?:\.\d+)?)\s*OMR""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Also try "OMR amount" format for balance
        val balancePattern2 = Regex(
            """رصيدك الحالي هو\s+OMR\s*([\d,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern2.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        return message.contains("بطاقة الخصم المباشر") ||  // debit card
                message.contains("بطاقة الائتمان") ||       // credit card
                message.contains("بطاقة")                    // card (generic)
    }
}
