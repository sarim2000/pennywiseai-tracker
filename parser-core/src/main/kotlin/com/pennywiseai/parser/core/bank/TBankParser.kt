package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for T-Bank (formerly Tinkoff) SMS messages (Russia)
 *
 * Supported formats:
 * - Deposit: "Пополнение, счет RUB. 5000 ₽. Банкомат. Доступно 10028,05 ₽"
 * - Purchase: "Покупка, счет карты *1023. 3267 ₽. AZS 09117. Доступно 30672,14 ₽"
 * - Transfer: "Перевод. Счет RUB. 250 ₽. Милана Н. Баланс 0 ₽"
 *
 * Notes:
 * - Russian uses comma as decimal separator (10028,05)
 * - Currency symbol: ₽ (Russian Ruble)
 */
class TBankParser : BankParser() {

    override fun getBankName() = "T-Bank"

    override fun getCurrency() = "RUB"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("TBANK") ||
                normalized.contains("T-BANK") ||
                normalized.contains("TINKOFF")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "5000 ₽" or "10028,05 ₽" (amount before ₽, but NOT after Доступно/Баланс)
        // We need the FIRST amount that appears before the balance
        val amountPattern = Regex(
            """(?:^|[.\s])(\d[\d\s]*(?:,\d{1,2})?)\s*₽""",
            RegexOption.IGNORE_CASE
        )
        // Find all matches, take the first one (transaction amount, not balance)
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1]
                .replace(" ", "")
                .replace(",", ".")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()

        return when {
            // Income
            lower.contains("пополнение") -> TransactionType.INCOME     // deposit/top-up
            lower.contains("зачисление") -> TransactionType.INCOME     // crediting
            lower.contains("возврат") -> TransactionType.INCOME        // refund
            lower.contains("кэшбэк") -> TransactionType.INCOME        // cashback
            lower.contains("входящий перевод") -> TransactionType.INCOME // incoming transfer

            // Expense
            lower.contains("покупка") -> TransactionType.EXPENSE       // purchase
            lower.contains("списание") -> TransactionType.EXPENSE      // charge/debit
            lower.contains("снятие") -> TransactionType.EXPENSE        // withdrawal
            lower.contains("перевод") -> TransactionType.EXPENSE       // transfer (outgoing by default)
            lower.contains("оплата") -> TransactionType.EXPENSE        // payment
            lower.contains("платёж") -> TransactionType.EXPENSE        // payment
            lower.contains("платеж") -> TransactionType.EXPENSE        // payment (without ё)

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // T-Bank format: "TYPE, ACCOUNT. AMOUNT ₽. MERCHANT. BALANCE ₽"
        // The merchant is between the amount (₽.) and the balance keyword (Доступно/Баланс)
        val merchantPattern = Regex(
            """₽\.\s+(.+?)\.\s+(?:Доступно|Баланс)""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Fallback: merchant after amount ₽. and before the end or next period
        val fallbackPattern = Regex(
            """₽\.\s+([^.]+)""",
            RegexOption.IGNORE_CASE
        )
        fallbackPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant) &&
                !merchant.lowercase().let { it.startsWith("доступно") || it.startsWith("баланс") }
            ) {
                return merchant
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // Pattern: "счет карты *1023" or "карты *1023"
        val cardPattern = Regex(
            """\*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        // "счет карты" = card account, "карта" = card
        if (lower.contains("карты") || lower.contains("карта")) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Доступно 10028,05 ₽" or "Баланс 0 ₽"
        val balancePattern = Regex(
            """(?:Доступно|Баланс)\s+(\d[\d\s]*(?:,\d{1,2})?)\s*₽""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1]
                .replace(" ", "")
                .replace(",", ".")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Skip OTP / verification messages
        if (lower.contains("код") || lower.contains("пароль") || lower.contains("otp")) {
            return false
        }

        // Must contain ₽ (ruble sign) and a transaction keyword
        if (!message.contains("₽")) return false

        val keywords = listOf(
            "пополнение", "покупка", "перевод", "списание",
            "снятие", "оплата", "платёж", "платеж",
            "возврат", "зачисление", "кэшбэк"
        )
        return keywords.any { lower.contains(it) }
    }
}
