package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Tigo Pesa / Mixx by Yas (Tanzania) mobile money SMS messages
 *
 * Handles formats like:
 * - "Cash-In of TSh 100,000 from Agent - LUCY SUKUM is successful. New balance is TSh 100,000"
 * - "You have sent TSh 25,000 with CashOut fee TSh 2,156 to 255713XXXXXX - BENEDICTA MREMA"
 * - "You have paid TSh 131,000 to DIAPERS AND WIPES SUPPLIERS. Charges TSh 2,000"
 * - "Transfer Successful. New balance is TSh 97,000. You have received TSh 97,000 from TIPS.Selcom_MFB"
 *
 * Key patterns:
 * - Sender: TIGOPESA, TIGOPESA(smsfp), MIXX BY YAS
 * - Currency: TSh (Tanzanian Shilling, same as TZS)
 * - Transaction ID: TxnId, TxnID, or Trnx ID patterns
 * - Fee breakdown: "(Fees TSh X, Levy TSh Y), VAT TSh Z"
 *
 * Country: Tanzania
 */
class TigoPesaParser : BankParser() {

    override fun getBankName() = "Tigo Pesa"

    override fun getCurrency() = "TZS"  // TSh is same as TZS (Tanzanian Shilling)

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("TIGOPESA") ||
                normalizedSender.contains("TIGO PESA") ||
                normalizedSender.contains("MIXX BY YAS") ||
                normalizedSender.contains("MIXXBYYAS") ||
                normalizedSender == "TIGO" ||
                // Handle sender format like "TIGOPESA(smsfp)"
                normalizedSender.startsWith("TIGOPESA")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "TSh 100,000" or "TSh100,000" (with or without space)
        val tshPattern = Regex(
            """TSh\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        // Find the first occurrence that's part of the main transaction (not fees)
        // Look for patterns like "sent TSh", "received TSh", "paid TSh", "Cash-In of TSh"
        val transactionAmountPatterns = listOf(
            Regex("""Cash-In of TSh\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""sent TSh\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""received TSh\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""paid TSh\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""You have sent TSh\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""You have paid TSh\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in transactionAmountPatterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        // Fallback: first TSh amount in message
        tshPattern.find(message)?.let { match ->
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
            // Cash-In = income (money received from agent)
            lowerMessage.contains("cash-in") -> TransactionType.INCOME

            // Received from TIPS or other sources = income
            lowerMessage.contains("you have received") -> TransactionType.INCOME
            lowerMessage.contains("received tsh") -> TransactionType.INCOME

            // Transfer successful with "received" = income
            lowerMessage.contains("transfer successful") &&
                lowerMessage.contains("received") -> TransactionType.INCOME

            // Sent/paid money = expense
            lowerMessage.contains("you have sent") -> TransactionType.EXPENSE
            lowerMessage.contains("you have paid") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "from Agent - NAME is successful" (Cash-In)
        val agentPattern = Regex(
            """from Agent\s*-?\s*([A-Z][A-Za-z\s]+?)\s+is\s+successful""",
            RegexOption.IGNORE_CASE
        )
        agentPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return "Agent - $merchant"
            }
        }

        // Pattern 2: "to PHONE - NAME" (sent money)
        // e.g., "to 255713XXXXXX - BENEDICTA MREMA"
        // Phone numbers can be masked with X characters
        val toPhoneNamePattern = Regex(
            """to\s+[\dX]+\s*-\s*([A-Z][A-Za-z\s]+?)(?:\.|Total|$)""",
            RegexOption.IGNORE_CASE
        )
        toPhoneNamePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: "paid TSh X to MERCHANT_NAME" (merchant payment)
        // e.g., "paid TSh 131,000 to DIAPERS AND WIPES SUPPLIERS"
        val paidToPattern = Regex(
            """paid\s+TSh\s*[0-9,]+(?:\.[0-9]{2})?\s+to\s+([A-Za-z0-9\s&]+?)(?:\.|Charges|$)""",
            RegexOption.IGNORE_CASE
        )
        paidToPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "from TIPS.SERVICE_NAME" (inter-operator transfer)
        // e.g., "from TIPS.Selcom_MFB.2.Tigo"
        val tipsPattern = Regex(
            """from\s+(TIPS\.[A-Za-z0-9_.]+)""",
            RegexOption.IGNORE_CASE
        )
        tipsPattern.find(message)?.let { match ->
            val tipsSource = match.groupValues[1]
            // Clean up TIPS source name
            return when {
                tipsSource.contains("Selcom", ignoreCase = true) -> "Selcom (TIPS Transfer)"
                tipsSource.contains("NMB", ignoreCase = true) -> "NMB Bank (TIPS Transfer)"
                tipsSource.contains("CRDB", ignoreCase = true) -> "CRDB Bank (TIPS Transfer)"
                else -> "TIPS Transfer"
            }
        }

        // Pattern 5: Simple "to NAME" at end
        val simpleToPattern = Regex(
            """to\s+([A-Z][A-Za-z\s]+?)(?:\.|,|Charges|Total|$)""",
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
        // Pattern 1: "New balance is TSh 481,801"
        val newBalancePattern = Regex(
            """New balance is TSh\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        newBalancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Your New balance is TSh 467,372"
        val yourNewBalancePattern = Regex(
            """Your New balance is TSh\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        yourNewBalancePattern.find(message)?.let { match ->
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
        // Pattern 1: "TxnId: 13411949026"
        val txnIdPattern = Regex(
            """TxnId:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "TxnID: 27755640833"
        val txnIDPattern = Regex(
            """TxnID:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        txnIDPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: "Trnx ID: 63425443091"
        val trnxIdPattern = Regex(
            """Trnx ID:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        trnxIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 4: TIPS reference pattern "with TxnId: 25693126312543. 035_12307E6LF"
        // The second part after period is the TIPS reference
        val tipsRefPattern = Regex(
            """with TxnId:\s*\d+\.\s*([A-Z0-9_]+)""",
            RegexOption.IGNORE_CASE
        )
        tipsRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Must contain TSh currency indicator (Tigo Pesa specific)
        if (!lowerMessage.contains("tsh")) {
            return false
        }

        // Must contain transaction keywords or status indicators
        val transactionKeywords = listOf(
            "cash-in",
            "you have sent",
            "you have paid",
            "you have received",
            "transfer successful",
            "is successful",
            "new balance"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")  // Remove trailing parentheses
            .replace(Regex("""\s+on\s+\d{2}/.*"""), "")  // Remove date suffix
            .replace(Regex("""\s*-\s*$"""), "")  // Remove trailing dash
            .replace(Regex("""^\s*-\s*"""), "")  // Remove leading dash
            .replace(Regex("""\s+$"""), "")  // Remove trailing whitespace
            .trim()
    }
}
