package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Parser for Karnataka Bank SMS messages
 *
 * Supported formats:
 * - Debit: "Your Account x001234x has been DEBITED for Rs.6368/-"
 * - Credit: "Your a/c XX1234 is credited by Rs.6600.00"
 * - ACH, UPI, and other transaction types
 *
 * Common senders: Karnataka Bank, KTKBNK, variations with DLT patterns
 */
class KarnatakaBankParser : BankParser() {

    override fun getBankName() = "Karnataka Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("KARNATAKA BANK") ||
                normalizedSender.contains("KARNATAKABANK") ||
                normalizedSender.contains("KBLBNK") ||
                normalizedSender.contains("KTKBANK") ||
                normalizedSender.contains("KARBANK") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-KBLBNK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-KARBANK-S$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-KBLBNK$")) ||
                // Direct sender IDs
                normalizedSender == "KBLBNK" ||
                normalizedSender == "KARBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "DEBITED for Rs.6368/-"
        val debitPattern = Regex(
            """DEBITED\s+for\s+Rs\.?([0-9,]+(?:\.\d{2})?)/?\-?""",
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

        // Pattern 2: "credited by Rs.6600.00"
        val creditPattern = Regex(
            """credited\s+by\s+Rs\.?([0-9,]+(?:\.\d{2})?)""",
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

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: ACH transactions - "ACHInwDr-MERCHANT/date"
        val achPattern = Regex(
            """ACH[A-Za-z]*-([^/]+)/""",
            RegexOption.IGNORE_CASE
        )
        achPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "from <merchant> on" for UPI
        val fromPattern = Regex(
            """from\s+([^\s]+)\s+on""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: Check for specific transaction types
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("lic of india") -> "LIC of India"
            lowerMessage.contains("upi") && fromPattern.find(message) == null -> "UPI Transaction"
            else -> super.extractMerchant(message, sender)
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Account x001234x" or "Account XX1234X"
        val accountPattern1 = Regex(
            """Account\s+[xX]*([0-9]{4,6})[xX]*""",
            RegexOption.IGNORE_CASE
        )
        accountPattern1.find(message)?.let { match ->
            val digits = match.groupValues[1]
            // Return last 4 digits if more than 4
            return if (digits.length > 4) {
                digits.takeLast(4)
            } else {
                digits
            }
        }

        // Pattern 2: "a/c XX1234"
        val accountPattern2 = Regex(
            """a/c\s+[xX]{0,2}([0-9]{4,6})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern2.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "UPI Ref no 441877242175"
        val upiRefPattern = Regex(
            """UPI\s+Ref\s+no\s+([0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Balance is Rs.705.92"
        val balancePattern = Regex(
            """Balance\s+is\s+Rs\.?([0-9,]+(?:\.\d{2})?)""",
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

        // Fall back to base class
        return super.extractBalance(message)
    }
}
