package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Base class for Thai bank parsers to share common logic.
 * Handles both Thai and English language transaction patterns with THB currency.
 */
abstract class BaseThailandBankParser : BankParser() {

    override fun getCurrency(): String = "THB"

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "1,250.00 THB" or "1,250.00 บาท"
        val patterns = listOf(
            Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:THB|บาท)"""),
            Regex("""(?:THB|฿)\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""),
            // Pattern 3: "1,250.00 USD" for international transactions
            Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*USD""")
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val cleanAmount = match.groupValues[1].replace(",", "")
                return try {
                    cleanAmount.toBigDecimal()
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }

        return when {
            // Credit card spending (check before expense — "ยอดใช้จ่าย" contains "ใช้จ่าย")
            lowerMessage.contains("credit card spending") ||
            lowerMessage.contains("ยอดใช้จ่ายต่างประเทศ") ||
            lowerMessage.contains("ยอดใช้จ่าย") -> TransactionType.CREDIT

            // Thai expense keywords
            lowerMessage.contains("เงินออก") ||
            lowerMessage.contains("ถอนเงิน") ||
            lowerMessage.contains("ถอนเงินสด") ||
            lowerMessage.contains("โอนเงินออก") ||
            lowerMessage.contains("โอนเงินผ่าน") ||
            lowerMessage.contains("ใช้จ่ายบัตร") ||
            lowerMessage.contains("ใช้จ่าย") ||
            // English expense keywords
            lowerMessage.contains("withdrawal") ||
            lowerMessage.contains("payment") ||
            lowerMessage.contains("you spent") ||
            lowerMessage.contains("transfer out") ||
            lowerMessage.contains("card payment") ||
            lowerMessage.contains("card transaction") ||
            lowerMessage.contains("atm withdrawal") -> TransactionType.EXPENSE

            // Thai income keywords
            lowerMessage.contains("เงินเข้า") ||
            lowerMessage.contains("เงินฝาก") ||
            lowerMessage.contains("รับเงิน") ||
            lowerMessage.contains("โอนเงินเข้า") ||
            lowerMessage.contains("รับเงินพร้อมเพย์") ||
            lowerMessage.contains("รับเงินโอน") ||
            lowerMessage.contains("เงินฝากเข้า") ||
            // English income keywords
            lowerMessage.contains("deposit") ||
            lowerMessage.contains("receive") ||
            lowerMessage.contains("transfer in") ||
            lowerMessage.contains("transfer received") -> TransactionType.INCOME

            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Thai: "คงเหลือ 15,820.45 บาท" or English: "Bal 15,820.45 THB"
        val patterns = listOf(
            Regex("""(?:Bal|คงเหลือ)\s+(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:THB|บาท)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Bal|คงเหลือ)\s+(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val cleanAmount = match.groupValues[1].replace(",", "")
                return try {
                    cleanAmount.toBigDecimal()
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "A/C xNNNN" or "บช xNNNN"
        val pattern = Regex("""(?:A/C|บช)\s*x(\d{4})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1]
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Thai: "ร้าน MERCHANT" or English: "at MERCHANT"
        val patterns = listOf(
            Regex("""(?:at|ร้าน)\s+([A-Za-z0-9\s&._-]+?)(?:\s+(?:A/C|บช|Bal|คงเหลือ|Available|on|$))""", RegexOption.IGNORE_CASE),
            Regex("""(?:at|ร้าน)\s+([A-Za-z0-9\s&._-]+)$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        return null
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        val pattern = Regex("""(?:Available limit|วงเงินคงเหลือ)\s+(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:THB|บาท)""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            val cleanAmount = match.groupValues[1].replace(",", "")
            return try {
                cleanAmount.toBigDecimal()
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    fun isCreditCardMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val cardKeywords = listOf(
            "credit card", "บัตรเครดิต",
            "card spending", "card payment", "card transaction",
            "ใช้จ่ายบัตร", "ยอดใช้จ่าย"
        )
        return cardKeywords.any { lowerMessage.contains(it) }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("รหัส") ||
            lowerMessage.contains("ยืนยัน")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("สมัคร") ||
            lowerMessage.contains("โปรโมชั่น") ||
            lowerMessage.contains("promotion") ||
            lowerMessage.contains("cashback offer")
        ) {
            return false
        }

        val transactionKeywords = listOf(
            // Thai
            "เงินเข้า", "เงินออก", "ถอนเงิน", "โอนเงิน", "ใช้จ่าย",
            "เงินฝาก", "รับเงิน", "คงเหลือ", "บาท", "ยอดใช้จ่าย",
            // English
            "withdrawal", "deposit", "transfer", "payment", "spent",
            "receive", "bal", "thb", "card transaction", "card payment",
            "credit card spending", "available limit"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant.trim()
    }

    override fun isValidMerchantName(name: String): Boolean {
        val commonWords = setOf(
            "USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE",
            "ผ่าน", "โดย", "จาก", "ที่", "ไปยัง", "ถึง"
        )

        return name.length >= 2 &&
                name.any { it.isLetter() } &&
                name.uppercase() !in commonWords &&
                !name.all { it.isDigit() } &&
                !name.contains("@")
    }
}
