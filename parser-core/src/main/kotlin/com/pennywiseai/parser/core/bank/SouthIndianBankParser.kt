package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * South Indian Bank specific parser.
 * Handles South Indian Bank's unique message formats including:
 * - UPI debit/credit transactions
 * - Balance updates
 * - Card transactions
 */
class SouthIndianBankParser : BaseIndianBankParser() {

    override fun getBankName() = "South Indian Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        // Common South Indian Bank sender IDs
        val sibSenders = setOf(
            "SIBSMS",
            "AD-SIBSMS",
            "CP-SIBSMS",
            "SIBSMS-S",
            "AD-SIBSMS-S",
            "CP-SIBSMS-S",
            "SOUTHINDIANBANK",
            "SIBBANK"
        )

        // Direct match
        if (upperSender in sibSenders) return true

        // Check for patterns with suffixes
        if (upperSender.contains("SIBSMS")) return true
        if (upperSender.contains("SIBBANK")) return true

        // DLT patterns
        return upperSender.startsWith("AD-SIB") ||
                upperSender.startsWith("CP-SIB") ||
                upperSender.startsWith("VM-SIB")
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Check if it's a transaction message
        if (!isTransactionMessage(smsBody)) {
            return null
        }

        // Extract amount
        val amount = extractAmount(smsBody) ?: return null

        // Extract transaction type
        val transactionType = extractTransactionType(smsBody) ?: return null

        // Extract other details
        val merchant = extractMerchant(smsBody, sender) ?: "Unknown"
        val reference = extractReference(smsBody)
        val accountLast4 = extractAccountLast4(smsBody)
        val balance = extractBalance(smsBody)

