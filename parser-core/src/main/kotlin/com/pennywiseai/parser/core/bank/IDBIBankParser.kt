package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Parser for IDBI Bank SMS messages
 *
 * Supported formats:
 * - Debit: "Your account has been successfully debited with Rs 59.00"
 * - UPI: "IDBI Bank Acct XX1234 debited for Rs 1040.00"
 * - AutoPay/Mandate transactions
 * - Balance information
 *
 * Common senders: IDBIBK, IDBIBANK, variations with DLT patterns
 */
class IDBIBankParser : BankParser() {

    override fun getBankName() = "IDBI Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("IDBIBK") ||
                normalizedSender.contains("IDBIBANK") ||
                normalizedSender.contains("IDBI") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-IDBIBK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-IDBI-S$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-IDBIBK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-IDBI$")) ||
                // Direct sender IDs
                normalizedSender == "IDBIBK" ||
                normalizedSender == "IDBIBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "debited with Rs 59.00"
        val debitWithPattern = Regex(
            """debited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitWithPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "debited for Rs 1040.00"
        val debitForPattern = Regex(
            """debited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitForPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: "credited with Rs XXX"
        val creditPattern = Regex(
            """credited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
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
        // Pattern 1: "towards <merchant> for"
        val towardsPattern = Regex(
            """towards\s+([^.\n]+?)\s+for""",
            RegexOption.IGNORE_CASE
        )
        towardsPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "; <merchant> credited."
        val creditedMerchantPattern = Regex(
            """;\s*([^.\n]+?)\s+credited\.""",
            RegexOption.IGNORE_CASE
        )
        creditedMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: AutoPay/Mandate specific
        if (message.contains("AutoPay", ignoreCase = true) ||
            message.contains("MANDATE", ignoreCase = true)
        ) {
            // Extract merchant name before "for" if it's AutoPay
            val merchantPattern = Regex(
                """towards\s+([^.\n]+?)\s+for\s+\w*MANDATE""",
                RegexOption.IGNORE_CASE
            )
            merchantPattern.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Acct XX1234"
        val acctPattern = Regex(
            """Acct\s+(?:XX|X\*+)?(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
        acctPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "IDBI Bank Acct XX1234"
        val bankAcctPattern = Regex(
            """IDBI\s+Bank\s+Acct\s+(?:XX|X\*+)?(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
        bankAcctPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: "RRN 519766155631"
        val rrnPattern = Regex(
            """RRN\s+([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        rrnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "UPI:521687538121"
        val upiPattern = Regex(
            """UPI:([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Bal Rs 3694.38"
        val balancePattern = Regex(
            """Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
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

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip UPI block instructions (not a transaction)
        if (lowerMessage.contains("to block upi") && lowerMessage.contains("send sms")) {
            // This is just instruction text, don't skip the entire message
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }
}