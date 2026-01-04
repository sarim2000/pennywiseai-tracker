package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Mashreq Bank - UAE
 * Handles AED currency and Mashreq NEO card transactions
 *
 * Example SMS format:
 * "Thank you for using NEO VISA Debit Card Card ending XXXX for AED 5.99 at CARREFOUR on 26-AUG-2025 10:25 PM. Available Balance is AED X,480.15"
 */
class MashreqBankParser : UAEBankParser() {

    override fun getBankName() = "Mashreq Bank"

    // Currency defaults to AED in UAEBankParser

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

        // Extract currency from message or use default AED
        val currency = extractCurrency(smsBody) ?: "AED"

        // Extract available limit for credit card transactions
        val availableLimit = if (type == TransactionType.CREDIT) {
            val limit = extractAvailableLimit(smsBody)
            limit
        } else {
            null
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            creditLimit = availableLimit,
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
        return upperSender == "MASHREQ" ||
                upperSender.contains("MASHREQ") ||
                upperSender == "MSHREQ" ||
                // DLT patterns for UAE
                upperSender.matches(Regex("^[A-Z]{2}-MASHREQ-[A-Z]$")) ||
                upperSender.matches(Regex("^[A-Z]{2}-MSHREQ-[A-Z]$"))
    }
    
    // extractAmount handled by UAEBankParser

    override fun extractMerchant(message: String, sender: String): String? {
        // Mashreq debit card purchase pattern: "at CARREFOUR on"
        if (message.contains("debit card", ignoreCase = true) ||
            message.contains("credit card", ignoreCase = true)
        ) {

            // Pattern: "at MERCHANT on DATE"
            val merchantPattern =
                Regex("""at\s+([^,\n]+?)\s+on\s+\d{1,2}-[A-Z]{3}-\d{4}""", RegexOption.IGNORE_CASE)
            merchantPattern.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        // ATM withdrawal pattern
        if (message.contains("atm", ignoreCase = true) &&
            message.contains("withdrawn", ignoreCase = true)
        ) {
            return "ATM Withdrawal"
        }

        // Transfer pattern
        if (message.contains("transfer", ignoreCase = true)) {
            return "Transfer"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Mashreq patterns for card/account extraction
        val patterns = listOf(
            // "Card ending XXXX" or "Card ending 1234"
            Regex("""Card ending\s+([X\d]{4})""", RegexOption.IGNORE_CASE),

            // "card no. XXXX" or "card number XXXX"
            Regex("""card\s+(?:no\.|number)\s+([X\d]{4})""", RegexOption.IGNORE_CASE),

            // Generic account pattern
            Regex("""account\s+(?:no\.|number)?\s*([X\d]{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val accountStr = match.groupValues[1].replace("X", "")
                // Only return if we have actual digits (not all Xs)
                if (accountStr.any { it.isDigit() }) {
                    return accountStr
                }
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Mashreq balance patterns
        // Note: Mashreq uses 'X' for masking thousands, e.g., "AED X,480.15"
        val balancePatterns = listOf(
            // "Available Balance is AED X,480.15" or "Available Balance is AED 1,480.15"
            Regex(
                """Available Balance is\s+([A-Z]{3})\s+([X0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),

            // "Avl. Bal. AED X,480.15"
            Regex(
                """Avl\.?\s*Bal\.?\s+([A-Z]{3})\s+([X0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),

            // "Balance: AED X,480.15"
            Regex("""Balance:?\s+([A-Z]{3})\s+([X0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                var balanceStr = match.groupValues[2].replace(",", "")

                // Handle 'X' masking - replace X with 0 for parsing
                // "X480.15" becomes "0480.15" which is valid
                balanceStr = balanceStr.replace("X", "0", ignoreCase = true)

                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Mashreq date/time patterns
        val referencePatterns = listOf(
            // "on 26-AUG-2025 10:25 PM" - Mashreq's standard format
            Regex(
                """on\s+(\d{1,2}-[A-Z]{3}-\d{4}\s+\d{1,2}:\d{2}\s+[AP]M)""",
                RegexOption.IGNORE_CASE
            ),

            // Fallback: any date-time pattern
            Regex("""(\d{1,2}-[A-Z]{3}-\d{4}\s+\d{1,2}:\d{2}\s+[AP]M)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in referencePatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Debit card purchases are expenses (any currency)
            lowerMessage.contains("debit card") &&
                    Regex("""for\s+[A-Z]{3}\s+[0-9,]+""", RegexOption.IGNORE_CASE).containsMatchIn(
                        message
                    ) -> TransactionType.EXPENSE

            // Credit card purchases are credit transactions (any currency)
            lowerMessage.contains("credit card") &&
                    Regex("""for\s+[A-Z]{3}\s+[0-9,]+""", RegexOption.IGNORE_CASE).containsMatchIn(
                        message
                    ) -> TransactionType.CREDIT

            // ATM withdrawals are expenses
            lowerMessage.contains("atm") && lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE

            // ATM deposits are income
            lowerMessage.contains("atm") && lowerMessage.contains("deposited") -> TransactionType.INCOME

            // Transfers
            lowerMessage.contains("transfer") -> TransactionType.TRANSFER

            // Credits are income
            lowerMessage.contains("credited") -> TransactionType.INCOME

            // Debits are expenses
            lowerMessage.contains("debited") -> TransactionType.EXPENSE

            // Fallback to base class logic
            else -> super.extractTransactionType(message)
        }
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Mashreq-specific card indicators
        val mashreqCardPatterns = listOf(
            "neo visa debit card",
            "neo debit card",
            "debit card card ending",
            "credit card card ending",
            "card ending",
            "mashreq card"
        )

        return mashreqCardPatterns.any { lowerMessage.contains(it) } ||
                super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip non-transaction messages specific to Mashreq
        val nonTransactionKeywords = listOf(
            "otp",
            "one time password",
            "verification code",
            "do not share",
            "activation",
            "has been blocked",
            "has been activated",
            "card request",
            "card application",
            "limit change",
            "pin change",
            "failed transaction",
            "transaction declined",
            "insufficient balance"
        )

        if (nonTransactionKeywords.any { lowerMessage.contains(it) }) {
            return false
        }

        // Mashreq-specific transaction indicators
        val mashreqTransactionKeywords = listOf(
            "thank you for using",
            "neo visa debit card",
            "neo debit card",
            "debit card card ending",
            "credit card card ending",
            "available balance is"
        )

        if (mashreqTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fallback to base class transaction detection
        return super.isTransactionMessage(message)
    }

    override fun extractCurrency(message: String): String? {
        // Extract currency from the transaction context
        val currencyPatterns = listOf(
            // "for AED 5.99"
            Regex("""for\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "of AED 5.99"
            Regex("""of\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // Generic pattern
            Regex("""\b([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in currencyPatterns) {
            pattern.find(message)?.let { match ->
                val currencyCode = match.groupValues[1].uppercase()

                // Validate it's a 3-letter code (standard ISO currency format) but not month names
                if (currencyCode.matches(Regex("""[A-Z]{3}""")) &&
                    !currencyCode.matches(
                        Regex(
                            """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""",
                            RegexOption.IGNORE_CASE
                        )
                    )
                ) {
                    return currencyCode
                }
            }
        }

        // Default to AED for Mashreq (UAE Dirham)
        return "AED"
    }
}
