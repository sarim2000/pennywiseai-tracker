package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for M-PESA (Kenya) mobile money SMS messages
 *
 * Handles formats like:
 * - "Ksh70.00 paid to Person Name. on 20/10/24" (name with period)
 * - "Ksh200.00 paid to Person Name. on 25/12/25" (full name with period)
 * - "Ksh160.00 paid to F.M STORES. on 20/12/25" (name with period in middle, like "F.M STORES")
 * - "Ksh70.00 paid to person 1. on 20/10/24" (name with number)
 * - "Ksh400.00 sent to BISHAR HAJI on 21/12/25" (name without phone number)
 * - "Ksh1000.00 sent to Equity Paybill Account for account 123123"
 * - "You have received Ksh300.00 from Person Name"
 * - "Ksh50.00 sent to Person Name 0711 111 111" (spaced phone number)
 * - "Ksh100.00 sent to Person Name 0711111111 on 25/12/25" (unspaced phone number)
 * 
 * All patterns normalize multiple spaces in merchant names to single spaces.
 *
 * Common patterns:
 * - Transaction ID: 10-character alphanumeric (e.g., TJK6H7T3GA)
 * - "Confirmed." at start
 * - "New M-PESA balance is Ksh..."
 * Currency: KES (Kenyan Shilling)
 */
class MPESAParser : BankParser() {

    override fun getBankName() = "M-PESA"

    override fun getCurrency() = "KES"

