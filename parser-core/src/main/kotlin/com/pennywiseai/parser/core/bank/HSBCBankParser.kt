package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for HSBC Bank SMS messages
 */
class HSBCBankParser : BankParser() {

    override fun getBankName() = "HSBC Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("HSBC") ||
                normalizedSender.contains("HSBCIN") ||
                // DLT patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-HSBCIN-[A-Z]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-HSBC-[A-Z]$"))
    }

    override fun parse(
        smsBody: String,
        sender: String,
        timestamp: Long
    ): ParsedTransaction? {
        if (!canHandle(sender)) return null
        if (!isTransactionMessage(smsBody)) return null

        val amount = extractAmount(smsBody) ?: return null
        val transactionType = extractTransactionType(smsBody) ?: return null
        val merchant = extractMerchant(smsBody, sender) ?: "Unknown"
        val currency = detectCurrency(smsBody)

        return ParsedTransaction(
            amount = amount,
            type = transactionType,
            merchant = merchant,
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            creditLimit = extractAvailableLimit(smsBody),
            reference = extractReference(smsBody),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = detectIsCard(smsBody),
            currency = currency
        )
    }

    private fun detectCurrency(message: String): String {
        val currencyPattern = Regex("""(EGP|INR|USD|GBP|EUR|AED|SAR|OMR|BHD|KWD|QAR)\s+[\d,]+""", RegexOption.IGNORE_CASE)
        return currencyPattern.find(message)?.groupValues?.get(1)?.uppercase() ?: "INR"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Multi-currency pattern group
        val cur = """(?:INR|EGP|USD|GBP|EUR|AED|SAR|OMR|BHD|KWD|QAR)"""

        // Pattern 1: "INR 49.00 is paid from" / "EGP 123.99 is debited"
        val pattern1 = Regex(
            """$cur\s+([\d,]+(?:\.\d+)?)\s+is\s+(?:paid|credited|debited)""",
            RegexOption.IGNORE_CASE
        )
        pattern1.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Pattern 2: "for EGP 123.99 on" / "for INR 305.00 on" (card transactions)
        val forCurrencyPattern = Regex(
            """for\s+$cur\s+([\d,]+(?:\.\d+)?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        forCurrencyPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Pattern 3: "has been used for EGP 123.99 on" (Egypt credit card)
        val usedForPattern = Regex(
            """used\s+for\s+$cur\s+([\d,]+(?:\.\d+)?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        usedForPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Pattern 4: "for INR 305.00" at end (credit card, no trailing "on")
        val forCurrencyEnd = Regex(
            """for\s+$cur\s+([\d,]+(?:\.\d+)?)(?:\s|$|\.)""",
            RegexOption.IGNORE_CASE
        )
        forCurrencyEnd.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 0: Outgoing NEFT/RTGS/IMPS - "credited to the [BANK] A/c XXX of [NAME]"
        // Extract the beneficiary name (the person receiving the money)
        val outgoingNeftPattern = Regex(
            """credited\s+to\s+the\s+\w+\s+A/c\s+[X\d]+\s+of\s+(.+?)\s+on\s+""",
            RegexOption.IGNORE_CASE
        )
        outgoingNeftPattern.find(message)?.let { match ->
            val beneficiaryName = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(beneficiaryName)) {
                return beneficiaryName
            }
        }

        // Pattern 1: "from CHAS A/c ***6983 of John Doe" (Issue #118 - NEFT/Credit transactions)
        // Extract everything after "from" until " ." or end of sentence
        val neftCreditPattern = Regex(
            """as\s+(?:NEFT|RTGS|IMPS)\s+from\s+(.+?)\s+\.""",
            RegexOption.IGNORE_CASE
        )
        neftCreditPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "at IKEA INDIA ." (debit card format with space before period)
        val atMerchantPattern = Regex(
            """at\s+([^.]+?)\s*\.""",
            RegexOption.IGNORE_CASE
        )
        atMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "used at [Merchant] for" (credit card)
        val creditCardPattern = Regex(
            """used\s+at\s+([^\s]+)\s+for\s+INR""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "to [Merchant] on" for payments
        val paymentPattern = Regex(
            """to\s+([^.]+?)\s+on\s+\d""",
            RegexOption.IGNORE_CASE
        )
        paymentPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 5: "from [Merchant]" for generic credits
        val creditPattern = Regex(
            """from\s+([^.]+?)(?:\s+on\s+|\s+with\s+|$)""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun cleanMerchantName(merchant: String): String {
        var cleaned = super.cleanMerchantName(merchant)

        // Remove "for INR xxx" suffix that may appear in credit card transactions
        cleaned = cleaned.replace(Regex("""\s+for\s+INR\s+[\d,]+(?:\.\d{2})?$""", RegexOption.IGNORE_CASE), "")

        return cleaned.trim()
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }

        // Pattern 0: "Credit Card ending with ***6" (Egypt format)
        val endingWithPattern = Regex(
            """(?:Credit\s+Card|Debit\s+Card|Card)\s+ending\s+with\s+([*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        endingWithPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        // Pattern 1: "A/c 074-260***-006" format (Issue #118)
        // Capture everything after A/c keyword, filter to digits, take last 4
        val acNoPattern = Regex(
            """A/c\s+([\d\-*]+)""",
            RegexOption.IGNORE_CASE
        )
        acNoPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        // Pattern 2: "Debit Card XXXXX71xx" format
        // Handle mixed digits and 'x' characters - extract digits only, take last 4
        val debitCardPattern = Regex(
            """Debit\s+Card\s+([X*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        debitCardPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        // Pattern 3: "creditcard xxxxx1234" or "credit card xxxxx1234"
        val creditCardPattern = Regex(
            """credit\s*card\s+([xX*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        // Pattern 4: account XXXXXX1234
        val accountPattern = Regex(
            """account\s+([X*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "with UTR CHASH00007392391" (Issue #118 - NEFT/RTGS/IMPS transactions)
        val utrPattern = Regex(
            """with\s+UTR\s+(\w+)""",
            RegexOption.IGNORE_CASE
        )
        utrPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "with ref 222222222222"
        val refPattern = Regex(
            """with\s+ref\s+(\w+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val cur = """(?:INR|EGP|USD|GBP|EUR|AED|SAR|OMR|BHD|KWD|QAR)"""

        // Pattern 1: "Your Avl Bal is INR xyz"
        val avlBalPattern = Regex(
            """(?:Your\s+)?Avl\s+Bal\s+is\s+$cur\s+([\d,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        avlBalPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Pattern 2: "available bal is INR/EGP xyz"
        val availableBalPattern = Regex(
            """available\s+bal\s+is\s+$cur\s+([\d,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        availableBalPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractBalance(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // "Your available limit is EGP 1234.29"
        val limitPattern = Regex(
            """available\s+limit\s+is\s+(?:INR|EGP|USD|GBP|EUR|AED|SAR|OMR|BHD|KWD|QAR)\s+([\d,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        limitPattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return super.extractAvailableLimit(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Debit card transactions - "Thank you for using HSBC Debit Card"
            lowerMessage.contains("debit card") && lowerMessage.contains("thank you for using") -> TransactionType.EXPENSE
            lowerMessage.contains("debit card") && lowerMessage.contains("for inr") -> TransactionType.EXPENSE

            // Credit card transactions
            lowerMessage.contains("creditcard") || lowerMessage.contains("credit card") -> TransactionType.CREDIT

            // NEFT/RTGS/IMPS outgoing transfer - "credited to the [OTHER BANK] A/c of [PERSON]"
            // This is a confirmation that YOUR outgoing transfer was successful
            isOutgoingNeftTransfer(message) -> TransactionType.TRANSFER

            // Standard transaction patterns
            lowerMessage.contains("is paid from") -> TransactionType.EXPENSE
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            lowerMessage.contains("is credited to") -> TransactionType.INCOME
            lowerMessage.contains("is credited with") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    /**
     * Detects outgoing NEFT/RTGS/IMPS transfers where the SMS confirms
     * that money has been credited to someone else's account at another bank.
     * Pattern: "your NEFT transaction... has been credited to the [BANK] A/c... of [NAME]"
     */
    private fun isOutgoingNeftTransfer(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Must be a NEFT/RTGS/IMPS transaction
        if (!lowerMessage.contains("neft") &&
            !lowerMessage.contains("rtgs") &&
            !lowerMessage.contains("imps")) {
            return false
        }

        // Check for pattern: "credited to the [BANK] A/c" where BANK is not HSBC
        val creditedToOtherBankPattern = Regex(
            """credited\s+to\s+the\s+(\w+)\s+A/c""",
            RegexOption.IGNORE_CASE
        )
        creditedToOtherBankPattern.find(message)?.let { match ->
            val bankName = match.groupValues[1].uppercase()
            // If credited to a non-HSBC account, it's an outgoing transfer
            if (bankName != "HSBC") {
                return true
            }
        }

        // Check for pattern: "credited to... of [PERSON NAME]" (beneficiary name)
        if (lowerMessage.contains("credited to") &&
            Regex("""A/c\s+[X\d]+\s+of\s+\w+""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return true
        }

        return false
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp is") || lowerMessage.contains("otp valid for")) {
            return false
        }

        // Check for HSBC-specific transaction keywords
        if (lowerMessage.contains("is paid from") ||
            lowerMessage.contains("is credited to") ||
            lowerMessage.contains("is debited") ||
            lowerMessage.contains("has been used for") ||
            (lowerMessage.contains("creditcard") && lowerMessage.contains("used at")) ||
            (lowerMessage.contains("credit card") && lowerMessage.contains("used at")) ||
            (lowerMessage.contains("credit card") && lowerMessage.contains("used for")) ||
            (lowerMessage.contains("thank you for using") && lowerMessage.contains("card")) ||
            (lowerMessage.contains("debit card") && lowerMessage.contains("for inr")) ||
            (lowerMessage.contains("inr") && lowerMessage.contains("account")) ||
            (lowerMessage.contains("egp") && lowerMessage.contains("card"))
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}
