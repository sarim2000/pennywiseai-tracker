package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Bank of Baroda (BOB) SMS messages
 */
class BankOfBarodaParser : BaseIndianBankParser() {

    override fun getBankName() = "Bank of Baroda"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("BOB") ||
                normalizedSender.contains("BARODA") ||
                normalizedSender.contains("BOBSMS") ||
                normalizedSender.contains("BOBTXN") ||
                normalizedSender.contains("BOBCRD") ||  // Credit card messages
                // DLT patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-BOBSMS-[A-Z]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOBTXN-[A-Z]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOB-[A-Z]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOBCRD-[A-Z]$")) ||  // Credit card DLT pattern
                // Direct sender IDs
                normalizedSender == "BOB" ||
                normalizedSender == "BANKOFBARODA"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 0: ALERT: INR XXX.XX is spent (Credit card pattern - check first)
        val alertSpentPattern = Regex(
            """ALERT:\s*INR\s*([\d,]+(?:\.\d{2})?)\s+is\s+spent""",
            RegexOption.IGNORE_CASE
        )
        alertSpentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 1: Rs.XX transferred from A/c (Transfer pattern)
        val transferPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+transferred\s+from""",
            RegexOption.IGNORE_CASE
        )
        transferPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Rs.80.00 Dr. from
        val drPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+Dr\.?\s+from""",
            RegexOption.IGNORE_CASE
        )
        drPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: credited with INR 70.00
        val creditPattern = Regex(
            """credited\s+with\s+INR\s+([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 4: Rs.xxxxxx Credited to
        val creditPattern2 = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+Credited\s+to""",
            RegexOption.IGNORE_CASE
        )
        creditPattern2.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 5: Cr. to redacted@ybl (UPI)
        val crPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+.*?Cr\.?\s+to""",
            RegexOption.IGNORE_CASE
        )
        crPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 6: Rs.xxxxx deposited in cash
        val cashDepositPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+deposited\s+in\s+cash""",
            RegexOption.IGNORE_CASE
        )
        cashDepositPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: transferred from A/c to:Merchant Name (Transfer pattern)
        val transferToPattern = Regex(
            """transferred\s+from\s+A/c\s+[^\s]+\s+to:\s*([^.]+?)(?:\.|$)""",
            RegexOption.IGNORE_CASE
        )
        transferToPattern.find(message)?.let { match ->
            val merchantRaw = match.groupValues[1].trim()
            // Clean up the merchant name (remove "Total Bal" and everything after if present)
            val merchant =
                merchantRaw.split(Regex("""\s+Total\s+Bal""", RegexOption.IGNORE_CASE))[0].trim()
            if (isValidMerchantName(merchant)) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: Cr. to redacted@ybl (UPI VPA)
        val upiPattern = Regex(
            """Cr\.?\s+to\s+([^\s]+@[^\s.]+)""",
            RegexOption.IGNORE_CASE
        )
        upiPattern.find(message)?.let { match ->
            val vpa = match.groupValues[1]
            // Extract name from VPA if possible
            val name = vpa.substringBefore("@")
            return if (name == "redacted") {
                "UPI Payment"
            } else {
                cleanMerchantName(name)
            }
        }

        // Pattern 3: IMPS by Name of Person
        val impsPattern = Regex(
            """IMPS/[\d]+\s+by\s+([^.]+?)(?:\s*\.|$)""",
            RegexOption.IGNORE_CASE
        )
        impsPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: For UPI credits, extract from context
        if (message.contains("UPI", ignoreCase = true)) {
            if (message.contains("credited", ignoreCase = true)) {
                return "UPI Credit"
            } else if (message.contains("Dr.", ignoreCase = true)) {
                return "UPI Payment"
            }
        }

        // Pattern 5: For IMPS without clear merchant
        if (message.contains("IMPS", ignoreCase = true)) {
            return "IMPS Transfer"
        }

        // Pattern 6: Cash deposit
        if (message.contains("deposited in cash", ignoreCase = true)) {
            return "Cash Deposit"
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 0: BOBCARD ending 1234 (Credit card format)
        val bobCardPattern = Regex(
            """BOBCARD\s+ending\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        bobCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 1: A/C XXXXXX (6 digits shown)
        val sixDigitPattern = Regex(
            """A/C\s+X*(\d{6})""",
            RegexOption.IGNORE_CASE
        )
        sixDigitPattern.find(message)?.let { match ->
            val digits = match.groupValues[1]
            // Return last 4 of the 6 digits shown
            return digits.takeLast(4)
        }

        // Pattern 2: A/c ...xxxx
        val maskedPattern = Regex(
            """A/c\s+\.+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        maskedPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: AvlBal:Rsxxxxxcx or AvlBal: Rsxxxxxxx
        val avlBalPattern = Regex(
            """AvlBal:\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
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

        // Pattern 2: Total Bal:Rs.xxxxxxx
        val totalBalPattern = Regex(
            """Total\s+Bal:\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        totalBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: Avlbl Amt:Rs.xxxxxxxx
        val avlAmtPattern = Regex(
            """Avlbl\s+Amt:\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        avlAmtPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: Ref:52211xxxxxx
        val refPattern1 = Regex(
            """Ref:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern1.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: UPI Ref No 510xxxxxxxxxx
        val upiRefPattern = Regex(
            """UPI\s+Ref\s+No\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: IMPS/5182xxxxxxx
        val impsRefPattern = Regex(
            """IMPS/(\d+)""",
            RegexOption.IGNORE_CASE
        )
        impsRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Credit card transactions - BOBCARD
            lowerMessage.contains("spent on your bobcard") -> TransactionType.CREDIT
            lowerMessage.contains("bobcard") && lowerMessage.contains("spent") -> TransactionType.CREDIT
            lowerMessage.contains("bobcard") && lowerMessage.contains("is spent") -> TransactionType.CREDIT

            // Debit/Expense patterns
            lowerMessage.contains("transferred from") -> TransactionType.EXPENSE
            lowerMessage.contains("dr.") || lowerMessage.contains("debited") -> TransactionType.EXPENSE

            // Credit/Income patterns
            lowerMessage.contains("cr.") || lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Pattern for "Available credit limit is Rs 42,981.46"
        val creditLimitPattern = Regex(
            """Available\s+credit\s+limit\s+is\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        creditLimitPattern.find(message)?.let { match ->
            val limitStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(limitStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAvailableLimit(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for BOB-specific transaction keywords
        if (lowerMessage.contains("dr. from") ||
            lowerMessage.contains("cr. to") ||
            lowerMessage.contains("credited to a/c") ||
            lowerMessage.contains("credited with inr") ||
            lowerMessage.contains("deposited in cash") ||
            lowerMessage.contains("transferred from") ||  // Transfer transactions
            lowerMessage.contains("is spent")
        ) {  // Credit card transactions
            return true
        }

        return super.isTransactionMessage(message)
    }
}