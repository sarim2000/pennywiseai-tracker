package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for IDFC First Bank SMS messages
 *
 * Common senders: XX-IDFCBK-S, XX-IDFCBK-T, XX-IDFCB-S, XX-IDFCB-T, IDFCBK
 * Examples: BM-IDFCBK-S, AX-IDFCBK-T, AD-IDFCB-S
 *
 * SMS Format:
 * Your A/C XXXXXXXXXXX is debited by INR 68.00 on 06/08/25 17:36. New Bal :INR XXXXX.00
 * Your A/C XXXXXXXXXXX is credited by INR 500.00 on 06/08/25 17:36. New Bal :INR XXXXX.00
 *
 * Credit Card Format (with multi-currency support):
 * Transaction Successful! EUR 500.00 spent on your IDFC FIRST Bank Credit Card ending XXXX at MERCHANT on DD-MMM-YYYY
 */
class IDFCFirstBankParser : BaseIndianBankParser() {

    override fun getBankName() = "IDFC First Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("IDFCBK") ||
                normalizedSender.contains("IDFCFB") ||
                normalizedSender.contains("IDFC")
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

        // Extract currency dynamically for multi-currency support (foreign transactions on credit cards)
        val currency = extractCurrencyFromMessage(smsBody) ?: "INR"

        // Extract available limit for credit card transactions
        val availableLimit = if (type == TransactionType.CREDIT) {
            extractAvailableLimit(smsBody)
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

    /**
     * Extract currency from IDFC First Bank transaction messages.
     * Handles formats like "EUR 500.00 spent" or "USD 100.00 spent" for credit card transactions.
     */
    private fun extractCurrencyFromMessage(message: String): String? {
        // Pattern: "EUR 500.00 spent" or "USD 100.00 spent" (credit card foreign currency transactions)
        val currencySpentPattern = Regex(
            """([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?\s+spent""",
            RegexOption.IGNORE_CASE
        )
        currencySpentPattern.find(message)?.let { match ->
            val currency = match.groupValues[1].uppercase()
            // Validate: 3 letters, not month abbreviations
            if (!currency.matches(Regex("^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$"))) {
                return currency
            }
        }

        return null // Falls back to INR
    }

    override fun extractAmount(message: String): BigDecimal? {
        // List of amount patterns for IDFC First Bank
        val amountPatterns = listOf(
            // Credit card foreign currency pattern: "EUR 500.00 spent" or "USD 100.00 spent"
            Regex("""[A-Z]{3}\s+([0-9,]+(?:\.\d{2})?)\s+spent""", RegexOption.IGNORE_CASE),

            // Debit patterns
            Regex("""Debit\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""debited\s+by\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""debited\s+by\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // Credit patterns
            Regex("""credited\s+by\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""credited\s+with\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""credited\s+by\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),

            // Interest pattern
            Regex("""interest\s+of\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                val amount = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amount)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
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

        // Must contain transaction keywords - IDFC specific patterns
        val transactionKeywords = listOf(
            "debit", "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid", "interest"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("debit") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE  // Credit card transactions
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("withdrawn") || lowerMessage.contains("withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("deposited") || lowerMessage.contains("deposit") -> TransactionType.INCOME
            lowerMessage.contains("cash deposit") -> TransactionType.INCOME
            lowerMessage.contains("interest") && lowerMessage.contains("earned") -> TransactionType.INCOME
            lowerMessage.contains("monthly interest") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Interest credit
        if (lowerMessage.contains("monthly interest")) {
            return "Interest Credit"
        }

        // Cash deposit
        if (lowerMessage.contains("cash deposit")) {
            // Try to extract ATM ID if present
            val atmPattern = Regex("""ATM\s+(?:ID\s+)?([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
            atmPattern.find(message)?.let { match ->
                return "Cash Deposit - ATM ${match.groupValues[1]}"
            }
            return "Cash Deposit"
        }

        // Pattern: "debited by Rs. X on DATE; MERCHANT credited" (e.g., REDBUS credited)
        val merchantCreditedPattern = Regex(
            """;\s*([A-Z][A-Z0-9\s]+?)\s+credited""",
            RegexOption.IGNORE_CASE
        )
        merchantCreditedPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1])
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // UPI transaction pattern
        if (message.contains("UPI", ignoreCase = true)) {
            // Try to extract UPI ID
            val upiPattern = Regex(
                """(?:to|from|at)\s+([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)""",
                RegexOption.IGNORE_CASE
            )
            upiPattern.find(message)?.let { match ->
                return "UPI - ${match.groupValues[1]}"
            }
            return "UPI Transaction"
        }

        // IMPS with mobile number
        if (message.contains("IMPS", ignoreCase = true)) {
            // Try to extract mobile number
            val mobilePattern = Regex("""mobile\s+[X]*(\d{3,4})""", RegexOption.IGNORE_CASE)
            mobilePattern.find(message)?.let { match ->
                return "IMPS Transfer - Mobile XXX${match.groupValues[1]}"
            }
            return "IMPS Transfer"
        }

        // NEFT/RTGS patterns
        when {
            message.contains("NEFT", ignoreCase = true) -> return "NEFT Transfer"
            message.contains("RTGS", ignoreCase = true) -> return "RTGS Transfer"
        }

        // ATM withdrawal/transaction
        if (message.contains("ATM", ignoreCase = true)) {
            // Try to extract ATM ID
            val atmIdPattern = Regex("""ATM\s+([A-Z]{2}\d+)""", RegexOption.IGNORE_CASE)
            atmIdPattern.find(message)?.let { match ->
                return "ATM - ${match.groupValues[1]}"
            }
            return "ATM Transaction"
        }

        // For card transactions
        val toPattern = Regex(
            """(?:to|at|for)\s+([A-Z][A-Z0-9\s&.-]+?)(?:\s+on|\s+New|\.|\,|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1])
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: Credit Card ending XX1234 or ending XXXX
        val cardEndingPattern = Regex(
            """Credit\s+Card\s+ending\s+[X]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        cardEndingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: A/C XXXXXXXXXXX where last 4 digits are visible
        val acPattern = Regex(
            """A/C\s+[X]*(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
        acPattern.find(message)?.let { match ->
            val digits = match.groupValues[1]
            return if (digits.length >= 4) digits.takeLast(4) else digits
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // List of balance patterns for IDFC First Bank
        val balancePatterns = listOf(
            // "New Bal :INR XXXXX.00" or "New bal: Rs.XXXXX.00"
            Regex(
                """New\s+Bal\s*:\s*(?:INR|Rs\.?)\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "New balance is INR XXXXX.00"
            Regex("""New\s+balance\s+is\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Updated balance is INR XXXXX.00"
            Regex(
                """Updated\s+balance\s+is\s+INR\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Available balance Rs. X,XXX.XX"
            Regex(
                """Available\s+balance\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
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
        // RRN (Retrieval Reference Number) pattern
        val rrnPattern = Regex(
            """RRN\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        rrnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // IMPS reference pattern in parentheses
        val impsRefPattern = Regex(
            """IMPS\s+Ref\s+no\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        impsRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // UPI reference pattern
        val upiRefPattern = Regex(
            """UPI[:/]\s*([0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Transaction ID pattern
        val txnIdPattern = Regex(
            """(?:txn|transaction)\s*(?:id|ref|no)[:\s]*([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }
}
