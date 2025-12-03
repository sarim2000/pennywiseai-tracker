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
        // Pattern 1: "at IKEA INDIA ." (debit card format with space before period)
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

        // Pattern 2: "used at [Merchant] for" (credit card)
        val creditCardPattern = Regex(
            """used\s+at\s+([^.]+?)\s+for\s+INR""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "to [Merchant] on" for payments
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

        // Pattern 4: "from [Merchant]" for credits
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

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Debit Card XXXXX71xx" format
        val debitCardPattern = Regex(
            """Debit\s+Card\s+[X*]+(\d+)[xX*]*""",
            RegexOption.IGNORE_CASE
        )
        debitCardPattern.find(message)?.let { match ->
            val digits = match.groupValues[1]
            // Extract last 4 digits if we have more than 4
            return if (digits.length >= 4) {
                digits.takeLast(4)
            } else {
                digits
            }
        }

        // Pattern 2: "creditcard xxxxx1234" or "credit card xxxxx1234"
        val creditCardPattern = Regex(
            """credit\s*card\s+[xX*]+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: account XXXXXX1234
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
        // Pattern: with ref 222222222222
        val pattern = Regex(
            """with\s+ref\s+(\w+)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "available bal is INR xyz" or "Your available bal is INR xyz"
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

            // Standard transaction patterns
            lowerMessage.contains("is paid from") -> TransactionType.EXPENSE
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            lowerMessage.contains("is credited to") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
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
