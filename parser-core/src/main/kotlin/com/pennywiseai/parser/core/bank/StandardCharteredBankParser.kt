package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Chartered Bank SMS messages (India)
 *
 * Supported formats:
 * - UPI Debit: "Your a/c XX3421 is debited for Rs. 302.00 on 03-12-2025 15:49 and credited to a/c XX1465 (UPI Ref no 487597904232)"
 * - NEFT Credit: "Dear Customer, there is an NEFT credit of INR 48,796.00 in your account 123xxxx7655 on 1/11/2025.Available Balance:INR 97,885.05"
 *
 * Common senders: VM-SCBANK-S, VD-SCBANK-S, JK-SCBANK-S, SCBANK, StanChart
 */
class StandardCharteredBankParser : BankParser() {

    override fun getBankName() = "Standard Chartered Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("SCBANK") ||
                upperSender.contains("STANCHART") ||
                upperSender.contains("STANDARDCHARTERED") ||
                upperSender.contains("STANDARD CHARTERED") ||
                // DLT patterns for transactions (-S suffix)
                upperSender.matches(Regex("""^[A-Z]{2}-SCBANK-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "is debited for Rs. 302.00"
        val debitPattern = Regex(
            """is debited for Rs\.\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "NEFT credit of INR 48,796.00"
        val neftCreditPattern = Regex(
            """(?:NEFT|RTGS|IMPS)\s+credit\s+of\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        neftCreditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: "is credited for Rs. xxx"
        val creditPattern = Regex(
            """is credited for Rs\.\s*([0-9,]+(?:\.\d{2})?)""",
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

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("is debited for") -> TransactionType.EXPENSE
            lowerMessage.contains("neft credit") -> TransactionType.INCOME
            lowerMessage.contains("rtgs credit") -> TransactionType.INCOME
            lowerMessage.contains("imps credit") -> TransactionType.INCOME
            lowerMessage.contains("is credited for") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "credited to a/c XX1465" (for debit/UPI transfers)
        val upiTransferPattern = Regex(
            """and credited to a/c ([X\*]+\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiTransferPattern.find(message)?.let { match ->
            val accountNum = match.groupValues[1]
            return "UPI Transfer to $accountNum"
        }

        // Pattern 2: NEFT/RTGS/IMPS credits - generic merchant
        if (message.lowercase().contains("neft credit")) {
            return "NEFT Credit"
        }
        if (message.lowercase().contains("rtgs credit")) {
            return "RTGS Credit"
        }
        if (message.lowercase().contains("imps credit")) {
            return "IMPS Credit"
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Your a/c XX3421" - extract digits after XX
        val acPattern = Regex(
            """Your a/c ([X\*]+)(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        acPattern.find(message)?.let { match ->
            return match.groupValues[2]
        }

        // Pattern 2: "in your account 123xxxx7655" - extract last 4 digits
        val accountPattern = Regex(
            """in your account (?:\d+[xX\*]+)?(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern: "UPI Ref no 487597904232"
        val upiRefPattern = Regex(
            """UPI Ref no (\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class patterns
        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Available Balance:INR 97,885.05" (may have no space after colon)
        val balancePattern = Regex(
            """Available Balance:\s*INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
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

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Standard Chartered specific transaction keywords
        if (lowerMessage.contains("is debited for") ||
            lowerMessage.contains("is credited for") ||
            lowerMessage.contains("neft credit") ||
            lowerMessage.contains("rtgs credit") ||
            lowerMessage.contains("imps credit")
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}
