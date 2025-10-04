package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Abu Dhabi Commercial Bank (ADCB) - UAE's largest bank by assets
 * Inherits from FABParser since ADCB follows similar UAE banking patterns
 * Handles AED currency and multi-currency international transactions
 */
class ADCBParser : FABParser() {

    override fun getBankName() = "Abu Dhabi Commercial Bank"

    override fun getCurrency() = "AED"  // UAE Dirham (default)

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "ADCBALERT" ||
                upperSender.contains("ADCB") ||
                upperSender.contains("ADCBANK") ||
                // DLT patterns for UAE
                upperSender.matches(Regex("^[A-Z]{2}-ADCB-[A-Z]$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Try patterns in order of specificity - most transaction-specific first
        val patterns = listOf(
            // 1. "was used for CURRENCYamount" - debit card usage (handle both spaced and non-spaced)
            Regex("""was used for\s+([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // 2. "used for CURRENCYamount" - debit card usage (shorter version)
            Regex("""used for\s+([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // 3. "CURRENCYamount withdrawn from" - ATM withdrawals
            Regex("""\b([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)\s+withdrawn from""", RegexOption.IGNORE_CASE),

            // 4. "CURRENCYamount has been deposited via ATM" - ATM deposits
            Regex("""\b([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)\s+has been deposited via ATM""", RegexOption.IGNORE_CASE),

            // 5. "CURRENCYamount transferred via" - transfers
            Regex("""\b([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)\s+transferred via""", RegexOption.IGNORE_CASE),

            // 6. "Cr. transaction of CURRENCY amount" - credit transactions
            Regex("""Cr\. transaction of\s+([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // 7. "Dr. transaction of CURRENCY amount" - debit transactions
            Regex("""Dr\.?\s*transaction of\s+([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // 8. "Transaction of CURRENCY amount" - failed transactions
            Regex("""Transaction of\s+([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // 9. "Amount Paid: CURRENCY amount" - TouchPoints redemption
            Regex("""Amount Paid:\s*([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val currencyCode = match.groupValues[1].trim()
                val amountStr = match.groupValues[2].trim()

                // Validate currency code (3 letters, not month names)
                if (currencyCode.length == 3 &&
                    currencyCode.matches(Regex("""[A-Z]{3}""")) &&
                    !currencyCode.matches(Regex("""^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""", RegexOption.IGNORE_CASE))) {
                    try {
                        val amount = BigDecimal(amountStr.replace(",", ""))
                        if (amount > BigDecimal("0.01")) {
                            // Normalize to 2 decimal places for consistency
                            return if (amount.scale() < 2) {
                                amount.setScale(2)
                            } else {
                                amount
                            }
                        }
                    } catch (e: NumberFormatException) {
                        // Continue to next pattern
                    }
                }
            }
        }

        // If no specific patterns match, try fallback with transaction context
        if (containsCardPurchase(message)) {
            val afterUsage = message.substringAfter("was used for")
            val currencyAmountPattern = Regex("""([A-Z]{3})\s*([0-9,]+(?:\.\d{2})?)""")
            currencyAmountPattern.find(afterUsage)?.let { match ->
                val currencyCode = match.groupValues[1].trim()
                val amountStr = match.groupValues[2].trim()

                if (currencyCode.length == 3 &&
                    currencyCode.matches(Regex("""[A-Z]{3}""")) &&
                    !currencyCode.matches(Regex("""^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""", RegexOption.IGNORE_CASE))) {
                    try {
                        val amount = BigDecimal(amountStr.replace(",", ""))
                        if (amount > BigDecimal("0.01")) {
                            // Normalize to 2 decimal places for consistency
                            return if (amount.scale() < 2) {
                                amount.setScale(2)
                            } else {
                                amount
                            }
                        }
                    } catch (e: NumberFormatException) {
                        // Continue
                    }
                }
            }
        }

        return null
    }

    override fun containsCardPurchase(message: String): Boolean {
        // ADCB debit card purchase patterns
        return message.contains("was used for", ignoreCase = true) ||
                message.contains("used for", ignoreCase = true)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // ADCB Debit Card Purchase pattern: "at MERCHANT,AE. Avl.Bal"
        if(containsCardPurchase(message)) {
            // Extract merchant name between "at " and ", AE" (country code)
            val merchantPattern = Regex("""at\s+([^,\n]+),\s*[A-Z]{2}""", RegexOption.IGNORE_CASE)
            merchantPattern.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        // TouchPoints Redemption pattern
        if (message.contains("TouchPoints Redemption", ignoreCase = true)) {
            return "TouchPoints Redemption"
        }

        // ATM location extraction: Clean up ATM names to show only location
        if (message.contains("withdrawn from", ignoreCase = true)) {
            // Extract everything between "at " and balance indicators
            val afterAt = message.substringAfter("at ")
            val beforeBalance = afterAt.substringBefore(" Avl.Bal").substringBefore("Available balance")

            // Clean up the ATM info - remove extra whitespace and newlines
            val atmInfo = beforeBalance.trim().replace(Regex("""\s+"""), " ")

            if (atmInfo.isNotEmpty() && (atmInfo.startsWith("ATM-") || atmInfo.startsWith("ATM "))) {
                // Clean up the ATM name to show only the meaningful location
                val cleanAtmName = when {
                    // Remove "ATM-" prefix
                    atmInfo.startsWith("ATM-") -> atmInfo.substring(4)
                    // Remove "ATM " prefix
                    atmInfo.startsWith("ATM ") -> atmInfo.substring(4)
                    else -> atmInfo
                }.trim()

                // Remove ATM numeric identifiers (digits that appear at the start)
                val finalAtmName = cleanAtmName.replace(Regex("""^\d+"""), "").replace(".", "").trim()

                if (finalAtmName.isNotEmpty()) {
                    return "ATM Withdrawal: $finalAtmName"
                }
            }
        }

        // ATM deposit location: "at Dubai Mall"
        if (message.contains("deposited via ATM", ignoreCase = true)) {
            val depositPattern = Regex("""at\s+([^.\n]+)""", RegexOption.IGNORE_CASE)
            depositPattern.find(message.substringAfter("deposited via ATM"))?.let { match ->
                return "ATM Deposit: ${match.groupValues[1].trim()}"
            }
        }

        // Transfer merchant: use FAB transfer logic
        if (message.contains("transferred via", ignoreCase = true)) {
            return "Transfer via ADCB Banking"
        }

        // Credit transaction: "A Cr. transaction"
        if (message.contains("Cr. transaction", ignoreCase = true)) {
            return "Account Credit"
        }

        // Debit transaction: "A Dr. transaction"
        if (message.contains("Dr. transaction", ignoreCase = true)) {
            return "Account Debit"
        }

        // Fallback to FAB merchant extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // ADCB patterns for account extraction
        val adcbPatterns = listOf(
            // For debit card transactions, prioritize account number over card number for consistency
            // Debit card: "Your debit card XXX0830 linked to acc. XXX810001" (extract 6-digit account)
            Regex("""debit card\s+[X\*]+(\d{4})\s+linked to acc\.?\s*[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // General linked account pattern: "linked to acc. XXX810001" (extract 6-digit account)
            Regex("""linked to acc\.?\s*[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // ATM withdrawals: "withdrawn from acc. XXX810001" (extract 6-digit account)
            Regex("""withdrawn from acc\.?\s*[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // ATM deposits: "in your account XXX810001" (extract 6-digit account)
            Regex("""in your account\s+[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // Transfers: "from acc. no. XXX810001" (extract 6-digit account)
            Regex("""from acc\.?\s*no\.?\s*[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // Account number: "account number XXX810001" (extract 6-digit account)
            Regex("""account (?:number\s*)?[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // Dr/Cr transactions: "on your account number XXX810001" (extract 6-digit account)
            Regex("""on your account number\s+[X\*]+(\d{6})""", RegexOption.IGNORE_CASE),

            // Fallback to 4-digit patterns for older messages or different format
            Regex("""debit card\s+[X\*]+(\d{4})\s+linked to acc\.?\s*[X\*]+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""withdrawn from acc\.?\s*[X\*]+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""in your account\s+[X\*]+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""from acc\.?\s*no\.?\s*[X\*]+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""account (?:number\s*)?[X\*]+(\d{4})""", RegexOption.IGNORE_CASE),

            // Standard card pattern (last resort)
            Regex("""Card\s+[X\*]+(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in adcbPatterns) {
            pattern.find(message)?.let { match ->
                // Extract the appropriate group based on pattern
                val accountNumber = when {
                    // For patterns with 2 groups (card + account), return account (group 2)
                    match.groupValues.size > 2 && match.groupValues[2].isNotEmpty() -> {
                        match.groupValues[2]  // Return 6-digit account number
                    }
                    // For patterns with 1 group, return that group
                    match.groupValues[1].isNotEmpty() -> {
                        match.groupValues[1]  // Return account number (could be 4 or 6 digits)
                    }
                    else -> null
                }

                if (accountNumber != null && accountNumber.isNotEmpty()) {
                    return accountNumber
                }
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // ADCB balance patterns: "Avl.Bal AED 131.20" or "Available balance is 173.20"
        val adcbBalancePatterns = listOf(
            // "Avl.Bal AED 131.20"
            Regex("""Avl\.Bal\s+([A-Z]{3})\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // "Available balance is 173.20"
            Regex("""Available balance is\s+([A-Z]{3})?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // "Avl. bal. AED 1758.97"
            Regex("""Avl\.?\s*bal\.?\s+([A-Z]{3})\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // "Avl.Bal.AED93.48" (no space between currency and amount)
            Regex("""Avl\.Bal\.?([A-Z]{3})([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // "Available Balance is AED4962.77"
            Regex("""Available Balance is\s+([A-Z]{3})([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in adcbBalancePatterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = if (match.groupValues.size > 2) match.groupValues[2] else match.groupValues[1]
                return try {
                    BigDecimal(balanceStr.replace(",", ""))
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // ADCB transaction reference patterns
        val adcbReferencePatterns = listOf(
            // Date format: "on Jul 10 2024  5:49PM"
            Regex("""on\s+(\w{3}\s+\d{1,2}\s+\d{4}\s+\d{1,2}:\d{2}[AP]M)"""),

            // Date format: "on Feb  4 2025 12:49PM"
            Regex("""on\s+(\w{3}\s+\d{1,2}\s+\d{4}\s+\d{1,2}:\d{2}[AP]M)"""),

            // Fallback to FAB reference extraction
            Regex("""(\d{2}/\d{2}/\d{2}\s+\d{2}:\d{2})""")
        )

        for (pattern in adcbReferencePatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Debit card purchases are expenses
            containsCardPurchase(message) -> TransactionType.EXPENSE

            // ATM withdrawals are expenses
            lowerMessage.contains("withdrawn from") && lowerMessage.contains("atm") -> TransactionType.EXPENSE

            // ATM deposits are income
            lowerMessage.contains("deposited via atm") -> TransactionType.INCOME

            // Transfers are transfers
            lowerMessage.contains("transferred via") -> TransactionType.TRANSFER

            // Credit transactions are income
            lowerMessage.contains("cr. transaction") -> TransactionType.INCOME

            // Debit transactions are expenses
            lowerMessage.contains("dr. transaction") -> TransactionType.EXPENSE

            // TouchPoints redemption is expense/transfer
            lowerMessage.contains("touchpoints redemption") -> TransactionType.EXPENSE

            // Fallback to FAB transaction type logic
            else -> super.extractTransactionType(message)
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip non-transaction messages specific to ADCB
        val adcbNonTransactionKeywords = listOf(
            // Failed transactions - these have amounts but shouldn't be parsed as successful transactions
            "could not be completed",
            "insufficient funds",
            "transaction.*could not be completed",

            // OTP and security messages
            "do not share your otp",
            "otp for transaction",
            "activation key",
            "do not share with anyone",

            // Card management
            "has been de-activated",
            "has been activated",
            "congratulations on the first usage",
            "digital card assigned to",

            // General alerts and confirmations
            "pin change/setup was successful",
            "request for pin change/setup",
            "we have updated your emirates id",
            "confirmation recd. from",
            "sr no.",
            "for clarifications please call",
            "for assistance please call"
        )

        if (adcbNonTransactionKeywords.any { keyword ->
                lowerMessage.contains(Regex(keyword, RegexOption.IGNORE_CASE))
            }) {
            return false
        }

        // ADCB-specific transaction indicators
        val adcbTransactionKeywords = listOf(
            "your debit card",
            "your credit card",
            "was used for",
            "used for",
            "withdrawn from",
            "deposited via atm",
            "transferred via",
            "cr. transaction",
            "dr. transaction",
            "cr.transaction",
            "dr.transaction",
            "transaction.*was successful",
            "touchpoints redemption",
            "debit card.*used for",  // "Debit card XXX0830 linked to acc. XXX810001 used for"
            "touchpoints redemption request",
            "account number XXX.*was successful"
        )

        if (adcbTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fallback to FAB's transaction detection
        return super.isTransactionMessage(message)
    }

    // Override currency extraction for ADCB's multi-currency support
    override fun extractCurrency(message: String): String? {
        // Extract currency from the transaction context, not balance info
        // Focus on the same contexts as extractAmount to ensure consistency

        // Look for currency codes in transaction-specific contexts
        val transactionCurrencyPatterns = listOf(
            // "was used for CURRENCYamount" - debit card usage
            Regex("""was used for\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "used for CURRENCYamount" - debit card usage (shorter version)
            Regex("""used for\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "CURRENCYamount withdrawn from" - ATM withdrawals (handle both spaced and non-spaced)
            Regex("""\b([A-Z]{3})\s*[0-9,]+(?:\.\d{2})?\s+withdrawn from""", RegexOption.IGNORE_CASE),

            // "CURRENCYamount has been deposited via ATM" - ATM deposits
            Regex("""\b([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?\s+has been deposited via ATM""", RegexOption.IGNORE_CASE),

            // "CURRENCYamount transferred via" - transfers
            Regex("""\b([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?\s+transferred via""", RegexOption.IGNORE_CASE),

            // "Cr. transaction of CURRENCY amount" - credit transactions
            Regex("""Cr\.?\s*transaction of\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "Dr. transaction of CURRENCY amount" - debit transactions
            Regex("""Dr\.?\s*transaction of\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "Transaction of CURRENCY amount" - failed transactions
            Regex("""Transaction of\s+([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),

            // "Amount Paid: CURRENCY amount" - TouchPoints redemption
            Regex("""Amount Paid:\s*([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in transactionCurrencyPatterns) {
            pattern.find(message)?.let { match ->
                val currencyCode = match.groupValues[1].uppercase()
                // Validate it's a 3-letter code (standard ISO currency format) but not month names
                if (currencyCode.matches(Regex("""[A-Z]{3}""")) &&
                    !currencyCode.matches(Regex("""^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""", RegexOption.IGNORE_CASE))) {
                    return currencyCode
                }
            }
        }

        // Fallback: look for currency codes in the transaction part of the message
        if (containsCardPurchase(message)) {
            val afterUsage = if (message.contains("was used for", ignoreCase = true)) {
                message.substringAfter("was used for")
            } else {
                message.substringAfter("used for")
            }
            val beforeBalance = afterUsage.substringBefore(" Avl.Bal").substringBefore(" Available balance")
            // Handle both spaced and non-spaced currency+amount patterns
            val currencyPattern = Regex("""([A-Z]{3})\s*[0-9,]+(?:\.\d{2})?""")
            currencyPattern.find(beforeBalance)?.let { match ->
                val currencyCode = match.groupValues[1].uppercase()
                if (currencyCode.matches(Regex("""[A-Z]{3}""")) &&
                    !currencyCode.matches(Regex("""^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""", RegexOption.IGNORE_CASE))) {
                    return currencyCode
                }
            }
        }

        // Default to AED for ADCB (UAE Dirham) only if no other currency found
        return "AED"
    }
}