        // Parse date/time from message if available, otherwise use SMS timestamp
        val dateTime = extractDateTime(smsBody) ?: LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        )

        return ParsedTransaction(
            amount = amount,
            type = transactionType,
            merchant = merchant,
            reference = reference,
            accountLast4 = accountLast4,
            balance = balance,
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName()
        )
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern for "Rs.42225.06" or "Rs.42225.06," (with comma after)
        val patterns = listOf(
            Regex("""(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // For IMPS transactions, extract from "Info: IMPS/xxx/reference/MERCHANT" format
        if (message.contains("IMPS", ignoreCase = true) && message.contains(
                "Info:",
                ignoreCase = true
            )
        ) {
            // Pattern for "Info: IMPS/FDRL/528005821348/EPIFI ACCOUN." - capture everything up to period
            val impsPattern = Regex("""Info:\s*IMPS/[^/]+/[^/]+/([^.]+)""", RegexOption.IGNORE_CASE)
            impsPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // For UPI transactions, try to extract UPI ID or merchant name
        if (message.contains("UPI", ignoreCase = true)) {
            // Pattern for "Info:UPI/IPOS/number/MERCHANT NAME on" format
            val infoPattern =
                Regex("""Info:UPI/[^/]+/[^/]+/([^/]+?)\s+on""", RegexOption.IGNORE_CASE)
            infoPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }

            // Check for "to" pattern (e.g., "to merchant@upi")
            // Only match if it appears early in the message to avoid matching footer phone numbers
            val messagePrefix = message.take(200)  // Only look in first 200 chars
            val toPattern = Regex("""to\s+([^,\s]+@[^\s,]+)""", RegexOption.IGNORE_CASE)
            toPattern.find(messagePrefix)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }

            // Check for "from" pattern for incoming transfers
            if (message.contains("credit", ignoreCase = true)) {
                val fromPattern = Regex("""from\s+([^,\s]+@[^\s,]+)""", RegexOption.IGNORE_CASE)
                fromPattern.find(messagePrefix)?.let { match ->
                    val merchant = match.groupValues[1].trim()
                    if (merchant.isNotEmpty()) {
                        return cleanMerchantName(merchant)
                    }
                }
                // Default to UPI Credit if no merchant found
                return "UPI Credit"
            }

            // Default to UPI Transaction for UPI messages (if not credit)
            return "UPI Transaction"
        }

        // For debit/credit transactions - merchant between amount and balance
        // Only apply this if NOT a UPI transaction (already handled above)
        if ((message.contains("debit", ignoreCase = true) ||
                    message.contains("credit", ignoreCase = true)) &&
            !message.contains("UPI", ignoreCase = true)) {
            // Pattern for "DEBIT:Rs.983.75 MERCHANT NAME Bal:Rs.79184.67"
            val debitCreditPattern = Regex(
                """(?:DEBIT|CREDIT)[:\s]*Rs\.?\s*[0-9,]+(?:\.\d{2})?\s+([A-Z\s]+?)\s+(?:Bal|Available)""",
                RegexOption.IGNORE_CASE
            )
            debitCreditPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && merchant.length > 2) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // For ATM withdrawals
        if (message.contains("ATM", ignoreCase = true) ||
            message.contains("withdrawn", ignoreCase = true)
        ) {
            return "ATM"
        }

        // For card transactions
        if (message.contains("card", ignoreCase = true)) {
            // Try to extract merchant after "at"
            val atPattern = Regex("""at\s+([^,\n]+?)(?:\s+on|\s*,|$)""", RegexOption.IGNORE_CASE)
            atPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        return super.extractMerchant(message, sender)
    }
    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Debit keywords
            lowerMessage.contains("debit") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("transfer to") -> TransactionType.EXPENSE

            // Credit keywords
            lowerMessage.contains("credit") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("transfer from") -> TransactionType.INCOME
            lowerMessage.contains("cashback") -> TransactionType.INCOME

            else -> null
        }
    }

    override fun extractReference(message: String): String? {
        // Pattern for IMPS reference in "Info: IMPS/xxx/reference/merchant" format
        if (message.contains("IMPS", ignoreCase = true) && message.contains(
                "Info:",
                ignoreCase = true
            )
        ) {
            val impsRefPattern = Regex("""Info:\s*IMPS/[^/]+/([^/]+)/""", RegexOption.IGNORE_CASE)
            impsRefPattern.find(message)?.let { match ->
                val ref = match.groupValues[1].trim()
                if (ref.isNotEmpty()) {
                    return ref
                }
            }
        }

        // Pattern for RRN (e.g., "RRN:523273398527" or "RRN:567304295699.")
        val rrnPattern = Regex("""RRN[:\s]*(\d{12})""", RegexOption.IGNORE_CASE)
        rrnPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Pattern for reference number
        val refPattern = Regex("""Ref(?:erence)?[:\s]*([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        refPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        return super.extractReference(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern for "A/c X1234" or "A/c XX1234" or "A/c XXX1234"
        val patterns = listOf(
            Regex("""A/c\s+[X*]*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""Account\s+[X*]*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""from\s+[X*]*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""to\s+[X*]*(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern for "Bal:Rs.1234.17" or "Balance:Rs.1234.17" or "Final balance is Rs.1234.17"
        val patterns = listOf(
            Regex(
                """Final\s+balance\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""Bal(?:ance)?[:\s]*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex(
                """Available\s+Bal(?:ance)?[:\s]*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""Avl\s+Bal[:\s]*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractBalance(message)
    }

    /**
     * Extract date and time from message.
     * Format: "20-08-25 12:13:23" (YY-MM-DD HH:MM:SS)
     */
    private fun extractDateTime(message: String): LocalDateTime? {
        // Pattern for "20-08-25 12:13:23" format
        val dateTimePattern = Regex("""(\d{2}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})""")
        dateTimePattern.find(message)?.let { match ->
            val dateStr = match.groupValues[1]
            val timeStr = match.groupValues[2]

            return try {
                // Parse YY-MM-DD format
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    val year = 2000 + parts[0].toInt()
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()

                    // Parse HH:MM:SS format
                    val timeParts = timeStr.split(":")
                    if (timeParts.size == 3) {
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        val second = timeParts[2].toInt()

                        LocalDateTime.of(year, month, day, hour, minute, second)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code") ||
            lowerMessage.contains("offer") ||
            lowerMessage.contains("discount")
        ) {
            return false
        }

        // Skip UPI auto-pay scheduled reminders
        if (lowerMessage.contains("upi auto pay") &&
            lowerMessage.contains("is scheduled on")
        ) {
            return false
        }

        // Check for transaction keywords
        val transactionKeywords = listOf(
            "debit", "credit", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid",
            "purchase", "refund", "cashback", "upi"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}