package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.util.Currency

/**
 * Parser for Charles Schwab Bank - handles USD debit card and ATM transactions
 */
class CharlesSchwabParser : BankParser() {

    override fun getBankName() = "Charles Schwab"

    override fun getCurrency() = "USD"  // Default currency

    override fun extractCurrency(message: String): String? {
        // Common currency symbol to currency code mapping
        val symbolToCurrencyMap = mapOf(
            "€" to "EUR", "£" to "GBP", "₹" to "INR", "¥" to "JPY",
            "฿" to "THB", "₩" to "KRW", "$" to "USD", "C$" to "CAD",
            "A$" to "AUD", "S$" to "SGD", "ብር" to "ETB"
        )

        // Check for currency symbols in the message
        for ((symbol, currencyCode) in symbolToCurrencyMap) {
            if (message.contains(symbol)) {
                return try {
                    Currency.getInstance(currencyCode).currencyCode
                } catch (e: IllegalArgumentException) {
                    null // Invalid currency code, continue
                }
            }
        }

        // Extract and validate 3-letter currency codes from pattern like "A USD 25.50"
        val currencyCodePattern = Regex("""A\s+([A-Z]{3})\s*[0-9,]+""")
        currencyCodePattern.find(message)?.let { match ->
            val currencyCode = match.groupValues[1]
            return try {
                Currency.getInstance(currencyCode).currencyCode
            } catch (e: IllegalArgumentException) {
                null // Invalid currency code, continue
            }
        }

        // Check for any 3-letter currency codes in the message
        val allCurrencyCodesPattern = Regex("""\b([A-Z]{3})\b""")
        allCurrencyCodesPattern.findAll(message).forEach { match ->
            val currencyCode = match.groupValues[1]
            try {
                Currency.getInstance(currencyCode)
                return currencyCode
            } catch (e: IllegalArgumentException) {
                // Not a valid currency code, continue searching
            }
        }

        return super.extractCurrency(message) ?: "USD"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Skip non-transaction messages
        if (!isTransactionMessage(smsBody)) {
            return null
        }

        val amount = extractAmount(smsBody)
        if (amount == null) {
            return null
        }

        val type = extractTransactionType(smsBody)
        if (type == null) {
            return null
        }

        // Extract available limit for credit card transactions
        val availableLimit = if (type == TransactionType.CREDIT) {
            val limit = extractAvailableLimit(smsBody)
            limit
        } else {
            null
        }

        // Use dynamic currency detection for Charles Schwab
        val currency = extractCurrency(smsBody) ?: getCurrency()

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            creditLimit = availableLimit,  // TODO: This is actually available limit, will be fixed in SmsReaderWorker
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = detectIsCard(smsBody),
            currency = currency
        )
    }

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "SCHWAB" ||
                upperSender.contains("CHARLES SCHWAB") ||
                upperSender.contains("SCHWAB BANK") ||
                upperSender == "24465" ||  // Typical DLT sender ID
                upperSender.matches(Regex("""^[A-Z]{2}-SCHWAB-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Charles Schwab patterns: "A $7.44 debit card transaction", "A $10.00 debit card transaction", "A $22.07 ACH was debited"
        // Multi-currency support: "A €25.50 debit card transaction", "A £15.75 ATM transaction", "A ฿500.00 ATM transaction"
        val patterns = listOf(
            Regex(
                """A\s+\$([0-9,]+(?:\.[0-9]{2})?)\s+debit card transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""A\s+\$([0-9,]+(?:\.[0-9]{2})?)\s+ATM transaction""", RegexOption.IGNORE_CASE),
            Regex(
                """A\s+\$([0-9,]+(?:\.[0-9]{2})?)\s+ACH\s+transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+\$([0-9,]+(?:\.[0-9]{2})?)\s+ACH\s+was debited""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+\$([0-9,]+(?:\.[0-9]{2})?)\s+(?:debit card|ATM)\s+transaction""",
                RegexOption.IGNORE_CASE
            ),
            // Multi-currency patterns with symbols before amount
            Regex(
                """A\s+([€£₹¥฿₩ብር])\s*([0-9,]+(?:\.[0-9]{2})?)\s+debit card transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([€£₹¥฿₩ብር])\s*([0-9,]+(?:\.[0-9]{2})?)\s+ATM transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([€£₹¥฿₩ብር])\s*([0-9,]+(?:\.[0-9]{2})?)\s+ACH\s+transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([€£₹¥฿₩ብር])\s*([0-9,]+(?:\.[0-9]{2})?)\s+ACH\s+was debited""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([€£₹¥฿₩ብር])\s*([0-9,]+(?:\.[0-9]{2})?)\s+(?:debit card|ATM)\s+transaction""",
                RegexOption.IGNORE_CASE
            ),
            // Generic currency code patterns
            Regex(
                """A\s+([A-Z]{3})\s*([0-9,]+(?:\.[0-9]{2})?)\s+debit card transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([A-Z]{3})\s*([0-9,]+(?:\.[0-9]{2})?)\s+ATM transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([A-Z]{3})\s*([0-9,]+(?:\.[0-9]{2})?)\s+ACH\s+transaction""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """A\s+([A-Z]{3})\s*([0-9,]+(?:\.[0-9]{2})?)\s+ACH\s+was debited""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = if (match.groupValues.size > 2) {
                    // Multi-currency pattern with currency symbol/code
                    match.groupValues[2].replace(",", "")
                } else {
                    // USD pattern with $ symbol
                    match.groupValues[1].replace(",", "")
                }
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // All Charles Schwab transactions are debits from account (expenses)
            lowerMessage.contains("debit card transaction") -> TransactionType.EXPENSE
            lowerMessage.contains("atm transaction") -> TransactionType.EXPENSE
            lowerMessage.contains("ach transaction") -> TransactionType.EXPENSE
            lowerMessage.contains("ach was debited") -> TransactionType.EXPENSE
            lowerMessage.contains("was debited") -> TransactionType.EXPENSE
            lowerMessage.contains("transaction was debited") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "account ending xxx" where xxx are last 4 digits
        val patterns = listOf(
            Regex("""account ending (\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""account.*ending (\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""from account ending (\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip STOP messages
        if (lowerMessage.contains("reply stop to end")) {
            // But still process if it has transaction info
            if (!lowerMessage.contains("transaction") && !lowerMessage.contains("debited")) {
                return false
            }
        }

        // Charles Schwab specific transaction keywords
        val schwabTransactionKeywords = listOf(
            "debit card transaction was debited",
            "atm transaction was debited",
            "ach was debited",
            "transaction was debited from account"
        )

        if (schwabTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Charles Schwab sends specific alerts for debit card transactions
        return when {
            lowerMessage.contains("debit card transaction") -> true
            lowerMessage.contains("atm transaction") -> true  // ATM transactions are card-based
            lowerMessage.contains("ach transaction") -> false // ACH transactions are not card-based
            else -> super.detectIsCard(message)
        }
    }
}