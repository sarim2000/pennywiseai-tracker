package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Parser for Punjab National Bank (PNB) SMS messages
 */
class PNBBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Punjab National Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("PUNJAB NATIONAL BANK") || // RCS sender (any case)
                normalizedSender.contains("PNBBNK") ||
                normalizedSender.contains("PUNBN") ||
                normalizedSender.contains("PNBSMS") || // Matches V?-PNBSMS-S
                normalizedSender.matches(Regex("^[A-Z]{2}-PNBBNK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-PNB-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-PNBBNK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-PNB$")) ||
                normalizedSender == "PNBBNK" ||
                normalizedSender == "PNB"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Normalize Unicode text for RCS messages
        val normalizedBody = normalizeUnicodeText(smsBody)

        // Use normalized body for parsing
        return super.parse(normalizedBody, sender, timestamp)
    }

    private fun normalizeUnicodeText(text: String): String {
        // Use Java's built-in normalizer to decompose Unicode
        // NFKD = Compatibility Decomposition
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("[^\\p{ASCII}]"), "") // Keep only ASCII
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Handle explicit debit of initial amount in auto-pay messages
        val initialDebitPattern = Regex(
            """initial\s+amount\s+of\s+(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?)\s+has\s+been\s+debited""",
            RegexOption.IGNORE_CASE
        )
        initialDebitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Handle UPI-Mandate creation amount
        val mandatePattern = Regex(
            """UPI-Mandate\s+is\s+successfully\s+created.*for\s+(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        mandatePattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Handle debit patterns - both "Rs." and "INR" formats
        // Expanded to handle optional space after currency and different spacing
        val debitPattern = Regex(
            """debited\s+with\s+(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?)""",
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

        // Handle credit patterns - both "Rs." and "INR" formats
        val creditPattern = Regex(
            """(?:(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?)\s+(?:has\s+been\s+)?credited|credited\s+(?:with\s+)?(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?))""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            // Try to get the amount from either capture group (pattern 1 or pattern 2)
            val amount =
                (if (match.groupValues[1].isNotEmpty()) match.groupValues[1] else match.groupValues[2])
                    .replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Note: Removed balance pattern - balance should never be used as transaction amount
        // Balance is extracted separately by extractBalance() method

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Explicitly handle Auto-Pay and UPI-Mandate as EXPENSE if they imply a payment/debit
        if (lowerMessage.contains("auto pay facility") || lowerMessage.contains("upi-mandate")) {
            return TransactionType.EXPENSE
        }

        return super.extractTransactionType(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Extract merchant from Auto-Pay activation: from Google Clouds
        val fromMerchantPattern = Regex(
            """auto\s+pay.*?activated.*?from\s+([^.]+?)(?:\s+An\s+initial|\.|$)""",
            RegexOption.IGNORE_CASE
        )
        fromMerchantPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Extract merchant from UPI-Mandate: towards Google
        val towardsPattern = Regex(
            """UPI-Mandate.*towards\s+([^\s]+)\s+for""",
            RegexOption.IGNORE_CASE
        )
        towardsPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Extract card info if available: thru card XX9239
        val cardPattern = Regex("""thru\s+card\s+([X\*]+\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return "Card ${match.groupValues[1]}"
        }

        val fromPattern = Regex(
            """From\s+([^/]+)/""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        if (message.contains("PNB ATM", ignoreCase = true)) {
            return "PNB ATM Withdrawal"
        }

        if (message.contains("NEFT", ignoreCase = true)) {
            return "NEFT Transfer"
        }

        if (message.contains("UPI", ignoreCase = true)) {
            return "UPI Transaction"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Handle variations: Ac, A/c, Card followed by X/dots/spaces and then digits (4 to 16)
        val acPattern = Regex(
            """(?:A/c(?:\s*No\.)?|Ac|Card)\s*(?:[X\*]+)?(\d{4,16})""",
            RegexOption.IGNORE_CASE
        )
        acPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        return null
    }

    override fun extractReference(message: String): String? {
        val neftRefPattern = Regex(
            """ref\s+no\.\s+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        neftRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        val upiRefPattern = Regex(
            """UPI:\s*([0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Handle "Aval Bal", "Avl Bal", "Bal" followed by currency and amount, usually ending with CR/DR
        val balPattern = Regex(
            """(?:Aval\s+Bal|Avl\s+Bal|Bal)\s*(?:INR\s*|Rs\.?\s*)?([0-9,]+(?:\.\d{2})?)(?:\s+(?:CR|DR))?""",
            RegexOption.IGNORE_CASE
        )
        balPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fallback for just "Bal XXXX.XX CR"
        val simpleBalPattern = Regex(
            """Bal\s*([0-9,]+(?:\.\d{2})?)\s+(?:CR|DR)""",
            RegexOption.IGNORE_CASE
        )
        simpleBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("auto pay facility") && lowerMessage.contains("debited")) {
            return true
        }

        if (lowerMessage.contains("upi-mandate") && lowerMessage.contains("successfully created")) {
            return true
        }

        if (lowerMessage.contains("register for e-statement")) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}