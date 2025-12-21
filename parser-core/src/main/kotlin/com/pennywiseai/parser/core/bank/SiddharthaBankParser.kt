package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Siddhartha Bank Limited (Nepal) SMS messages
 *
 * Handles formats like:
 * - "Dear [NAME], AC ###XXXX1234, NPR 97.00 withdrawn on 09/12/2025 12:31:20 for Fund Trf to A/C PAYABLE IBFT"
 * - "Dear [NAME], AC ###XXXX1234, NPR 810.00 withdrawn on 05/12/2025 18:06:50 for QR Payment to FALCHA KHAJA GHAR"
 * - "Dear [NAME], AC ###XXXX1234, NPR 120,000.00 deposited on 28/11/2025 20:13:59 for Fund Trf frm A/C PAYABLE IBF-FON"
 *
 * Common sender: SBL_Alert
 * Currency: NPR (Nepalese Rupee)
 */
class SiddharthaBankParser : BankParser() {

    override fun getBankName() = "Siddhartha Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace("-", "_")
        return normalizedSender.contains("SBL") ||
                normalizedSender == "SBL_ALERT" ||
                normalizedSender.contains("SIDDHARTHA")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "NPR 97.00" or "NPR 120,000.00" (with commas)
        val nprPattern = Regex(
            """NPR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        nprPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Withdrawn = expense (debit)
        if (lowerMessage.contains("withdrawn")) {
            return TransactionType.EXPENSE
        }

        // Deposited = income (credit)
        if (lowerMessage.contains("deposited") || lowerMessage.contains("credited")) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "QR Payment to FALCHA KHAJA GHAR - falcha"
        val qrPattern = Regex(
            """qr payment to\s+([^-\n]+?)(?:\s+-|$)""",
            RegexOption.IGNORE_CASE
        )
        qrPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: Utility bill - "Fund Trf to A/C PAYABLE IBFT (IN-670724040,NEA"
        if (lowerMessage.contains("nea")) {
            return "Nepal Electricity Authority"
        }

        // Pattern 3: Fund transfer to account
        if (lowerMessage.contains("fund trf to") || lowerMessage.contains("fund transfer to")) {
            // Check for IBFT (Inter-Bank Fund Transfer)
            if (lowerMessage.contains("ibft")) {
                return "Fund Transfer (IBFT)"
            }
            return "Fund Transfer"
        }

        // Pattern 4: Fund transfer from account (deposits)
        if (lowerMessage.contains("fund trf frm") || lowerMessage.contains("fund transfer from")) {
            if (lowerMessage.contains("ibft")) {
                return "Fund Transfer (IBFT)"
            }
            return "Fund Transfer"
        }

        // Pattern 5: Generic deposit
        if (lowerMessage.contains("deposited")) {
            return "Deposit"
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "AC ###XXXX1234" - extract last 4 digits
        val accountPattern = Regex(
            """AC\s+###[X#]+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Alternative pattern: "AC XXXX1234" without ###
        val altAccountPattern = Regex(
            """AC\s+[X#]+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        altAccountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "(IN-670725619,222" - transaction reference with IN prefix
        val inPattern = Regex(
            """\(IN-(\d+)"""
        )
        inPattern.find(message)?.let { match ->
            return "IN-${match.groupValues[1]}"
        }

        // Pattern 2: "IBFT:1171853" - IBFT reference
        val ibftPattern = Regex(
            """IBFT:(\d+)"""
        )
        ibftPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: "FON:IBFT:1171853" - FON reference
        val fonPattern = Regex(
            """FON:IBFT:(\d+)"""
        )
        fonPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Must contain transaction keywords and NPR amount
        val hasAmount = lowerMessage.contains("npr")
        val hasTransactionKeyword = lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("deposited") ||
                lowerMessage.contains("fund trf") ||
                lowerMessage.contains("fund transfer") ||
                lowerMessage.contains("qr payment")

        return hasAmount && hasTransactionKeyword
    }
}
