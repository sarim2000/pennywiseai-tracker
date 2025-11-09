package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Indian Overseas Bank (IOB) SMS messages
 *
 * Common senders: VA-IOBCHN-S, XX-IOB-S, etc.
 *
 * SMS Format:
 * Your a/c no. XXXXX92 is credited by Rs.906.00 on 2025-08-28 17, from SIDDHANT SIN-7737219900@su(UPI Ref no 560699645381).Payer Remark - Paid via Supe -IOB
 */
class IndianOverseasBankParser : BankParser() {

    override fun getBankName() = "Indian Overseas Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("IOB") ||
                normalizedSender.contains("IOBCHN")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // List of amount patterns for IOB
        val amountPatterns = listOf(
            // "credited by Rs.906.00"
            Regex("""credited\s+by\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "debited by Rs.906.00"
            Regex("""debited\s+by\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "credited with Rs.906.00"
            Regex("""credited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "debited for Rs.906.00"
            Regex("""debited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
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

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credited by") -> TransactionType.INCOME
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            lowerMessage.contains("is credited") -> TransactionType.INCOME

            lowerMessage.contains("debited by") -> TransactionType.EXPENSE
            lowerMessage.contains("debited for") -> TransactionType.EXPENSE
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // UPI transaction with payer details
        // Pattern: "from SIDDHANT SIN-7737219900@su(UPI Ref"
        val upiPayerPattern = Regex(
            """from\s+([^(]+?)(?:\(UPI|$)""",
            RegexOption.IGNORE_CASE
        )
        upiPayerPattern.find(message)?.let { match ->
            val payer = match.groupValues[1].trim()

            // Check if it contains UPI ID
            if (payer.contains("@")) {
                // Extract name and UPI ID
                val parts = payer.split("-")
                return if (parts.size >= 2) {
                    val name = cleanMerchantName(parts[0].trim())
                    val upiId = parts[1].trim()
                    "UPI - $name ($upiId)"
                } else {
                    "UPI - ${cleanMerchantName(payer)}"
                }
            } else {
                val cleanedPayer = cleanMerchantName(payer)
                if (isValidMerchantName(cleanedPayer)) {
                    return cleanedPayer
                }
            }
        }

        // Check for payer remark
        val remarkPattern = Regex(
            """Payer\s+Remark\s*-\s*([^-]+)""",
            RegexOption.IGNORE_CASE
        )
        remarkPattern.find(message)?.let { match ->
            val remark = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(remark) && !remark.equals("Paid via Supe", ignoreCase = true)) {
                return remark
            }
        }

        // Generic patterns for debit transactions
        if (message.contains("debited", ignoreCase = true)) {
            // Try to extract merchant from "to" or "for" patterns
            val toPattern = Regex(
                """(?:to|for)\s+([^,.-]+)""",
                RegexOption.IGNORE_CASE
            )
            toPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Your a/c no. XXXXX92"
        val accountPattern = Regex(
            """a/c\s+no\.\s+[X]*(\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            val digits = match.groupValues[1]
            return if (digits.length >= 4) digits.takeLast(4) else digits
        }

        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern: "(UPI Ref no 560699645381)"
        val upiRefPattern = Regex(
            """\(UPI\s+Ref\s+no\s+(\d+)\)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Alternative pattern without parentheses
        val altUpiRefPattern = Regex(
            """UPI\s+Ref\s+no\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        altUpiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and non-transaction messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("verification") ||
            lowerMessage.contains("request") ||
            lowerMessage.contains("failed")
        ) {
            return false
        }

        // Check for IOB specific transaction patterns
        if (lowerMessage.contains("is credited by") ||
            lowerMessage.contains("is debited by") ||
            lowerMessage.contains("credited with") ||
            lowerMessage.contains("debited for")
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}