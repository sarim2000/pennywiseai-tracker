package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for M-PESA (Kenya) mobile money SMS messages
 *
 * Handles formats like:
 * - "Ksh70.00 paid to Person Name on 20/10/24"
 * - "Ksh1000.00 sent to Equity Paybill Account for account 123123"
 * - "You have received Ksh300.00 from Person Name"
 * - "Ksh50.00 sent to Person Name 0711 111 111"
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
        // Pattern 1: "paid to Person Name. on DATE" or "paid to Person 4 1. on DATE"
        // Capture everything before " number. on" pattern
        val paidToPattern = Regex(
            """paid to\s+(.+?)\s+\d+\.\s+on""",
            RegexOption.IGNORE_CASE
        )
        paidToPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "sent to Person 2 0711 111 111" (with phone number - spaced format)
        val sentToPhonePattern = Regex(
            """sent to\s+(.+?)\s+0\d{3}\s+\d{3}\s+\d{3}""",
            RegexOption.IGNORE_CASE
        )
        sentToPhonePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "sent to PAYBILL_NAME for account NUMBER" or "sent to Equity Paybill Account for account"
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
        return merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")  // Remove trailing parentheses
            .replace(Regex("""\s+Ref\s+No.*""", RegexOption.IGNORE_CASE), "")  // Remove ref numbers
            .replace(Regex("""\s+on\s+\d{2}.*"""), "")  // Remove date suffixes
            .replace(Regex("""\s+UPI.*""", RegexOption.IGNORE_CASE), "")  // Remove UPI suffixes
            .replace(Regex("""\s+at\s+\d{2}:\d{2}.*"""), "")  // Remove time suffixes
            .replace(Regex("""\s*-\s*$"""), "")  // Remove trailing dash
            .trim()
    }
}
