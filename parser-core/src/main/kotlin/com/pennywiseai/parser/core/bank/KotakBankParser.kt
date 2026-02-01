package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Kotak Bank specific parser.
 * Handles Kotak Bank's unique message formats including:
 * - UPI transactions with recipient details
 * - Standard debit/credit messages
 * - Card transactions
 */
class KotakBankParser : BankParser() {

    override fun getBankName() = "Kotak Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()

        // DLT patterns for Kotak Bank
        return normalizedSender.matches(Regex("^[A-Z]{2}-KOTAKB-[ST]$"))
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Credit card merchant pattern: "on DD-MON-YYYY at MERCHANT. Avl limit"
        val cardMerchantPattern = Regex(
            """on\s+\d{1,2}-\w{3}-\d{2,4}\s+at\s+([^.]+?)(?:\.|\s+Avl|$)""",
            RegexOption.IGNORE_CASE
        )
        cardMerchantPattern.find(message)?.let { match ->
            val rawMerchant = match.groupValues[1].trim()
            val merchant = cleanKotakCardMerchant(rawMerchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 1: "Sent Rs.X from Kotak Bank AC XXXX to merchant@bank on"
        // Pattern 2: "Received Rs.X in your Kotak Bank AC XXXX from merchant@bank on"

        // Try "to" pattern for sent transactions
        val toPattern = Regex("to\\s+([^\\s]+@[^\\s]+)\\s+on", RegexOption.IGNORE_CASE)
        val fromPattern = Regex("from\\s+([^\\s]+@[^\\s]+)\\s+on", RegexOption.IGNORE_CASE)

        // Check both patterns
        val upiMatch = toPattern.find(message) ?: fromPattern.find(message)

        upiMatch?.let { match ->
            val upiId = match.groupValues[1].trim()

            // Extract merchant name from UPI ID
            val merchantName = when {
                // Handle "upiXXX@bank" format - remove "upi" prefix
                upiId.startsWith("upi", ignoreCase = true) -> {
                    val name = upiId.substring(3).substringBefore("@")
                    if (name.isNotEmpty()) cleanMerchantName(name) else null
                }
                // Handle other UPI IDs - extract username part
                else -> {
                    val name = upiId.substringBefore("@")
                    val bankCode = upiId.substringAfter("@")

                    when {
                        // Check if this is a generated payment app QR code ID
                        isPaymentAppGeneratedId(name) -> {
                            // For generated IDs like "paytmqr...", extract merchant from domain
                            extractMerchantFromBankCode(bankCode) ?: cleanMerchantName(name)
                        }
                        // Valid UPI ID with meaningful content (not just all digits)
                        name.isNotEmpty() && (!name.all { it.isDigit() } || name.contains("-") || name.contains(
                            "_"
                        )) -> {
                            // For phone numbers or IDs with separators, try to get meaningful merchant name
                            if (name.all { it.isDigit() || it == '-' || it == '_' }) {
                                // This looks like a phone number or ID, try to extract merchant from bank code
                                extractMerchantFromBankCode(bankCode) ?: name
                            } else {
                                cleanMerchantName(name)
                            }
                        }
                        // Pure phone numbers - always return the phone number
                        name.length > 0 && name.all { it.isDigit() } -> {
                            // For person-to-person transfers, always show the phone number
                            // not the bank/app name (users want to see WHO they sent to, not HOW)
                            name
                        }

                        else -> null
                    }
                }
            }

            if (merchantName != null) {
                // For other merchants, check validation
                if (isValidMerchantName(merchantName)) {
                    return merchantName
                }
                // If validation fails but we have a merchant name, still return it
                // This handles edge cases where the extracted name doesn't pass standard validation
                return merchantName
            }
        }

        // Fall back to generic extraction
        return super.extractMerchant(message, sender)
    }

    /**
     * Cleans merchant name from Kotak credit card SMS format.
     * Handles UPI reference format "UPI-<ref_number>-<MERCHANT_NAME>"
     * by extracting just the merchant name part.
     */
    private fun cleanKotakCardMerchant(rawMerchant: String): String {
        val upiRefPattern = Regex("""^UPI-\d+-(.+)$""", RegexOption.IGNORE_CASE)
        upiRefPattern.find(rawMerchant)?.let { match ->
            return cleanMerchantName(match.groupValues[1].trim())
        }
        return cleanMerchantName(rawMerchant)
    }

    /**
     * Checks if the UPI username looks like a generated ID from a payment app
     * rather than a human-readable username or phone number.
     */
    private fun isPaymentAppGeneratedId(name: String): Boolean {
        val lowerName = name.lowercase()

        // Common patterns for generated QR code IDs
        val generatedIdPrefixes = listOf(
            "paytmqr",          // Paytm QR codes: paytmqr288005050101t74afkchmxjd
            "phonepeqr",        // PhonePe QR codes
            "phonepe.qr",       // PhonePe QR codes (alternative)
            "gpay",             // Google Pay generated IDs
            "amazonpayqr",      // Amazon Pay QR codes
            "bhimqr",           // BHIM QR codes
            "bharatpeqr",       // BharatPe QR codes
            "freechargeqr",     // Freecharge QR codes
            "mobikwikqr"        // MobiKwik QR codes
        )

        // Check if name starts with any known generated ID prefix
        if (generatedIdPrefixes.any { lowerName.startsWith(it) }) {
            return true
        }

        // Check if name looks like a random generated ID (mix of letters and numbers, long length)
        // Typical pattern: starts with a known prefix or is very long with random alphanumeric characters
        if (name.length > 20 && name.any { it.isLetter() } && name.any { it.isDigit() }) {
            // This looks like a generated ID rather than a meaningful name
            return true
        }

        return false
    }

    /**
     * Extract meaningful merchant name from UPI bank codes
     */
    private fun extractMerchantFromBankCode(bankCode: String): String? {
        return when (bankCode.lowercase()) {
            "okaxis" -> "Axis Bank"
            "okbizaxis" -> "Axis Bank Business"
            "okhdfcbank" -> "HDFC Bank"
            "okicici" -> "ICICI Bank"
            "oksbi" -> "State Bank of India"
            "paytm" -> "Paytm"
            "ybl" -> "PhonePe"
            "amazonpay" -> "Amazon Pay"
            "googlepay" -> "Google Pay"
            "airtel" -> "Airtel Money"
            "freecharge" -> "Freecharge"
            "mobikwik" -> "MobiKwik"
            "jupiteraxis" -> "Jupiter"
            "razorpay" -> "Razorpay"
            "bharatpe" -> "BharatPe"
            else -> null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Credit card transactions: "Avl limit" indicates credit card usage
        if (lowerMessage.contains("avl limit") || lowerMessage.contains("avl lmt")) {
            return TransactionType.CREDIT
        }

        // Explicit credit card mention with spending keywords
        if (lowerMessage.contains("credit card") &&
            (lowerMessage.contains("spent") || lowerMessage.contains("debited"))
        ) {
            return TransactionType.CREDIT
        }

        return when {
            // Kotak specific: "Sent Rs.X from Kotak Bank" - money going OUT (EXPENSE)
            // This indicates the user sent money from their Kotak account to someone else
            lowerMessage.contains("sent") && lowerMessage.contains("from kotak") -> TransactionType.EXPENSE

            // Standard expense keywords
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE

            // Income keywords
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("cashback") && !lowerMessage.contains("earn cashback") -> TransactionType.INCOME

            else -> null
        }
    }

    override fun extractReference(message: String): String? {
        // Kotak specific UPI reference pattern
        val upiRefPattern = Regex("UPI\\s+Ref\\s+([0-9]+)", RegexOption.IGNORE_CASE)
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Fall back to generic extraction
        return super.extractReference(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Kotak credit card pattern: "Credit Card x5236" or "Credit Card XX5236"
        val kotakCardPattern = Regex(
            """Credit\s+Card\s+[xX*]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        kotakCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Kotak specific pattern: "AC X0000" or "AC XXXX0000"
        val kotakAccountPattern =
            Regex("AC\\s+[X*]*([0-9]{4})(?:\\s|,|\\.)", RegexOption.IGNORE_CASE)
        kotakAccountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        val kotakCreditLimitPatterns = listOf(
            // "Avl limit INR 73733.02"
            Regex("""Avl\s+limit:?\s*INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Avl Lmt INR 73733.02"
            Regex("""Avl\s+Lmt:?\s*INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Available limit INR 73733.02"
            Regex("""Available\s+limit:?\s*INR\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in kotakCreditLimitPatterns) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(limitStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAvailableLimit(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip fraud warning links
        if (lowerMessage.contains("not you") && lowerMessage.contains("fraud")) {
            // This is still a transaction message, just with fraud warning
            // Continue processing
        }

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code") ||
            lowerMessage.contains("offer") ||
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")
        ) {
            return false
        }

        // Skip payment request messages
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment") ||
            lowerMessage.contains("requests rs") ||
            lowerMessage.contains("ignore if already paid")
        ) {
            return false
        }

        // Kotak specific transaction keywords
        val kotakTransactionKeywords = listOf(
            "sent", // Kotak uses "Sent Rs.X from Kotak Bank"
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid"
        )

        return kotakTransactionKeywords.any { lowerMessage.contains(it) }
    }
}