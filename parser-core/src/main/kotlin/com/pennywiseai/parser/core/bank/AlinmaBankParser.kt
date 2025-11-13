package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Alinma Bank (Saudi Arabia) SMS messages
 *
 * Handles Arabic text formats:
 * - "شراء محلي من نقاط البيع" = Local purchase from POS
 * - "شراء عبر" = Purchase via
 * - "بمبلغ" / "مبلغ" = Amount
 * - "الرصيد" = Balance
 * - "من" = From (merchant)
 * - Currency: SAR (Saudi Riyal) / ريال سعودى
 */
class AlinmaBankParser : BankParser() {

    override fun getBankName() = "Alinma Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("ALINMA") ||
                normalizedSender == "ALINMA" ||
                normalizedSender.contains("الإنماء") // Arabic name
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "بمبلغ: XX SAR" or "بمبلغ: 3 SAR"
        val amountSARPattern = Regex(
            """بمبلغ:\s*([0-9]+(?:\.[0-9]{2})?)\s*SAR""",
            RegexOption.IGNORE_CASE
        )
        amountSARPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1]
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "مبلغ: SAR XXX.XX"
        val amountPattern2 = Regex(
            """مبلغ:\s*SAR\s*([0-9]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern2.find(message)?.let { match ->
            val amountStr = match.groupValues[1]
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: "مبلغ: ريال سعودى XXX.XX"
        val amountArabicPattern = Regex(
            """مبلغ:\s*ريال سعودى\s*([0-9]+(?:\.[0-9]{2})?)"""
        )
        amountArabicPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1]
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // "شراء" means "purchase" in Arabic - always an expense
        if (message.contains("شراء") || message.contains("Purchase", ignoreCase = true)) {
            return TransactionType.EXPENSE
        }

        // For future: could add support for income/refunds
        // "إيداع" typically means deposit/credit
        if (message.contains("إيداع") || message.contains("Deposit", ignoreCase = true)) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "من: Establishment Name" (من = from)
        val fromPattern = Regex(
            """من:\s*([^\n]+?)(?:\n|في:)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "لدى: Commercial Self-Technolog" (لدى = at/with)
        val atPattern = Regex(
            """لدى:\s*([^\n]+?)(?:\n|في:)""",
            RegexOption.IGNORE_CASE
        )
        atPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Default for POS transactions
        if (message.contains("POS") || message.contains("نقاط البيع")) {
            return "POS Transaction"
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "حساب: **XXXX" or "حساب: **0000" (حساب = account)
        val accountPattern = Regex(
            """حساب:\s*\*+(\d{4})"""
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "حساب: *XXXX"
        val accountPattern2 = Regex(
            """حساب:\s*\*(\d{4})"""
        )
        accountPattern2.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: "البطاقة: **XXXX" (البطاقة = card)
        val cardPattern = Regex(
            """البطاقة:\s*\*+(\d{4})"""
        )
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 4: "البطاقة الائتمانية: **XXXX" (credit card)
        val creditCardPattern = Regex(
            """البطاقة الائتمانية:\s*\*+(\d{4})"""
        )
        creditCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 5: "بطاقة مدى: XXXX*" (Mada card - reversed format)
        val madaPattern = Regex(
            """بطاقة مدى:\s*(\d{4})\*"""
        )
        madaPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: "الرصيد: XXX.XX SAR" (الرصيد = balance)
        val balanceSARPattern = Regex(
            """الرصيد:\s*([0-9]+(?:\.[0-9]{2})?)\s*SAR""",
            RegexOption.IGNORE_CASE
        )
        balanceSARPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1]
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "الرصيد: XXX.XX ريال" (ريال = riyal)
        val balanceRiyalPattern = Regex(
            """الرصيد:\s*([0-9]+(?:\.[0-9]{2})?)\s*ريال"""
        )
        balanceRiyalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1]
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        // Skip OTP messages
        if (message.contains("OTP", ignoreCase = true) ||
            message.contains("رمز", ignoreCase = true) || // "رمز" = code
            message.contains("كلمة المرور")
        ) { // "كلمة المرور" = password
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "شراء",        // purchase
            "بمبلغ",       // amount
            "مبلغ",        // amount
            "الرصيد",      // balance
            "Purchase",    // English variant
            "POS"
        )

        return transactionKeywords.any { message.contains(it) }
    }

    override fun detectIsCard(message: String): Boolean {
        // Check for card-related keywords in Arabic
        return message.contains("البطاقة") ||          // card
                message.contains("بطاقة") ||            // card
                message.contains("البطاقة الائتمانية") || // credit card
                message.contains("بطاقة مدى") ||        // Mada card
                message.contains("POS") ||
                message.contains("نقاط البيع")          // POS in Arabic
    }
}
