package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Canara Bank SMS messages
 */
class CanaraBankParser : BaseIndianBankParser() {

    private companion object {
        val COMPACT_DEBIT_PATTERN = Regex(
            """\bDr\.?\s*(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{2})?)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun getBankName() = "Canara Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("CANBNK") ||
                normalizedSender.contains("CANARA")
    }

    override fun extractAmount(message: String): BigDecimal? {
        COMPACT_DEBIT_PATTERN.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Pattern: Rs.23.00 paid thru
        val upiAmountPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+paid""",
            RegexOption.IGNORE_CASE
        )
        upiAmountPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern: INR 50.00 has been DEBITED
        val debitPattern = Regex(
            """INR\s+([\d,]+(?:\.\d{2})?)\s+has\s+been\s+DEBITED""",
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

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: RTGS/NEFT incoming - "by Sender AXIS MUTUAL FUND REDEMPTION PO, IFSC..."
        // Extract the sender name before IFSC/comma
        val rtgsSenderPattern = Regex(
            """by\s+Sender\s+([^,]+?)(?:,\s*IFSC|,\s*Sender\s+A/c|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        rtgsSenderPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: UPI - paid thru A/C XX1234 on 08-8-25 16:41:00 to BMTC BUS KA57F6
        val upiMerchantPattern = Regex(
            """\sto\s+([^,;]+?)(?:[,;]\s*UPI|\.|-Canara)""",
            RegexOption.IGNORE_CASE
        )
        upiMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Check if it's a generic debit
        if (message.contains("DEBITED", ignoreCase = true)) {
            return "Canara Bank Debit"
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // Pattern: account XXX123 or A/C XX1234
        val accountPattern = Regex(
            """(?:account|A/C)\s+([X*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: Total Avail.bal INR 1,092.62
        val balancePattern = Regex(
            """(?:Total\s+)?Avail\.?bal\s+INR\s+([\d,]+(?:\.\d{2})?)""",
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

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern: UPI Ref 123456789012
        val upiRefPattern = Regex(
            """UPI\s+Ref\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip failed transactions
        if (lowerMessage.contains("failed due to")) {
            return false
        }

        // Defer to the base class first. It accepts the standard keyword forms
        // ("paid thru", "has been debited/credited") AND — importantly — rejects
        // OTP / promotional / payment-request / reminder messages. The previous
        // version short-circuited to `true` on those keywords (and on the
        // compact pattern) *before* super, bypassing those guards. (#621)
        if (super.isTransactionMessage(message)) {
            return true
        }

        // The compact debit form ("Dr. INR 500") carries no standard keyword, so
        // the base class drops it. Accept it here — but not when it appears
        // inside an OTP / promotional body that merely quotes a "Dr. INR" figure.
        if (COMPACT_DEBIT_PATTERN.containsMatchIn(message)) {
            val looksNonTransactional = lowerMessage.contains("otp") ||
                lowerMessage.contains("one time password") ||
                lowerMessage.contains("verification code") ||
                lowerMessage.contains("offer") ||
                lowerMessage.contains("discount") ||
                lowerMessage.contains("win ") ||
                lowerMessage.contains("has requested") ||
                lowerMessage.contains("payment request")
            return !looksNonTransactional
        }

        return false
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Mutual fund REDEMPTION credited = INCOME (money coming in from selling investment)
        // This overrides the base class which would mark "mutual fund" as INVESTMENT
        if (lowerMessage.contains("redemption") && lowerMessage.contains("credited")) {
            return TransactionType.INCOME
        }

        if (COMPACT_DEBIT_PATTERN.containsMatchIn(message)) {
            return TransactionType.EXPENSE
        }

        // Fall back to base class
        return super.extractTransactionType(message)
    }
}