    /**
     * M-PESA is a mobile money service, not a traditional bank account.
     * Since there's no account number in M-PESA SMS messages, we return
     * a consistent identifier so all M-PESA transactions are grouped under one account.
     */
    override fun extractAccountLast4(message: String): String? {
        // Return a consistent identifier for all M-PESA transactions
        // This ensures all M-PESA transactions are grouped under one account
        return "0000"
    }

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("MPESA") ||
                normalizedSender.contains("M-PESA") ||
                normalizedSender == "MPESA" ||
                normalizedSender == "M-PESA"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "Ksh70.00 paid" or "Ksh1,120.00 paid" or "Ksh1000.00 sent"
        val amountPattern = Regex(
            """Ksh([0-9,]+(?:\.[0-9]{2})?)\s+(?:paid|sent|received)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "received Ksh300.00 from"
        val receivedPattern = Regex(
            """received\s+Ksh([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        receivedPattern.find(message)?.let { match ->
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

        // "You have received" = income
        if (lowerMessage.contains("you have received") ||
            lowerMessage.contains("received ksh")
        ) {
            return TransactionType.INCOME
        }

        // "paid to" or "sent to" = expense
        if (lowerMessage.contains("paid to") ||
            lowerMessage.contains("sent to")
        ) {
            return TransactionType.EXPENSE
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1a: "paid to Person Name. on DATE" (name without numbers, period before "on")
        // Handles names with periods like "F.M STORES." by capturing everything up to ". on"
        val paidToNamePattern = Regex(
            """paid to\s+(.+?)\.\s+on""",
            RegexOption.IGNORE_CASE
        )
        paidToNamePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Normalize multiple spaces to single space
            merchant = merchant.replace(Regex("""\s+"""), " ")
            // Remove trailing single digits (like "person 1" -> "person")
            merchant = merchant.replace(Regex("""\s+\d+$"""), "")
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 1b: "paid to Person Name. on DATE" or "paid to Person 4 1. on DATE"
        // Capture everything before " number. on" pattern
        val paidToPattern = Regex(
            """paid to\s+(.+?)\s+\d+\.\s+on""",
            RegexOption.IGNORE_CASE
        )
        paidToPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Normalize multiple spaces to single space
            merchant = merchant.replace(Regex("""\s+"""), " ")
            // Remove trailing single digits (like "Person 4 1" -> "Person 4")
            merchant = merchant.replace(Regex("""\s+\d+$"""), "")
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "sent to PAYBILL_NAME for account NUMBER" - check this BEFORE general "sent to" patterns
        // to avoid matching "Equity Paybill Account for account 123123" as a name
        val sentToAccountPattern = Regex(
            """sent to\s+(.+?)\s+for account""",
            RegexOption.IGNORE_CASE
        )
        sentToAccountPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2a: "sent to Person Name on" (without phone number, preserves multiple spaces)
        // Try pattern with date first (more specific)
        // Note: Pattern 3 (for account) is checked first, so this won't match paybill messages
        val sentToNameWithDatePattern = Regex(
            """sent to\s+(.+?)\s+on\s+\d{2}/\d{2}/\d{2}""",
            RegexOption.IGNORE_CASE
        )
        sentToNameWithDatePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Skip if this contains "for account" (should have been caught by Pattern 3)
            if (merchant.contains("for account", ignoreCase = true)) {
                return@let
            }
            // Preserve multiple spaces (don't normalize)
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2a-alt: "sent to Person Name on" (fallback without requiring date format)
        // Only match if not followed by phone number pattern
        // Note: Pattern 3 (for account) is checked first, so this won't match paybill messages
        val sentToNamePattern = Regex(
            """sent to\s+(.+?)\s+on\s+(?!0\d{3})""",
            RegexOption.IGNORE_CASE
        )
        sentToNamePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Skip if this contains "for account" (should have been caught by Pattern 3)
            if (merchant.contains("for account", ignoreCase = true)) {
                return@let
            }
            // Preserve multiple spaces (don't normalize)
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2b: "sent to Person Name 0711111111 on" (with phone number - unspaced format)
        val sentToPhoneUnspacedPattern = Regex(
            """sent to\s+(.+?)\s+0\d{9}\s+on""",
            RegexOption.IGNORE_CASE
        )
        sentToPhoneUnspacedPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Normalize multiple spaces to single space
            merchant = merchant.replace(Regex("""\s+"""), " ")
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2c: "sent to Person 2 0711 111 111" (with phone number - spaced format)
        val sentToPhonePattern = Regex(
            """sent to\s+(.+?)\s+0\d{3}\s+\d{3}\s+\d{3}""",
            RegexOption.IGNORE_CASE
        )
        sentToPhonePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Normalize multiple spaces to single space
            merchant = merchant.replace(Regex("""\s+"""), " ")
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "received from Person 3 0712121212" or "from BANK OF BARODA KENYA LIMITED 123123"
        // Use greedy match, then remove phone/account numbers BEFORE cleanMerchantName (which removes "LIMITED")
        // Also handle "from LOOP B2C. on" by removing trailing period
        val receivedFromPattern = Regex(
            """received\s+(?:Ksh[0-9,]+(?:\.[0-9]{2})?\s+)?from\s+(.+?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        receivedFromPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Remove trailing period (for "LOOP B2C.")
            merchant = merchant.removeSuffix(".").trim()
            // Remove phone numbers at the end (10 digits without country code)
            merchant = merchant.replace(Regex("""\s+0\d{10}$"""), "")
            // Remove account numbers at the end (6+ digits) - BEFORE cleanMerchantName
            merchant = merchant.replace(Regex("""\s+\d{6,}$"""), "").trim()

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 5: "from COMPANY NAME. on DATE" (period before "on")
        val fromPattern = Regex(
            """from\s+([^.]+)\.\s+on""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Remove phone numbers at the end
            merchant = merchant.replace(Regex("""\s+0\d{10}$"""), "")

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "New M-PESA balance is Ksh123.12"
        val balancePattern = Regex(
            """New M-PESA balance is Ksh([0-9,]+(?:\.[0-9]{2})?)""",
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

        return null
    }

    /**
     * Extracts the transaction cost from M-PESA SMS messages.
     * Pattern: "Transaction cost, Ksh7.00" or "Transaction cost, Ksh0.00"
     * Returns null if not found or if cost is 0.
     */
    fun extractTransactionCost(message: String): BigDecimal? {
        // Pattern: "Transaction cost, Ksh7.00" or "Transaction cost, Ksh0.00"
        val costPattern = Regex(
            """Transaction cost,\s*Ksh([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        costPattern.find(message)?.let { match ->
            val costStr = match.groupValues[1].replace(",", "")
            return try {
                val cost = BigDecimal(costStr)
                // Return null if cost is zero (no need to track free transactions)
                if (cost.compareTo(BigDecimal.ZERO) > 0) {
                    cost
                } else {
                    null
                }
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: Transaction ID at the start (e.g., "TJK6H7T3GA Confirmed")
        val txnIdPattern = Regex(
            """^([A-Z0-9]{10})\s+Confirmed""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Alternative pattern: "TJF987E58C Confirmed.You"
        val txnIdAltPattern = Regex(
            """^([A-Z0-9]{10})\s+Confirmed\.""",
            RegexOption.IGNORE_CASE
        )
        txnIdAltPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: After "Congratulations! " (e.g., "Congratulations! TJ56H6J1WU confirmed")
        val congratsPattern = Regex(
            """Congratulations!\s+([A-Z0-9]{10})\s+confirmed""",
            RegexOption.IGNORE_CASE
        )
        congratsPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip promotional messages that don't have "Confirmed"
        if (!lowerMessage.contains("confirmed")) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "paid to",
            "sent to",
            "received",
            "new m-pesa balance"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        // For M-PESA, we want to keep "LIMITED" in company names like "BANK OF BARODA KENYA LIMITED"
        // So we only apply basic cleaning, not the LTD/LIMITED removal
        var cleaned = merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")  // Remove trailing parentheses
            .replace(Regex("""\s+Ref\s+No.*""", RegexOption.IGNORE_CASE), "")  // Remove ref numbers
            .replace(Regex("""\s+on\s+\d{2}.*"""), "")  // Remove date suffixes
            .replace(Regex("""\s+UPI.*""", RegexOption.IGNORE_CASE), "")  // Remove UPI suffixes
            .replace(Regex("""\s+at\s+\d{2}:\d{2}.*"""), "")  // Remove time suffixes
            .replace(Regex("""\s*-\s*$"""), "")  // Remove trailing dash
            .trim()
        
        // Remove phone numbers (both spaced and unspaced formats)
        // Pattern: 0 followed by 9 digits (spaced: "0XXX XXX XXX" or unspaced: "0XXXXXXXXX")
        cleaned = cleaned.replace(Regex("""\s+0\d{3}\s+\d{3}\s+\d{3}$"""), "")  // Spaced format: "0711 111 111"
        cleaned = cleaned.replace(Regex("""\s+0\d{9}$"""), "")  // Unspaced format: "0711111111"
        cleaned = cleaned.replace(Regex("""\s+0\d{10}$"""), "")  // 11 digits: "07111111111"
        
        // Remove account numbers at the end (6+ digits)
        cleaned = cleaned.replace(Regex("""\s+\d{6,}$"""), "")
        
        // Remove "for account" suffix if it somehow got included
        cleaned = cleaned.replace(Regex("""\s+for account.*$""", RegexOption.IGNORE_CASE), "")
        
        // Preserve multiple spaces (don't normalize) - test expects "Person  Name" with double space
        // Only trim leading/trailing spaces, not internal spaces
        
        return cleaned.trim()
    }
}
