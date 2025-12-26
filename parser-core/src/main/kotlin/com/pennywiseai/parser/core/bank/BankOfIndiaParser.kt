package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Bank of India (BOI) SMS messages.
 *
 * Handles formats like:
 * - "Rs.200.00 debited A/cXX5468 and credited to SAI MISAL via UPI Ref No 315439383341 on 23Aug25. Call 18001031906, if not done by you. -BOI"
 * - Other BOI transaction formats
 */
class BankOfIndiaParser : BaseIndianBankParser() {

    override fun getBankName() = "Bank of India"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()

        // Direct sender IDs
        val boiSenders = setOf(
            "BOIIND",
            "BOIBNK"
        )

        if (normalizedSender in boiSenders) return true

        // DLT patterns (XX-BOIIND-S/T or XX-BOIBNK-S/T format)
        return normalizedSender.matches(Regex("^[A-Z]{2}-BOIIND-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOIBNK-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOI-[ST]$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOIIND$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOIBNK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-BOI$")) ||
                normalizedSender.matches(Regex("^BK-BOIIND.*$")) ||
                normalizedSender.matches(Regex("^JD-BOIIND.*$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: Rs.200.00 debited/credited
        val rsPattern = Regex(
            """Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+(?:debited|credited)""",
            RegexOption.IGNORE_CASE
        )
        rsPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: INR format
        val inrPattern = Regex(
            """INR\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+(?:debited|credited)""",
            RegexOption.IGNORE_CASE
        )
        inrPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: withdrawn Rs 500
        val withdrawnPattern =
            Regex("""withdrawn\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        withdrawnPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // BOI specific: Cash deposits should be INCOME (not investment)
        if (lowerMessage.contains("deposited in your account") ||
            lowerMessage.contains("cash") && lowerMessage.contains("deposited")
        ) {
            return TransactionType.INCOME
        }

        // Check for investment transactions (including UPI Mandate for mutual funds)
        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }

        // UPI Mandate for mutual funds/investments
        if (lowerMessage.contains("mandate") &&
            (lowerMessage.contains("mutual fund") ||
                    lowerMessage.contains("iccl") ||
                    lowerMessage.contains("groww") ||
                    lowerMessage.contains("zerodha") ||
                    lowerMessage.contains("kuvera") ||
                    lowerMessage.contains("paytm money"))
        ) {
            return TransactionType.INVESTMENT
        }

        // BOI specific: "debited A/c... and credited to" pattern indicates expense
        if (lowerMessage.contains("debited") && lowerMessage.contains("and credited to")) {
            return TransactionType.EXPENSE
        }

        // BOI specific: "credited A/c... and debited from" pattern indicates income
        if (lowerMessage.contains("credited") && lowerMessage.contains("and debited from")) {
            return TransactionType.INCOME
        }

        // Standard patterns
        return super.extractTransactionType(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern for cash deposit via Cash Acceptor Machine
        if (message.contains("Cash Acceptor Machine", ignoreCase = true) ||
            (message.contains("cash", ignoreCase = true) && message.contains(
                "deposited",
                ignoreCase = true
            ))
        ) {
            return "Cash Deposit"
        }

        // Pattern for UPI Mandate execution: "towards MERCHANT for Mandate Created via PLATFORM"
        if (message.contains("Mandate", ignoreCase = true) && message.contains(
                "towards",
                ignoreCase = true
            )
        ) {
            // Try to extract platform first (e.g., "via GROWW")
            val viaPattern = Regex("""via\s+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
            viaPattern.find(message)?.let { match ->
                val platform = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(platform)) {
                    return platform
                }
            }

            // If no platform found, extract merchant from "towards MERCHANT for"
            val towardsPattern =
                Regex("""towards\s+([^,\n]+?)(?:\s+for|\s*,|$)""", RegexOption.IGNORE_CASE)
            towardsPattern.find(message)?.let { match ->
                val merchantInfo = match.groupValues[1].trim()
                // Clean up the merchant name (e.g., "ICCL - Mutual Funds - Autopa" -> "ICCL - Mutual Funds")
                val cleanedMerchant = merchantInfo
                    .replace(Regex("""\s*-\s*Autopa.*$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (isValidMerchantName(cleanedMerchant)) {
                    return cleanMerchantName(cleanedMerchant)
                }
            }
        }

        // Pattern 1: "credited to MERCHANT via UPI" (for debits)
        val creditedToPattern = Regex(
            """credited\s+to\s+([^.\n]+?)(?:\s+via|\s+Ref|\s+on|$)""",
            RegexOption.IGNORE_CASE
        )
        creditedToPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "debited from MERCHANT via UPI" (for credits)
        val debitedFromPattern = Regex(
            """debited\s+from\s+([^.\n]+?)(?:\s+via|\s+Ref|\s+on|$)""",
            RegexOption.IGNORE_CASE
        )
        debitedFromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: ATM withdrawal
        if (message.contains("ATM", ignoreCase = true) || message.contains(
                "withdrawn",
                ignoreCase = true
            )
        ) {
            val atmPattern = Regex(
                """(?:ATM|withdrawn)\s+(?:at\s+)?([^.\n]+?)(?:\s+on|\s+Ref|$)""",
                RegexOption.IGNORE_CASE
            )
            atmPattern.find(message)?.let { match ->
                val location = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(location)) {
                    return "ATM - $location"
                }
            }
            return "ATM"
        }

        // Pattern 4: "towards MERCHANT" (generic, but not for Mandate messages which are handled above)
        if (!message.contains("Mandate", ignoreCase = true)) {
            val towardsPattern =
                Regex("""towards\s+([^.\n]+?)(?:\s+via|\s+Ref|\s+on|$)""", RegexOption.IGNORE_CASE)
            towardsPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 5: "to MERCHANT" (generic)
        val toPattern =
            Regex("""to\s+([^.\n]+?)(?:\s+via|\s+Ref|\s+on|$)""", RegexOption.IGNORE_CASE)
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 6: "from MERCHANT" (generic)
        val fromPattern =
            Regex("""from\s+([^.\n]+?)(?:\s+via|\s+Ref|\s+on|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: A/cXX5468 or A/c XX5468 (BOI format)
        val accountPattern = Regex("""A/c\s*(?:XX|X\*+)?(\d{4})""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Account ending 1234
        val endingPattern = Regex("""(?:Account|A/c)\s+ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        endingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: A/c No. XX1234
        val accountNoPattern =
            Regex("""A/c\s+No\.?\s*(?:XX|X\*+)?(\d{4})""", RegexOption.IGNORE_CASE)
        accountNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: Ref No 315439383341 (BOI format)
        val refNoPattern = Regex("""Ref\s+No\.?\s*(\d+)""", RegexOption.IGNORE_CASE)
        refNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Reference: 123456
        val referencePattern = Regex("""Reference[:\s]+(\w+)""", RegexOption.IGNORE_CASE)
        referencePattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: Txn ID/Txn#
        val txnPattern = Regex("""Txn\s*(?:ID|#)[:\s]*(\w+)""", RegexOption.IGNORE_CASE)
        txnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 4: UPI reference
        val upiPattern = Regex("""UPI[:\s]+(\d+)""", RegexOption.IGNORE_CASE)
        upiPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: Bal: Rs 1000.00
        val balRsPattern =
            Regex("""Bal[:\s]+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        balRsPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Available Balance: Rs 1000.00
        val availableBalPattern = Regex(
            """Available\s+Balance[:\s]+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        availableBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: Avl Bal Rs 1000.00
        val avlBalPattern = Regex(
            """Avl\s+Bal[:\s]+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        avlBalPattern.find(message)?.let { match ->
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

        // Skip future debit notifications
        if (lowerMessage.contains("will be")) {
            return false
        }

        // Skip if it contains the customer care message but ensure it's a transaction
        if (lowerMessage.contains("call") && lowerMessage.contains("if not done by you")) {
            // This is likely a transaction message with a security notice
            // Check if it contains transaction keywords
            if (lowerMessage.contains("debited") || lowerMessage.contains("credited") ||
                lowerMessage.contains("withdrawn") || lowerMessage.contains("transferred")
            ) {
                return true
            }
        }

        // Skip OTP and verification messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")
        ) {
            return false
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }
}
