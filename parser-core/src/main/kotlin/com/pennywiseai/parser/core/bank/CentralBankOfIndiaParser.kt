package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Central Bank of India (CBoI) SMS messages
 */
class CentralBankOfIndiaParser : BankParser() {

    override fun getBankName() = "Central Bank of India"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("CENTBK") ||
                normalizedSender.contains("CBOI") ||
                normalizedSender.contains("CENTRALBANK") ||
                normalizedSender.contains("CENTRAL") ||
                // DLT patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-CENTBK-[A-Z]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-CBOI-[A-Z]$"))
    }

    override fun parse(
        smsBody: String,
        sender: String,
        timestamp: Long
    ): ParsedTransaction? {
        if (!canHandle(sender)) return null
        if (!isTransactionMessage(smsBody)) return null

        val amount = extractAmount(smsBody) ?: return null
        val transactionType = extractTransactionType(smsBody) ?: return null
        val merchant = extractMerchant(smsBody, sender) ?: "Unknown"

        return ParsedTransaction(
            amount = amount,
            type = transactionType,
            merchant = merchant,
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            reference = extractReference(smsBody),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName()
        )
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: Credited by Rs.50.00
        // Pattern 2: Debited by Rs.100.50
        val pattern1 = Regex(
            """(?:Credited|Debited)\s+by\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )

        pattern1.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Rs.XXX credited/debited
        val pattern2 = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+(?:credited|debited)""",
            RegexOption.IGNORE_CASE
        )

        pattern2.find(message)?.let { match ->
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
        // Pattern 1: "By.NAME" or "By NAME" for NEFT/transfer credits (before bank suffix like -CBoI)
        val byPattern = Regex(
            """By[.\s]+(.+?)(?:-CBoI|-CBOI|-CENTBK|$)""",
            RegexOption.IGNORE_CASE
        )
        byPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "from [NAME]" for credits
        val fromPattern = Regex(
            """from\s+([A-Z0-9]+|[^\s]+?)(?:\s+via|\s+Ref|\s+\.|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Handle masked UPI IDs
            if (merchant.contains("X")) {
                return "UPI Transfer"
            }
            return cleanMerchantName(merchant)
        }

        // Pattern 3: "to [NAME]" for debits
        val toPattern = Regex(
            """to\s+([^\s]+?)(?:\s+via|\s+Ref|\s+\.|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: via UPI
        if (message.contains("via UPI", ignoreCase = true)) {
            if (message.contains("Credited", ignoreCase = true)) {
                return "UPI Credit"
            } else if (message.contains("Debited", ignoreCase = true)) {
                return "UPI Payment"
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: account XX3113 (last 4 visible)
        val pattern1 = Regex(
            """account\s+[X*]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        pattern1.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: A/C ending XXXX
        val pattern2 = Regex(
            """A/C\s+ending\s+[X*]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        pattern2.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: Total Bal Rs.0000.99 CR
        val totalBalPattern = Regex(
            """Total\s+Bal\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+(CR|DR)""",
            RegexOption.IGNORE_CASE
        )
        totalBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            val type = match.groupValues[2].uppercase()
            return try {
                val balance = BigDecimal(balanceStr)
                // If DR (debit), make it negative
                if (type == "DR") balance.negate() else balance
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Clear Bal Rs.XXX CR
        val clearBalPattern = Regex(
            """Clear\s+Bal\s+Rs\.?\s*([\d,]+(?:\.\d{2})?)\s+(CR|DR)""",
            RegexOption.IGNORE_CASE
        )
        clearBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            val type = match.groupValues[2].uppercase()
            return try {
                val balance = BigDecimal(balanceStr)
                if (type == "DR") balance.negate() else balance
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern: Ref No.541986000003
        val pattern = Regex(
            """Ref\s+No\.?\s*(\w+)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for CBoI-specific transaction keywords
        if ((lowerMessage.contains("credited by") ||
                    lowerMessage.contains("debited by")) &&
            lowerMessage.contains("bal")
        ) {
            return true
        }

        // Check for signature
        if (lowerMessage.contains("-cboi")) {
            return lowerMessage.contains("credited") ||
                    lowerMessage.contains("debited")
        }

        return super.isTransactionMessage(message)
    }
}