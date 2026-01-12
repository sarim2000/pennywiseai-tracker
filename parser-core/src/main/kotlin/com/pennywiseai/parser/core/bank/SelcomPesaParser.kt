package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Selcom Pesa (Tanzania) mobile money SMS messages
 *
 * Handles formats like:
 * - "0426JXCX Confirmed. You have received TZS 175,000.00 from MICHAEL EMIL LUYANGI - NMB"
 * - "0426JXGC Accepted. You have sent TZS 50,000.00 to NURU ISSA - Mixx by Yas"
 * - "10234C2WQ Confirmed. You have withdrawn TZS 200,000.00 at ATM"
 * - "0428KRRY Confirmed. You have paid TZS 8,900.00 to APPLECOMBILL"
 *
 * Key patterns:
 * - Transaction ID: 8-9 character alphanumeric at start (e.g., 0426JXCX, 10234C2WQ)
 * - Status: "Confirmed." or "Accepted."
 * - Balance: "Updated balance is TZS X"
 * - Fee breakdown: "Total charges TZS X (Fee X, VAT X, Ex Duty X)"
 *
 * Currency: TZS (Tanzanian Shilling)
 * Country: Tanzania
 */
class SelcomPesaParser : BankParser() {

    override fun getBankName() = "Selcom Pesa"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("SELCOM") ||
                normalizedSender.contains("SELCOMPESA") ||
                normalizedSender == "SELCOM PESA" ||
                normalizedSender == "SELCOM"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "TZS 175,000.00" or "TZS 50,000.00"
        val tzsPattern = Regex(
            """TZS\s+([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        tzsPattern.find(message)?.let { match ->
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
            lowerMessage.contains("you have received") -> TransactionType.INCOME

            // Sent money, paid, withdrawn = expense
            lowerMessage.contains("you have sent") -> TransactionType.EXPENSE
            lowerMessage.contains("you have paid") -> TransactionType.EXPENSE
            lowerMessage.contains("you have withdrawn") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "from NAME - BANK/SERVICE (account/phone)"
        // e.g., "from MICHAEL EMIL LUYANGI - NMB (201100XXXXX)"
        val fromPattern = Regex(
            """from\s+([A-Z][A-Za-z\s]+?)(?:\s+-\s+[^(]+)?\s*\([^)]+\)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "to NAME - SERVICE (phone)" for sent money
        // e.g., "to NURU ISSA - Mixx by Yas (Tigo Pesa) (25571XXXXXXX)"
        val toNamePattern = Regex(
            """to\s+([A-Z][A-Za-z\s]+?)(?:\s+-\s+[^(]+)?\s*\([^)]+\)""",
            RegexOption.IGNORE_CASE
        )
        toNamePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "paid TZS X to MERCHANT using" (card payment)
        // e.g., "paid TZS 8,900.00 to APPLECOMBILL using"
        val paidToPattern = Regex(
            """paid\s+TZS\s+[0-9,]+(?:\.[0-9]{2})?\s+to\s+([A-Za-z0-9\s]+?)(?:\s+using|\s+on)""",
            RegexOption.IGNORE_CASE
        )
        paidToPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: ATM withdrawal - "at ATM - LOCATION"
        if (message.contains("withdrawn", ignoreCase = true) && message.contains("ATM", ignoreCase = true)) {
            val atmPattern = Regex(
                """at\s+ATM\s+-?\s*([^u]+?)(?:\s+using|$)""",
                RegexOption.IGNORE_CASE
            )
            atmPattern.find(message)?.let { match ->
                val location = match.groupValues[1].trim()
                return if (location.isNotEmpty()) "ATM - $location" else "ATM Withdrawal"
            }
            return "ATM Withdrawal"
        }

        // Pattern 5: Simple "to NAME" without service info
        val simpleToPattern = Regex(
            """to\s+([A-Z][A-Za-z\s]+?)(?:\s+on\s+|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        simpleToPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Updated balance is TZS 175,000.00"
        val balancePattern = Regex(
            """Updated balance is TZS\s+([0-9,]+(?:\.[0-9]{2})?)""",
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
        // Pattern 1: Transaction ID at start (8-9 alphanumeric characters)
        // e.g., "0426JXCX Confirmed" or "10234C2WQ Confirmed"
        val txnIdPattern = Regex(
            """^([A-Z0-9]{8,9})\s+(?:Confirmed|Accepted)""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: TIPS reference in double notification
        val tipsPattern = Regex(
            """TIPS\s+Reference[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        tipsPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "card ending with 8318" or "card ending 1915"
        val cardPattern = Regex(
            """card\s+ending\s+(?:with\s+)?(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Must contain "Confirmed" or "Accepted"
        if (!lowerMessage.contains("confirmed") && !lowerMessage.contains("accepted")) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "you have received",
            "you have sent",
            "you have paid",
            "you have withdrawn",
            "updated balance"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Card transactions mention "card ending with" or "using your card"
        return lowerMessage.contains("card ending") ||
                lowerMessage.contains("using your card")
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")  // Remove trailing parentheses
            .replace(Regex("""\s+-\s+.*$"""), "")  // Remove " - Service" suffix
            .replace(Regex("""\s+on\s+\d{4}.*"""), "")  // Remove date suffix
            .replace(Regex("""\s*-\s*$"""), "")  // Remove trailing dash
            .trim()
    }
}
