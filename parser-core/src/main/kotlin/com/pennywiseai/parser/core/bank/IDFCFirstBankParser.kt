package com.pennywiseai.parser.core.bank

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
 */
class IDFCFirstBankParser : BankParser() {

    override fun getBankName() = "IDFC First Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("IDFCBK") ||
                normalizedSender.contains("IDFCFB") ||
                normalizedSender.contains("IDFC")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // List of amount patterns for IDFC First Bank
        val amountPatterns = listOf(
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
        // Pattern: A/C XXXXXXXXXXX where last 4 digits are visible
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
