package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for M-Pesa Tanzania (Vodacom) mobile money SMS messages
 *
 * Handles formats like:
 * - "SGR1234567 Confirmed. You have received TZS 50,000.00 from JOHN DOE (255754XXXXXX)"
 * - "SGR9876543 Confirmed. TZS 20,000.00 sent to JANE SMITH (255762XXXXXX)"
 * - "SGR5544332 Confirmed. TZS 15,000.00 paid to SUPERMARKET X (Merchant ID: 556677)"
 * - "SGR1122334 Confirmed. TZS 10,000.00 paid to LUKU for account 1423XXXXXXX. Token: ..."
 *
 * Key patterns:
 * - Transaction ID: 10 character alphanumeric starting with SGR (e.g., SGR1234567)
 * - Status: "Confirmed." at start after transaction ID
 * - Balance: "New M-Pesa balance is TZS X"
 * - Currency: TZS (Tanzanian Shilling)
 *
 * Note: This is distinct from Kenya M-Pesa which uses KES currency
 * Country: Tanzania
 */
class MPesaTanzaniaParser : BankParser() {

    override fun getBankName() = "M-Pesa Tanzania"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        // M-Pesa Tanzania uses same sender IDs but we differentiate by content
        return normalizedSender.contains("MPESA") ||
                normalizedSender.contains("M-PESA") ||
                normalizedSender == "MPESA" ||
                normalizedSender == "M-PESA" ||
                normalizedSender.contains("VODACOM")
    }

    /**
     * Override parse to check for TZS currency (Tanzania)
     * This helps differentiate from Kenya M-Pesa which uses KES
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Only parse if message contains TZS (Tanzanian Shilling)
        // This differentiates from Kenya M-Pesa which uses Ksh/KES
        if (!smsBody.contains("TZS", ignoreCase = true)) {
            return null
        }

        return super.parse(smsBody, sender, timestamp)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "TZS 50,000.00" with space
        val tzsSpacePattern = Regex(
            """TZS\s+([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        tzsSpacePattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "TZS50,000.00" without space
        val tzsNoSpacePattern = Regex(
            """TZS([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        tzsNoSpacePattern.find(message)?.let { match ->
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

        return when {
            // Received money = income
            lowerMessage.contains("you have received") ||
            lowerMessage.contains("received tsh") ||
            lowerMessage.contains("received tzs") -> TransactionType.INCOME

            // Sent/paid money = expense
            lowerMessage.contains("sent to") -> TransactionType.EXPENSE
            lowerMessage.contains("paid to") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "received TZS X from NAME (phone)"
        val fromPattern = Regex(
            """from\s+([A-Z][A-Za-z\s]+?)(?:\s*\(|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "sent to NAME (phone)" or "TZS X sent to NAME"
        val sentToPattern = Regex(
            """sent to\s+([A-Z][A-Za-z\s]+?)(?:\s*\(|$)""",
            RegexOption.IGNORE_CASE
        )
        sentToPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "paid to MERCHANT (Merchant ID: X)"
        val paidToMerchantPattern = Regex(
            """paid to\s+([A-Za-z0-9\s]+?)(?:\s*\(Merchant|\s+on|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        paidToMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "paid to LUKU for account X" (utility payment)
        val utilityPattern = Regex(
            """paid to\s+(\w+)\s+for\s+account""",
            RegexOption.IGNORE_CASE
        )
        utilityPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "New M-Pesa balance is TZS 150,000.00"
        val balancePattern = Regex(
            """New M-Pesa balance is TZS\s*([0-9,]+(?:\.[0-9]{2})?)""",
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
        // Pattern 1: Transaction ID at start (10-char alphanumeric, typically starts with SGR)
        // e.g., "SGR1234567 Confirmed"
        val txnIdPattern = Regex(
            """^([A-Z0-9]{10})\s+Confirmed""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Alternative pattern without space
        val txnIdAltPattern = Regex(
            """^([A-Z0-9]{10})\s+Confirmed\.""",
            RegexOption.IGNORE_CASE
        )
        txnIdAltPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: TIPS Reference for inter-operator transfers
        val tipsPattern = Regex(
            """TIPS\s+Reference[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        tipsPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Must contain "Confirmed" (M-Pesa Tanzania standard)
        if (!lowerMessage.contains("confirmed")) {
            return false
        }

        // Must contain TZS currency indicator
        if (!lowerMessage.contains("tzs")) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "received",
            "sent to",
            "paid to",
            "withdrawn",
            "new m-pesa balance"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")  // Remove trailing parentheses
            .replace(Regex("""\s+on\s+\d{4}.*"""), "")  // Remove date suffix
            .replace(Regex("""\s+at\s+\d{2}:\d{2}.*"""), "")  // Remove time suffix
            .replace(Regex("""\s*-\s*$"""), "")  // Remove trailing dash
            .trim()
    }
}
