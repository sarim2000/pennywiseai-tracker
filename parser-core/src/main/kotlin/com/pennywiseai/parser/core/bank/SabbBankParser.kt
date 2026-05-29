package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for SABB - Saudi Awwal Bank (Saudi Arabia) SMS messages.
 *
 * Handles Arabic transaction formats such as:
 *   شراء عبر نقاط البيع                    (POS purchase, e.g. Samsung Pay)
 *   شراء إنترنت                            (Online / internet purchase)
 *   حوالة صادرة مقبولة                     (Outgoing transfer)
 *   إيداع حوالة واردة                      (Incoming deposit / transfer)
 *   حوالة راتب                             (Salary transfer / credit)
 *
 * Common fields:
 *   بطاقة: ***1234;mada(Samsung Pay)       Card with last 4
 *   مبلغ: SAR 56.00  /  مبلغ: 126.28 SAR   Amount (either order)
 *   لدى: MERCHANT                          Purchase merchant
 *   من: SENDER / **NNNN                    From (sender for incoming, own a/c for outgoing)
 *   إلى: RECIPIENT / **NNNN                To (recipient for outgoing, own a/c for incoming)
 *   رسوم: SAR 0.57                         Fees on outgoing transfers
 *   في: 2026-05-06 20:02:46                Timestamp
 *
 * Sender example: SAB
 */
class SabbBankParser : BankParser() {

    override fun getBankName() = "SABB"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("[\\s\\-_]"), "")
        // Match sender variants like "SAB", "SABB", "SAB-Bank", "JD-SAB-S".
        // Avoid catching banks with "SAB" as a sub-token only when there is
        // additional bank-identifying context.
        if (normalized == "SAB" || normalized == "SABB") return true
        if (normalized.contains("SABBANK") || normalized.contains("SABB")) return true
        // DLT-style headers e.g. "JD-SAB-S", "VK-SAB-T"
        if (Regex("""(?:^|[^A-Z])SAB(?:[^A-Z]|$)""").containsMatchIn(sender.uppercase())) return true
        // Arabic name for SABB / Saudi Awwal Bank
        if (sender.contains("ساب") || sender.contains("الأول")) return true
        return false
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "مبلغ: SAR 56.00"
        val amountSarFirst = Regex(
            """مبلغ\s*:?\s*SAR\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountSarFirst.find(message)?.let { return parseSarAmount(it.groupValues[1]) }

        // Pattern 2: "مبلغ: 126.28 SAR"
        val amountSarLast = Regex(
            """مبلغ\s*:?\s*([0-9,]+(?:\.\d{1,2})?)\s*SAR""",
            RegexOption.IGNORE_CASE
        )
        amountSarLast.find(message)?.let { return parseSarAmount(it.groupValues[1]) }

        // Pattern 3: fallback "SAR 123.45"
        val looseSar = Regex(
            """SAR\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        looseSar.find(message)?.let { return parseSarAmount(it.groupValues[1]) }

        return null
    }

    private fun parseSarAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            // Incoming / deposit first so it wins over generic "حوالة"
            message.contains("حوالة راتب") -> TransactionType.INCOME   // salary credit
            message.contains("إيداع") -> TransactionType.INCOME
            message.contains("واردة") -> TransactionType.INCOME

            message.contains("صادرة") -> TransactionType.EXPENSE
            message.contains("شراء") -> TransactionType.EXPENSE
            message.contains("سحب") -> TransactionType.EXPENSE
            message.contains("خصم") -> TransactionType.EXPENSE
            message.contains("سداد") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Salary credit (حوالة راتب) has no merchant line; label it as "Salary".
        if (message.contains("حوالة راتب")) {
            return "Salary"
        }

        val isIncoming = message.contains("إيداع") || message.contains("واردة")
        val isOutgoingTransfer = message.contains("صادرة")

        // Purchases use "لدى:" (at / with) for the merchant.
        val ladaPattern = Regex("""لدى\s*:?\s*([^\n]+?)(?:\n|في\s*:|$)""")
        ladaPattern.find(message)?.let { match ->
            cleanSabbMerchant(match.groupValues[1])?.let { return it }
        }

        // Outgoing transfer: recipient is on "إلى:" line (Arabic "to").
        if (isOutgoingTransfer) {
            val toPattern = Regex("""إلى\s*:?\s*([^\n]+?)(?:\n|في\s*:|$)""")
            toPattern.find(message)?.let { match ->
                cleanSabbMerchant(match.groupValues[1])?.let { return it }
            }
        }

        // Incoming transfer: sender is on "من:" line.
        if (isIncoming) {
            val fromPattern = Regex("""من\s*:?\s*([^\n]+?)(?:\n|في\s*:|$)""")
            fromPattern.find(message)?.let { match ->
                cleanSabbMerchant(match.groupValues[1])?.let { return it }
            }
        }

        return null
    }

    /**
     * Cleans a captured field: trims, removes trailing masking chars (× and *),
     * runs base cleaning, and validates the result. Returns null if invalid
     * (e.g. purely masked account such as "**9999").
     */
    private fun cleanSabbMerchant(raw: String): String? {
        var value = raw.trim()
        // Strip trailing masking symbols and whitespace.
        value = value.trimEnd('×', '*', ' ', '\t')
        // Reject if what remains is just digits / mask chars (account number).
        if (value.isBlank()) return null
        if (value.all { it == '*' || it == '×' || it.isDigit() || it.isWhitespace() }) return null
        val cleaned = cleanMerchantName(value)
        return if (isValidMerchantName(cleaned)) cleaned else null
    }

    override fun extractAccountLast4(message: String): String? {
        // "بطاقة: ***1234;..." (card with last 4)
        val cardPattern = Regex("""بطاقة\s*:?\s*\*+\s*(\d{3,4})""")
        cardPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        // For transfers, own account often appears as "من: **9999" (outgoing)
        // or "إلى: **9999" (incoming). Capture those too.
        val ownAccountPatterns = listOf(
            Regex("""من\s*:?\s*\*+\s*(\d{3,4})"""),
            Regex("""إلى\s*:?\s*\*+\s*(\d{3,4})""")
        )
        for (pattern in ownAccountPatterns) {
            pattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "الرصيد: SAR 1234.56" or "الرصيد المتاح: SAR 1234.56"
        val balancePattern = Regex(
            """الرصيد(?:\s*المتاح)?\s*:?\s*SAR\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { return parseSarAmount(it.groupValues[1]) }
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        if (message.contains("بطاقة") ||           // card
            message.contains("مدى") ||             // Mada
            message.contains("نقاط البيع") ||      // POS in Arabic
            message.contains("SamsungPay", ignoreCase = true) ||
            message.contains("Samsung Pay", ignoreCase = true) ||
            message.contains("ApplePay", ignoreCase = true) ||
            message.contains("Apple Pay", ignoreCase = true)
        ) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (message.contains("رمز") || message.contains("OTP", ignoreCase = true) ||
            message.contains("كلمة المرور")
        ) {
            return false
        }

        val keywords = listOf(
            "شراء",      // purchase
            "سحب",       // withdrawal
            "حوالة",     // transfer
            "إيداع",     // deposit
            "خصم",       // deduction
            "سداد",      // bill payment
            "SAR"
        )
        return keywords.any { message.contains(it) }
    }
}
