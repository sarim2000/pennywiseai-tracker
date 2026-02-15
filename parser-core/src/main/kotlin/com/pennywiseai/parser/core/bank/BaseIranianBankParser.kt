package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Base class for Iranian bank parsers to share common logic.
 * Handles common Persian language transaction patterns and IRR currency.
 */
abstract class BaseIranianBankParser : BankParser() {

    override fun getCurrency(): String = "IRR"

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "مبلغ 1,500,000 ریال" or "مبلغ 1,500,000 تومان"
        val patterns = listOf(
            Regex("""مبلغ\s*(\d{1,3}(?:,\d{3})*|\d+)\s*(?:ریال|تومان)"""),
            // Pattern 2: amount followed directly by keyword
            Regex("""(\d{1,3}(?:,\d{3})*|\d+)\s*(?:ریال|تومان)""")
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val cleanAmount = match.groupValues[1].replace(",", "")
                return try {
                    val amountValue = cleanAmount.toBigDecimal()
                    if (amountValue >= BigDecimal.valueOf(1000)) {
                        amountValue
                    } else {
                        null
                    }
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
            lowerMessage.contains("برداشت") ||
            lowerMessage.contains("پرداخت") ||
            lowerMessage.contains("خرید") ||
            lowerMessage.contains("انتقال") ||
            lowerMessage.contains("مصرف") -> TransactionType.EXPENSE

            lowerMessage.contains("واریز") ||
            (lowerMessage.contains("credited") && !lowerMessage.contains("block")) -> TransactionType.INCOME

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val cardPattern = Regex("""(\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4})""")
        cardPattern.find(message)?.let { match ->
            return "Card ${match.groupValues[1]}"
        }

        return null
    }

    override fun extractReference(message: String): String? {
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        val cardPattern = Regex("""\d{4}[-\s]?(\d{4})""")
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex("""مانده\s*:?\s*(\d{1,3}(?:,\d{3})*)""")
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val cardKeywords = listOf(
            "کارت", "card", "debit card", "credit card", "کارت بدهی", "کارت اعتباری"
        )

        return cardKeywords.any { lowerMessage.contains(it) }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("رمز یکبار مصرف") ||
            lowerMessage.contains("کد تایید")
        ) {
            return false
        }

        if (lowerMessage.contains("تبلیغ") ||
            lowerMessage.contains("پیشنهاد") ||
            lowerMessage.contains("تخفیف") ||
            lowerMessage.contains("cashback offer")
        ) {
            return false
        }

        if (lowerMessage.contains("درخواست") && lowerMessage.contains("پرداخت")) {
            return false
        }

        val transactionKeywords = listOf(
            "مبلغ", "ریال", "تومان", "IRR", "TOMAN",
            "برداشت", "واریز", "پرداخت", "خرید", "انتقال",
            "debit", "credit", "spent", "received", "transferred", "paid"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant.trim()
    }

    override fun isValidMerchantName(name: String): Boolean {
        val commonWords = setOf(
            "USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE",
            "استفاده", "از", "توسط", "از طریق", "برای", "به", "از", "در", "و", "با"
        )

        return name.length >= 2 &&
                name.any { it.isLetter() } &&
                name.uppercase() !in commonWords &&
                !name.all { it.isDigit() } &&
                !name.contains("@")
    }
}
