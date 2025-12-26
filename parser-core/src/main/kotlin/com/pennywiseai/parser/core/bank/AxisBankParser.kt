package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Axis Bank SMS messages
 */
class AxisBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Axis Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("AXIS BANK") ||
                normalizedSender.contains("AXISBANK") ||
                normalizedSender.contains("AXISBK") ||
                normalizedSender.contains("AXISB") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-AXISBK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AXISBANK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AXIS-S$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-AXISBK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AXIS$")) ||
                // Direct sender IDs
                normalizedSender == "AXISBK" ||
                normalizedSender == "AXISBANK" ||
                normalizedSender == "AXIS"
    }

    override fun extractAmount(message: String): BigDecimal? {
        val inrDebitPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+debited""",
            RegexOption.IGNORE_CASE
        )
        inrDebitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        val inrCreditPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+credited""",
            RegexOption.IGNORE_CASE
        )
        inrCreditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        val paymentPattern = Regex(
            """Payment\s+of\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        paymentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // ATM withdrawal detection
        // Pattern: "debited from A/c no. XX589034 on AXIS BANK L" or similar
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("debited from a/c no.") &&
            lowerMessage.contains(" on axis bank")) {
            return "ATM"
        }

        // Also check for explicit ATM mentions
        if ((lowerMessage.contains("atm") || lowerMessage.contains("cash withdrawal")) &&
            lowerMessage.contains("debited")) {
            return "ATM"
        }

        // Debit card transaction pattern (Issue #120)
        // Pattern: "debited from A/c no. XXxxxxy on BURGRILL 04-12-2025 13:13:27 IST"
        // Extract merchant name between "on" and the date pattern
        val debitCardPattern = Regex(
            """debited from A/c no\. [^\s]+ on ([^0-9]+?)(?:\d{2}-\d{2}-\d{4})""",
            RegexOption.IGNORE_CASE
        )
        debitCardPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Credit card "Spent" transactions with merchant on separate line
        // Format 1: "Spent INR 131\nAxis Bank Card no. XX0818\n05-10-25 09:43:27 IST\nSwiggy Limi\nAvl Limit:"
        // Format 2: "Spent\nCard no. XX7441\nINR 562\n01-09-25 12:04:18\nAVENUE SUPE\nAvl Lmt"
        val spentPatternWithIST = Regex(
            """Spent[\s\S]*?IST\s*\n\s*([^\n]+?)(?:\s*\n|\s*Avl Limit:|\s*Avl Lmt|\s*Not you?)""",
            RegexOption.IGNORE_CASE
        )
        spentPatternWithIST.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            // Clean up truncated merchant names by removing common truncation patterns
            merchant = merchant.replace(Regex("""\s+Limi$"""), "")  // "Swiggy Limi" -> "Swiggy"
            merchant = merchant.replace(Regex("""\s+Pay$"""), "")   // "Amazon Pay" -> "Amazon"
            merchant = merchant.replace(Regex("""\s+SUPE$"""), "")  // "AVENUE SUPE" -> "AVENUE"

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Alternative pattern without IST (for formats that use different time formats)
        val spentPatternWithTime = Regex(
            """Spent[\s\S]*?\d{2}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*\n\s*([^\n]+?)(?:\s*\n|\s*Avl Limit:|\s*Avl Lmt|\s*Not you?)""",
            RegexOption.IGNORE_CASE
        )
        spentPatternWithTime.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            // Clean up truncated merchant names
            merchant = merchant.replace(Regex("""\s+Limi$"""), "")
            merchant = merchant.replace(Regex("""\s+Pay$"""), "")
            merchant = merchant.replace(Regex("""\s+SUPE$"""), "")

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        val upiMerchantPattern = Regex(
            """UPI/[^/]+/[^/]+/([^\n]+?)(?:\s*Not you|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        upiMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        val upiPersonPattern = Regex(
            """UPI/P2A/[^/]+/([^\n]+?)(?:\s*Not you|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        upiPersonPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        val infoPattern = Regex(
            """Info\s*[-–]\s*([^.\n]+?)(?:\.\s*Chk|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        infoPattern.find(message)?.let { match ->
            val info = match.groupValues[1].trim()
            return when {
                info.contains("SALARY", ignoreCase = true) -> "Salary"
                else -> cleanMerchantName(info)
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "A/c no. XXNNNN" or "A/c no. XXxxxxy" - extract everything after "A/c no."
        // Handle both uppercase X and lowercase x patterns
        val acNoPattern = Regex(
            """A/c\s+no\.\s+([X\*xX]+[a-zA-Z\d]+)""",
            RegexOption.IGNORE_CASE
        )
        acNoPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            // Extract all alphanumeric characters (to preserve patterns like "xxxy")
            val digitsAndLetters = accountStr.filter { it.isLetterOrDigit() }

            // If it contains lowercase letters at the end (like "xxxy"), return last 4 chars as-is
            if (digitsAndLetters.any { it in 'a'..'z' }) {
                return if (digitsAndLetters.length >= 4) {
                    digitsAndLetters.takeLast(4).lowercase()
                } else {
                    digitsAndLetters.lowercase()
                }
            }

            // Otherwise, extract only digits (for patterns like XX1234)
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else {
                digitsOnly
            }
        }

        // Pattern 2: "Card no. XXNNNN" - for credit card spending messages
        val cardNoPattern = Regex(
            """Card\s+no\.\s+([X\*]*\d+)""",
            RegexOption.IGNORE_CASE
        )
        cardNoPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else {
                digitsOnly
            }
        }

        // Pattern 3: "Credit Card XXNNNN"
        val creditCardPattern = Regex(
            """Credit\s+Card\s+([X\*]*\d+)""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else {
                digitsOnly
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        val upiRefPattern = Regex(
            """UPI/[^/]+/([0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip Axis-specific payment confirmation messages (payment TO card, not spending)
        if (lowerMessage.contains("payment") &&
            lowerMessage.contains("has been received") &&
            lowerMessage.contains("towards your axis bank")
        ) {
            return false
        }

        // Base class handles common payment reminders and other non-transaction messages
        return super.isTransactionMessage(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Credit card transactions: if message contains "Avl Limit" or "Avl Lmt", it's a credit card
        if (lowerMessage.contains("avl limit") || lowerMessage.contains("avl lmt")) {
            return TransactionType.CREDIT
        }

        // Explicit credit card mention
        if ((lowerMessage.contains("credit card") || lowerMessage.contains(" cc ")) &&
            (lowerMessage.contains("debited") || lowerMessage.contains("spent"))
        ) {
            return TransactionType.CREDIT
        }

        // Fall back to base class for standard checks
        return super.extractTransactionType(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Axis Bank specific patterns using "INR" instead of "Rs"
        val axisCreditLimitPatterns = listOf(
            // "Avl Limit: INR 217162.72"
            Regex("""Avl\s+Limit:?\s*INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Avl Lmt INR 4632.87"
            Regex("""Avl\s+Lmt\s+INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Available limit INR 111,111.89"
            Regex("""Available\s+limit:?\s*INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in axisCreditLimitPatterns) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(limitStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        // Fall back to base class patterns (for Rs-based formats)
        return super.extractAvailableLimit(message)
    }
}