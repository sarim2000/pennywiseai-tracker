package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Al Rajhi Bank (Saudi Arabia) SMS messages
 *
 * Supported formats (Arabic):
 * - Purchase: "شراء ... بـSAR 5.75 لـMERCHANT"
 * - Online purchase: "شراء انترنت ... بـSAR 140 لـMERCHANT"
 * - ATM withdrawal: "سحب:صراف آلي ... مبلغ:SAR 100 مكان السحب:LOCATION"
 * - Outgoing local transfer: "حوالة محلية صادرة ... مبلغ:SAR 100 الى:RECIPIENT"
 * - Incoming local transfer: "حوالة محلية واردة ... مبلغ:SAR 7714.80 من:SENDER"
 * - Outgoing internal transfer: "حوالة داخلية صادرة ... بـSAR 200"
 * - Incoming internal transfer: "حوالة داخلية واردة ... بـSAR 1170"
 * - Loan installment: "خصم: قسط تمويل ... القسط: 2304.58 SAR"
 * - Bill payment: "سداد فاتورة"
 *
 * Sender: AlRajhiBank
 */
class AlRajhiBankParser : BankParser() {

    override fun getBankName() = "Al Rajhi Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("ALRAJHI") ||
                normalized.contains("RAJHI") ||
                sender.contains("الراجحي")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "بـSAR 5.75" or "بـSAR 140"
        val bPattern = Regex(
            """بـSAR\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        bPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        // Pattern 2: "مبلغ:SAR 100" or "مبلغ: SAR 100"
        val amountPattern = Regex(
            """مبلغ:\s*SAR\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        // Pattern 3: "القسط: 2304.58 SAR" (loan installment)
        val installmentPattern = Regex(
            """القسط:\s*([0-9,]+(?:\.\d{1,2})?)\s*SAR""",
            RegexOption.IGNORE_CASE
        )
        installmentPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

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
            // Incoming (واردة = incoming)
            message.contains("واردة") -> TransactionType.INCOME

            // Expense types
            message.contains("شراء") -> TransactionType.EXPENSE           // purchase
            message.contains("سحب") -> TransactionType.EXPENSE            // withdrawal
            message.contains("صادرة") -> TransactionType.EXPENSE          // outgoing
            message.contains("خصم") -> TransactionType.EXPENSE            // deduction
            message.contains("سداد") -> TransactionType.EXPENSE           // payment/settlement

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "لـMERCHANT" (to/for merchant) — stop at newline or date pattern
        val toPattern = Regex(
            """لـ([^\n*]+?)(?:\n|\d{2}/\d|$)"""
        )
        toPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            // Skip if it looks like an account number (all *s and digits)
            if (raw.all { it == '*' || it.isDigit() || it == ';' || it.isWhitespace() }) {
                // Not a merchant, skip
            } else {
                // If contains ";", take the part after it (name after account)
                val merchant = if (raw.contains(";")) {
                    cleanMerchantName(raw.substringAfter(";").trim())
                } else {
                    cleanMerchantName(raw)
                }
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 2: "الى:MERCHANT" (to: recipient for transfers)
        val toColonPattern = Regex(
            """الى:([^\n]+?)(?:\n|الى:|الرسوم:|$)"""
        )
        toColonPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            if (!raw.all { it == '*' || it.isDigit() }) {
                val merchant = cleanMerchantName(raw)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 3: "مكان السحب:LOCATION" (withdrawal location for ATM)
        val atmPattern = Regex(
            """مكان السحب:([^\n]+?)(?:\n|$)"""
        )
        atmPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "من:SENDER" for incoming transfers — extract who sent money
        val fromPattern = Regex(
            """من:([^\n*]+?)(?:\n|\d{2}/\d|$)"""
        )
        fromPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            if (raw.isNotBlank() && !raw.all { it == '*' || it.isDigit() }) {
                val merchant = cleanMerchantName(raw)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 5: "من****;NAME" for incoming internal transfers
        val fromInlinePattern = Regex(
            """من\*+;(.+?)(?:\n|\d{2}/\d|$)"""
        )
        fromInlinePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // ATM fallback
        if (message.contains("صراف آلي")) {
            return "ATM Withdrawal"
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "المبلغ المتبقي: SAR 13827.48" (remaining amount)
        val remainingPattern = Regex(
            """المبلغ المتبقي:\s*SAR\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        remainingPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        // مدى = Mada (Saudi debit card network)
        // بطاقة = card
        if (message.contains("مدى") || message.contains("بطاقة")) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        // Skip OTP / verification
        if (message.contains("رمز") || message.contains("OTP", ignoreCase = true) ||
            message.contains("كلمة المرور")
        ) {
            return false
        }

        val keywords = listOf(
            "شراء",      // purchase
            "سحب",       // withdrawal
            "حوالة",     // transfer
            "خصم",       // deduction
            "سداد",      // payment/settlement
            "SAR"        // currency marker
        )
        return keywords.any { message.contains(it) }
    }
}
