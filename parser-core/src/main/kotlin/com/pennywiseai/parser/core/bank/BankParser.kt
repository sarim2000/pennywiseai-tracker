package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.CompiledPatterns
import com.pennywiseai.parser.core.Constants
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Base class for bank-specific message parsers.
 * Each bank should extend this class and implement its specific parsing logic.
 */
abstract class BankParser {

    /**
     * Returns the name of the bank this parser handles.
     */
    abstract fun getBankName(): String

    /**
     * Checks if this parser can handle messages from the given sender.
     */
    abstract fun canHandle(sender: String): Boolean

    /**
     * Returns the currency used by this bank.
     * Defaults to INR for Indian banks. International banks should override this.
     */
    open fun getCurrency(): String = "INR"

    /**
     * Parses an SMS message and extracts transaction information.
     * Returns null if the message cannot be parsed.
     */
    open fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
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
            currency = getCurrency()
        )
    }

    /**
     * Checks if the message is a transaction message (not OTP, promotional, etc.)
     */
    protected open fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")
        ) {
            return false
        }

        // Skip payment request messages (common across banks)
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment") ||
            lowerMessage.contains("requests rs") ||
            lowerMessage.contains("ignore if already paid")
        ) {
            return false
        }

        // Skip merchant payment acknowledgments
        if (lowerMessage.contains("have received payment")) {
            return false
        }

        // Skip payment reminder/due messages
        if (lowerMessage.contains("is due") ||
            lowerMessage.contains("min amount due") ||
            lowerMessage.contains("minimum amount due") ||
            lowerMessage.contains("in arrears") ||
            lowerMessage.contains("is overdue") ||
            lowerMessage.contains("ignore if paid") ||
            (lowerMessage.contains("pls pay") && lowerMessage.contains("min of"))
        ) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    /**
     * Extracts the transaction currency from the message.
     * Can be overridden by specific bank parsers for custom logic.
     */
    protected open fun extractCurrency(message: String): String? {
        // Default implementation - try to find currency pattern
        val currencyPattern = Regex("""([A-Z]{3})\s*[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE)
        currencyPattern.find(message)?.let { match ->
            return match.groupValues[1].uppercase()
        }
        return null
    }

    /**
     * Extracts the transaction amount from the message.
     */
    protected open fun extractAmount(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Amount.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Extracts the transaction type (INCOME/EXPENSE/INVESTMENT).
     */
    protected open fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Check for investment transactions first (highest priority)
        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }

        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("deducted") -> TransactionType.EXPENSE

            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("cashback") && !lowerMessage.contains("earn cashback") -> TransactionType.INCOME

            else -> null
        }
    }

    /**
     * Checks if the message is for an investment transaction.
     * Can be overridden by specific bank parsers for custom logic.
     */
    protected open fun isInvestmentTransaction(lowerMessage: String): Boolean {
        val investmentKeywords = listOf(
            // Clearing corporations
            "iccl",                         // Indian Clearing Corporation Limited
            "indian clearing corporation",
            "nsccl",                        // NSE Clearing Corporation
            "nse clearing",
            "clearing corporation",

            // Auto-pay indicators (excluding mandate/UMRN to avoid subscription false positives)
            "nach",                         // National Automated Clearing House
            "ach",                          // Automated Clearing House
            "ecs",                          // Electronic Clearing Service

            // Investment platforms
            "groww",
            "zerodha",
            "upstox",
            "kite",
            "kuvera",
            "paytm money",
            "etmoney",
            "coin by zerodha",
            "smallcase",
            "angel one",
            "angel broking",
            "5paisa",
            "icici securities",
            "icici direct",
            "hdfc securities",
            "kotak securities",
            "motilal oswal",
            "sharekhan",
            "edelweiss",
            "axis direct",
            "sbi securities",

            // Investment types
            "mutual fund",
            "sip",                          // Systematic Investment Plan
            "elss",                         // Tax saving funds
            "ipo",                          // Initial Public Offering
            "folio",                        // Mutual fund folio
            "demat",
            "stockbroker",
            "digital gold",                 // Digital Gold investments
            "sovereign gold",               // Sovereign Gold Bonds

            // Stock exchanges
            "nse",                          // National Stock Exchange
            "bse",                          // Bombay Stock Exchange
            "cdsl",                         // Central Depository Services
            "nsdl"                          // National Securities Depository
        )

        return investmentKeywords.any { lowerMessage.contains(it) }
    }

    /**
     * Extracts merchant/payee information.
     */
    protected open fun extractMerchant(message: String, sender: String): String? {
        for (pattern in CompiledPatterns.Merchant.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        return null
    }

    /**
     * Extracts transaction reference number.
     */
    protected open fun extractReference(message: String): String? {
        for (pattern in CompiledPatterns.Reference.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        return null
    }

    /**
     * Extracts last 4 digits of account number.
     */
    protected open fun extractAccountLast4(message: String): String? {
        for (pattern in CompiledPatterns.Account.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val accountLast4 = match.groupValues[1]

                // Validate that this is actually an account number, not a date or RRN
                if (isValidAccountLast4(accountLast4, match.value, message)) {
                    return accountLast4
                }
            }
        }

        return null
    }

    /**
     * Validates that the extracted 4 digits are actually part of an account number,
     * not a date, RRN, or other numeric field.
     */
    private fun isValidAccountLast4(last4: String, matchedText: String, fullMessage: String): Boolean {
        // Escape the last4 for safe regex usage
        val escapedLast4 = Regex.escape(last4)

        // Check if it's part of a date pattern (dd/mm/yyyy, dd-mm-yyyy, etc.)
        val datePatterns = listOf(
            Regex("""\d{1,2}[/-]\d{1,2}[/-]$escapedLast4"""),  // 04/11/2025, 05-02-2025
            Regex("""$escapedLast4[/-]\d{1,2}[/-]\d{1,2}"""),  // 2025/11/04, 2025-02-05
            Regex("""\bon\s+\d{1,2}[/-]\d{1,2}[/-]$escapedLast4""", RegexOption.IGNORE_CASE),  // "on 04/11/2025"
            Regex("""\bdated\s+\d{1,2}[/-]\d{1,2}[/-]$escapedLast4""", RegexOption.IGNORE_CASE)  // "dated 05-02-2025"
        )

        for (datePattern in datePatterns) {
            if (datePattern.find(fullMessage) != null) {
                return false
            }
        }

        // Check if it's part of an RRN (Reference Number) - typically 12 digits
        val rrnPatterns = listOf(
            Regex("""RRN\s+(?:No\.?)?(\d{8,16})""", RegexOption.IGNORE_CASE),  // "RRN No.503612315893"
            Regex("""Ref\s+(?:No\.?)?(\d{8,16})""", RegexOption.IGNORE_CASE)   // "Ref No.503612315893"
        )

        for (rrnPattern in rrnPatterns) {
            rrnPattern.find(fullMessage)?.let { rrnMatch ->
                val rrnNumber = rrnMatch.groupValues[1]
                // If our last4 is part of this RRN, reject it
                if (rrnNumber.contains(last4)) {
                    return false
                }
            }
        }

        // Check if it's a standalone year (2024, 2025, etc.)
        if (last4.toIntOrNull() in 2000..2099) {
            // Only reject if it appears to be a year in date context
            val yearContextPatterns = listOf(
                Regex("""\bon\s+\d{1,2}[/-]\d{1,2}[/-]$escapedLast4""", RegexOption.IGNORE_CASE),
                Regex("""\bdated\s+.*?$escapedLast4""", RegexOption.IGNORE_CASE),
                Regex("""$escapedLast4(?:\s|$)""")  // Year at end of phrase
            )

            for (yearPattern in yearContextPatterns) {
                if (yearPattern.find(fullMessage) != null) {
                    // Only reject if NOT preceded by "Account" or "A/c" within 25 chars
                    val accountBeforeYear = Regex("""(?:A/c|Account|Acct).{0,25}$escapedLast4""", RegexOption.IGNORE_CASE)
                    if (accountBeforeYear.find(fullMessage) == null) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Extracts balance after transaction.
     */
    protected open fun extractBalance(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Balance.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Extracts credit card available limit from the message.
     * This is the remaining credit available to spend, NOT the total credit limit.
     */
    protected open fun extractAvailableLimit(message: String): BigDecimal? {

        // Common patterns for credit limit across banks
        val creditLimitPatterns = listOf(
            // "Available limit Rs.111,111.89" - Federal Bank format (no space after Rs.)
            Regex("""Available\s+limit\s+Rs\.([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Available limit Rs. 111,111.89" or "Available limit: Rs 111,111.89"
            Regex(
                """Available\s+limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Avl Lmt Rs.111,111.89" or "Avl Lmt: Rs 111,111.89" (ICICI and others)
            Regex("""Avl\s+Lmt:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Avail Limit Rs.111,111.89"
            Regex("""Avail\s+Limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Available Credit Limit: Rs.111,111.89"
            Regex(
                """Available\s+Credit\s+Limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Limit: Rs.111,111.89" (generic, but only for credit card messages)
            Regex("""(?:^|\s)Limit:?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for ((index, pattern) in creditLimitPatterns.withIndex()) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[1].replace(",", "")
                return try {
                    val limit = BigDecimal(limitStr)
                    limit
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Detects if the transaction is from a card (credit/debit) based on message patterns.
     * First excludes account-related patterns, then checks for actual card patterns.
     */
    protected open fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // FIRST: Explicitly exclude account-related patterns - these are NOT cards
        val accountPatterns = listOf(
            "a/c",           // Account abbreviation (e.g., "from HDFC Bank A/c 120092")
            "account",       // Full word account (e.g., "from HDFC Bank Account XX0093")
            "ac ",           // Account abbreviation with space
            "acc ",          // Account abbreviation
            "saving account",
            "current account",
            "savings a/c",
            "current a/c"
        )

        // If message contains account patterns, it's NOT a card transaction
        for (pattern in accountPatterns) {
            if (lowerMessage.contains(pattern)) {
                return false
            }
        }

        // SECOND: Check for actual card-specific patterns
        val cardPatterns = listOf(
            "card ending",
            "card xx",
            "debit card",
            "credit card",
            "card no.",
            "card number",
            "card *",
            "card x"
        )

        // Check for card patterns
        for (pattern in cardPatterns) {
            if (lowerMessage.contains(pattern)) {
                return true
            }
        }

        // Check for masked card number patterns (e.g., "XXXX1234", "*1234", "ending 1234")
        // BUT only if we haven't already excluded it as an account transaction
        val maskedCardRegex = Regex("""(?:xx|XX|\*{2,})?\d{4}""")
        if (lowerMessage.contains("ending") && maskedCardRegex.containsMatchIn(message)) {
            return true
        }

        return false
    }

    /**
     * Cleans merchant name by removing common suffixes and noise.
     */
    protected open fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(CompiledPatterns.Cleaning.TRAILING_PARENTHESES, "")
            .replace(CompiledPatterns.Cleaning.REF_NUMBER_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.DATE_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.UPI_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TIME_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TRAILING_DASH, "")
            .replace(CompiledPatterns.Cleaning.PVT_LTD, "")
            .replace(CompiledPatterns.Cleaning.LTD, "")
            .trim()
    }

    /**
     * Validates if the extracted merchant name is valid.
     */
    protected open fun isValidMerchantName(name: String): Boolean {
        val commonWords =
            setOf("USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE")

        return name.length >= Constants.Parsing.MIN_MERCHANT_NAME_LENGTH &&
                name.any { it.isLetter() } &&
                name.uppercase() !in commonWords &&
                !name.all { it.isDigit() } &&
                !name.contains("@") // Not a UPI ID
    }
}
