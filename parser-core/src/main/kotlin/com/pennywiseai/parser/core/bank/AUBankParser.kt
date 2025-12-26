package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for AU Small Finance Bank SMS messages
 *
 * Supported formats:
 * - Credit transactions: "Credited INR XXX to A/c XXXXX on DD-MM-YYYY Ref UPI/XX/XXXXXXXXXX/XXX XXX XX(name of the account). Bal INR XXX"
 * - Debit transactions: "Debited INR XXX from A/c XXXXX on DD-MM-YYYY..."
 * - ATM withdrawals and other transactions
 *
 * Sender patterns: XX-AUBANK-S/T, AUSFB, AU-BANK, etc.
 */
class AUBankParser : BaseIndianBankParser() {

    override fun getBankName() = "AU Small Finance Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("AUBANK")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: Credited INR XXX
        val creditedPattern = Regex(
            """Credited\s+INR\s+([0-9,]+(?:\.\d{2})?)\s+to""",
            RegexOption.IGNORE_CASE
        )
        creditedPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Debited INR XXX
        val debitedPattern = Regex(
            """Debited\s+INR\s+([0-9,]+(?:\.\d{2})?)\s+from""",
            RegexOption.IGNORE_CASE
        )
        debitedPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: INR XXX spent (credit card format)
        val spentPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+spent""",
            RegexOption.IGNORE_CASE
        )
        spentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 4: withdrawn INR XXX
        val withdrawnPattern = Regex(
            """withdrawn\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        withdrawnPattern.find(message)?.let { match ->
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
        // Pattern 1: UPI transactions - extract name from Ref UPI/.../.../.../name(account)
        val upiPattern = Regex(
            """Ref\s+UPI/[^/]+/[^/]+/[^/]+\s+([^(]+)\([^)]+\)""",
            RegexOption.IGNORE_CASE
        )
        upiPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: Alternative UPI format - name in parentheses
        val upiParenPattern = Regex(
            """UPI/[^/]+/[^/]+/[^/]+\s+[^(]*\(([^)]+)\)""",
            RegexOption.IGNORE_CASE
        )
        upiParenPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: ATM transactions
        if (message.contains("ATM", ignoreCase = true) ||
            message.contains("withdrawn", ignoreCase = true)
        ) {
            return "ATM Withdrawal"
        }

        // Pattern 4: General "to/from" patterns
        val toPattern = Regex(
            """(?:to|from)\s+([^.\n]+?)(?:\.\s*|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant) && !merchant.contains("A/c", ignoreCase = true)) {
                return merchant
            }
        }

        // Fall back to base class extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Income keywords
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            // Expense keywords
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE

            // Credit card transactions
            lowerMessage.contains("credit card") && lowerMessage.contains("spent") -> TransactionType.CREDIT

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern for account number: "A/c XXXXX"
        val accountPattern = Regex(
            """A/c\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            val accountNumber = match.groupValues[1]
            return if (accountNumber.length >= 4) {
                accountNumber.takeLast(4)
            } else {
                accountNumber
            }
        }

        // Fall back to base class patterns
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern for balance: "Bal INR XXX"
        val balancePattern = Regex(
            """Bal\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balance = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balance)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Check for AU Bank specific transaction keywords
        val auBankKeywords = listOf(
            "credited inr",
            "debited inr",
            "withdrawn inr",
            "bal inr",
            "ref upi"
        )

        // If any AU Bank specific pattern is found, it's likely a transaction
        if (auBankKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }
}
