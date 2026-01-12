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

        return ParsedTransaction(
            amount = amount,
            type = transactionType,
            merchant = merchant,
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            reference = extractReference(smsBody),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName()
        )
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: INR 49.00 is paid from
        // Pattern 2: INR 1000.50 is credited to
        val pattern1 = Regex(
            """INR\s+([\d,]+(?:\.\d{2})?)\s+is\s+(?:paid|credited|debited)""",
            RegexOption.IGNORE_CASE
        )

        pattern1.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "for INR 123.45 on" (debit card format)
        val debitCardPattern = Regex(
            """for\s+INR\s+([\d,]+(?:\.\d{2})?)\s+on""",
            RegexOption.IGNORE_CASE
        )

        debitCardPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: "used at ... for INR 305.00" (credit card)
        val creditCardPattern = Regex(
            """for\s+INR\s+([\d,]+(?:\.\d{2})?)(?:\s|$|\.)""",
            RegexOption.IGNORE_CASE
        )

        creditCardPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
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
        // Pattern 1: "A/c 074-260***-006" format (Issue #118)
        // Match account numbers with dashes and asterisks
        val acNoPattern = Regex(
            """A/c\s+\d+-\d+\*+-(\d+)""",
            RegexOption.IGNORE_CASE
        )
        acNoPattern.find(message)?.let { match ->
            val lastPart = match.groupValues[1]
            // Pad with leading zero if needed to make it 4 digits
            return lastPart.padStart(4, '0')
        }

        // Pattern 2: "Debit Card XXXXX71xx" format
        // Handle mixed digits and 'x' characters
        val debitCardPattern = Regex(
            """Debit\s+Card\s+[X*]+(\d+[xX]*)""",
            RegexOption.IGNORE_CASE
        )
        debitCardPattern.find(message)?.let { match ->
            val cardNum = match.groupValues[1]
            // Return last 4 characters as-is (may include 'x')
            return if (cardNum.length >= 4) {
                cardNum.takeLast(4).lowercase()
            } else {
                cardNum.lowercase()
            }
        }

        // Pattern 3: "creditcard xxxxx1234" or "credit card xxxxx1234"
        val creditCardPattern = Regex(
            """credit\s*card\s+[xX*]+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 4: account XXXXXX1234
        val accountPattern = Regex(
            """account\s+[X*]+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
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
        // Pattern 1: "Your Avl Bal is INR xyz" (Issue #118 - abbreviated form)
        val avlBalPattern = Regex(
            """(?:Your\s+)?Avl\s+Bal\s+is\s+INR\s+([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        avlBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "available bal is INR xyz" or "Your available bal is INR xyz"
        val availableBalPattern = Regex(
            """available\s+bal\s+is\s+INR\s+([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        availableBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractBalance(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Debit card transactions - "Thank you for using HSBC Debit Card"
            lowerMessage.contains("debit card") && lowerMessage.contains("thank you for using") -> TransactionType.EXPENSE
            lowerMessage.contains("debit card") && lowerMessage.contains("for inr") -> TransactionType.EXPENSE

            // Credit card transactions
            lowerMessage.contains("creditcard") || lowerMessage.contains("credit card") -> {
                // Credit card transactions that say "used at" are expenses (credit type)
                if (lowerMessage.contains("used at")) TransactionType.CREDIT
                else TransactionType.CREDIT
            }

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
        if (lowerMessage.contains("otp is") || lowerMessage.contains("otp valid for")
        ) {
            return false
        }

        // Check for HSBC-specific transaction keywords
        if (lowerMessage.contains("is paid from") ||
            lowerMessage.contains("is credited to") ||
            lowerMessage.contains("is debited") ||
            (lowerMessage.contains("creditcard") && lowerMessage.contains("used at")) ||
            (lowerMessage.contains("credit card") && lowerMessage.contains("used at")) ||
            (lowerMessage.contains("thank you for using") && lowerMessage.contains("card")) ||
            (lowerMessage.contains("debit card") && lowerMessage.contains("for inr")) ||
            (lowerMessage.contains("inr") && lowerMessage.contains("account"))
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}
