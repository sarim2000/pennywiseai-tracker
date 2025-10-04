package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.MandateInfo
import java.math.BigDecimal

/**
 * Parser for Federal Bank SMS messages
 * 
 * Supported formats:
 * - UPI transactions: "Rs 34.51 debited via UPI on 08-05-2025 13:48:03 to VPA ..."
 * - Card transactions
 * - ATM withdrawals
 * - NEFT/IMPS transfers
 * 
 * Sender patterns: AD-FEDBNK-S, JM-FEDBNK-S, etc.
 */
class FederalBankParser : BankParser() {
    
    override fun getBankName() = "Federal Bank"
    
    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("FEDBNK") ||
               normalizedSender.contains("FEDERAL") ||
               normalizedSender.contains("FEDFIB") ||
               // DLT patterns for transactions (-S suffix)
               normalizedSender.matches(Regex("^[A-Z]{2}-FEDBNK-S$")) ||
               // FedFiB patterns
               normalizedSender.matches(Regex("^[A-Z]{2}-FedFiB-[A-Z]$")) ||
               // Other DLT patterns
               normalizedSender.matches(Regex("^[A-Z]{2}-FEDBNK-[TPG]$")) ||
               // Legacy patterns
               normalizedSender.matches(Regex("^[A-Z]{2}-FEDBNK$"))
    }

    fun detectIsCreditCard(message: String): Boolean {
        return  message.lowercase().contains("credit card")
    }

    /**
     * Detects if the transaction is from a card (credit/debit) based on Federal Bank specific patterns.
     */
    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        return when {
            // Explicit credit card patterns
            detectIsCreditCard(message) -> true
            
            // Explicit debit card patterns
            lowerMessage.contains("debit card") -> true
            
            // Card number patterns: "card XX**9747" or "card ending with 1234"
            lowerMessage.contains("card xx**") -> true
            lowerMessage.contains("card ending with") -> true
            
            // INR spent pattern (typically credit card)
            lowerMessage.matches(Regex(""".*inr\s+[\d,]+(?:\.\d{2})?\s+spent.*""")) -> true
            
            // "at <merchant> on <date>" pattern (credit card transactions)
            lowerMessage.contains(" spent ") && lowerMessage.contains(" at ") && 
            lowerMessage.contains(" on ") -> true
            
            // E-mandate on card patterns
            (lowerMessage.contains("e-mandate") || lowerMessage.contains("payment of")) &&
            (lowerMessage.contains("federal bank debit card") || 
             lowerMessage.contains("federal bank credit card")) -> true
            
            // Exclude UPI transactions (these are not card transactions)
            lowerMessage.contains("via upi") -> false
            lowerMessage.contains("to vpa") -> false
            
            // Exclude ATM withdrawals from being categorized as card transactions
            lowerMessage.contains("atm") -> false
            lowerMessage.contains("withdrawn") && !lowerMessage.contains("card") -> false
            
            // Exclude IMPS/NEFT/RTGS transfers
            lowerMessage.contains("via imps") -> false
            lowerMessage.contains("via neft") -> false
            lowerMessage.contains("via rtgs") -> false
            
            else -> false
        }
    }
    
    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: INR 506.52 spent (credit card format)
        val inrSpentPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+spent""",
            RegexOption.IGNORE_CASE
        )
        inrSpentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        // Pattern 2: "you've received INR 10,509.09"
        val receivedPattern = Regex(
            """you've received INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        receivedPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: Rs 34.51 debited via UPI
        val debitPattern = Regex(
            """Rs\s+([0-9,]+(?:\.\d{2})?)\s+debited""",
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
        
        // Pattern 4: Rs 70.00 sent via UPI
        val sentPattern = Regex(
            """Rs\s+([0-9,]+(?:\.\d{2})?)\s+sent""",
            RegexOption.IGNORE_CASE
        )
        sentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        // Pattern 5: Rs 500.00 credited
        val creditPattern = Regex(
            """Rs\s+([0-9,]+(?:\.\d{2})?)\s+credited""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        // Pattern 6: withdrawn Rs 500
        val withdrawnPattern = Regex(
            """withdrawn\s+Rs\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        withdrawnPattern.find(message)?.let { match ->
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
        // Priority 1: IMPS credits - show "IMPS Credit" instead of parsing description
        if (message.contains("credited to your A/c", ignoreCase = true) &&
            message.contains("via IMPS", ignoreCase = true)) {
            return "IMPS Credit"
        }

        // Priority 2: Card transactions - use detectIsCard to avoid duplication
        if (detectIsCard(message)) {
            // Credit card transactions - "at <merchant> on date"
            if (message.contains(" at ", ignoreCase = true)) {
                val creditCardPattern = Regex(
                    """at\s+([^.\n]+?)\s+on\s+\d""",
                    RegexOption.IGNORE_CASE
                )
                creditCardPattern.find(message)?.let { match ->
                    val merchant = cleanMerchantName(match.groupValues[1].trim())
                    if (isValidMerchantName(merchant)) {
                        val cleanedMerchant = merchant
                            .replace(Regex("""\s+(limited|ltd|pvt\s+ltd|private\s+limited)$""", RegexOption.IGNORE_CASE), "")
                            .trim()
                        return cleanedMerchant.ifEmpty { merchant }
                    }
                }
            }
        }

        // Priority 3: E-mandate transactions
        if (message.contains("e-mandate", ignoreCase = true) || message.contains("payment of", ignoreCase = true)) {
            val emandatePattern = Regex(
                """payment of\s+[^.]+?\s+for\s+([^.\n]+?)\s+via\s+e-mandate""",
                RegexOption.IGNORE_CASE
            )
            emandatePattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

            val emandateDeclinedPattern = Regex(
                """payment via e-mandate\s+declined\s+for\s+ID:\s*[^.]+?\s+on\s+Federal Bank\s+Debit Card\s+\d+""",
                RegexOption.IGNORE_CASE
            )
            if (emandateDeclinedPattern.find(message) != null) {
                return "E-Mandate Declined"
            }
        }
        
        // Priority 4: UPI transactions - "to VPA merchant@bank"
        if (message.contains("VPA", ignoreCase = true)) {
            val vpaPattern = Regex(
                """to\s+VPA\s+([^\s]+?)(?:\.\s*Ref\s+No|\s*Ref\s+No|$)""",
                RegexOption.IGNORE_CASE
            )
            vpaPattern.find(message)?.let { match ->
                val vpa = match.groupValues[1].trim()
                return parseUPIMerchant(vpa)
            }
        }
        
        // Priority 5: "to <merchant name>" (general)
        val toPattern = Regex(
            """to\s+([^.\n]+?)(?:\.\s*Ref|Ref\s+No|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (!merchant.contains("VPA", ignoreCase = true)) {
                val cleaned = cleanMerchantName(merchant)
                if (isValidMerchantName(cleaned)) {
                    return cleaned
                }
            }
        }
        
        // Priority 6: "you've received INR" transactions
        if (message.contains("you've received", ignoreCase = true)) {
            val sentByPattern = Regex(
                """It was sent by\s+([^.\n]+?)(?:\s+on|$)""",
                RegexOption.IGNORE_CASE
            )
            sentByPattern.find(message)?.let { match ->
                val sender = match.groupValues[1].trim()
                if (sender.matches(Regex("^0+$")) || sender.length <= 4) {
                    return "Bank Transfer"
                }
                val merchant = cleanMerchantName(sender)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Priority 7: "from <sender name>"
        val fromPattern = Regex(
            """from\s+([^.\n]+?)(?:\.\s*|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }
        
        // Priority 8: ATM transactions
        if (message.contains("ATM", ignoreCase = true) || 
            message.contains("withdrawn", ignoreCase = true)) {
            return "ATM Withdrawal"
        }
        
        return super.extractMerchant(message, sender)
    }
    
    private fun parseUPIMerchant(vpa: String): String {
        val cleanVPA = vpa.split("@")[0].lowercase()
        
        return when {
            // Airlines & Travel
            cleanVPA.contains("indigo") -> "Indigo"
            cleanVPA.contains("spicejet") -> "SpiceJet"
            cleanVPA.contains("airasia") -> "AirAsia"
            cleanVPA.contains("vistara") -> "Vistara"
            cleanVPA.contains("airindia") -> "Air India"

            // Ride-hailing
            cleanVPA.contains("uber") -> "Uber"
            cleanVPA.contains("ola") -> "Ola"
            cleanVPA.contains("rapido") -> "Rapido"

            // E-commerce
            cleanVPA.contains("amazon") -> "Amazon"
            cleanVPA.contains("flipkart") -> "Flipkart"
            cleanVPA.contains("myntra") -> "Myntra"
            cleanVPA.contains("meesho") -> "Meesho"

            // Payment apps
            cleanVPA.contains("paytm") -> "Paytm"
            cleanVPA.contains("bharatpe") -> "BharatPe"
            cleanVPA.contains("phonepe") -> "PhonePe"
            cleanVPA.contains("googlepay") || cleanVPA.contains("gpay") -> "Google Pay"
            
            // Food delivery
            cleanVPA.contains("swiggy") -> "Swiggy"
            cleanVPA.contains("zomato") -> "Zomato"
            
            // Entertainment
            cleanVPA.contains("netflix") -> "Netflix"
            cleanVPA.contains("spotify") -> "Spotify"
            cleanVPA.contains("hotstar") || cleanVPA.contains("disney") -> "Disney+ Hotstar"
            cleanVPA.contains("prime") -> "Amazon Prime"
            cleanVPA.contains("pvr") || cleanVPA.contains("inox") -> "PVR Inox"
            cleanVPA.contains("bookmyshow") || cleanVPA.contains("bms") -> "BookMyShow"
            
            // Telecom
            cleanVPA.contains("jio") -> "Jio"
            cleanVPA.contains("airtel") -> "Airtel"
            cleanVPA.contains("vodafone") || cleanVPA.contains("vi") -> "Vi"
            cleanVPA.contains("bsnl") -> "BSNL"
            
            // Travel
            cleanVPA.contains("irctc") -> "IRCTC"
            cleanVPA.contains("redbus") -> "RedBus"
            cleanVPA.contains("makemytrip") || cleanVPA.contains("mmt") -> "MakeMyTrip"
            cleanVPA.contains("goibibo") -> "Goibibo"
            cleanVPA.contains("oyo") -> "OYO"
            cleanVPA.contains("airbnb") -> "Airbnb"
            
            // Payment gateways
            cleanVPA.contains("razorpay") || cleanVPA.contains("razorp") || cleanVPA.contains("rzp") -> {
                when {
                    cleanVPA.contains("pvr") -> "PVR"
                    cleanVPA.contains("inox") -> "PVR Inox"
                    cleanVPA.contains("swiggy") -> "Swiggy"
                    cleanVPA.contains("zomato") -> "Zomato"
                    else -> "Online Payment"
                }
            }
            cleanVPA.contains("payu") || cleanVPA.contains("billdesk") || cleanVPA.contains("ccavenue") -> "Online Payment"
            
            // Individual transfers
            cleanVPA.matches(Regex("\\d+")) -> "Individual"
            
            else -> vpa.trim()
        }
    }
    
    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") || 
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")) {
            return false
        }
        
        // Skip mandate creation notifications and declined payments
        if (isMandateCreationNotification(message) || isDeclinedMandatePayment(message)) {
            return false
        }

        // Federal Bank specific transaction keywords
        val federalKeywords = listOf(
            "sent via upi",
            "debited via upi",
            "credited",
            "withdrawn",
            "received",
            "transferred",
            "spent on your credit card",
            "payment of",
            "payment via e-mandate"
        )
        
        if (federalKeywords.any { lowerMessage.contains(it) }) {
            return true
        }
        
        return super.isTransactionMessage(message)
    }
    
    override fun extractAccountLast4(message: String): String? {
        // Only extract card numbers if this is actually a card transaction
        if (detectIsCard(message)) {
            // Pattern 1: "credit card ending with 1234"
            val endingWithPattern = Regex(
                """(?:credit|debit)\s+card\s+ending\s+with\s+(\d{4})""",
                RegexOption.IGNORE_CASE
            )
            endingWithPattern.find(message)?.let { match ->
                return match.groupValues[1]
            }

            // Pattern 2: "card XX**9747"
            val cardPattern = Regex(
                """card\s+XX\*\*?(\d{4})""",
                RegexOption.IGNORE_CASE
            )
            cardPattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        // For non-card transactions, try base class patterns
        return super.extractAccountLast4(message)
    }
    
    override fun extractBalance(message: String): BigDecimal? {
        // Don't extract credit limit as balance
        return super.extractBalance(message)
    }
    
    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        
        return when {
            // Credit card transactions - now using detectIsCard
            detectIsCreditCard(message) && lowerMessage.contains("spent") -> TransactionType.CREDIT

            // E-mandate payments (only successful ones)
            (lowerMessage.contains("e-mandate") || lowerMessage.contains("payment of")) &&
            lowerMessage.contains("processed successfully") -> TransactionType.EXPENSE

            // Expense keywords
            lowerMessage.contains("sent via upi") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") && !detectIsCreditCard(message) -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE

            // Income keywords
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            else -> super.extractTransactionType(message)
        }
    }

    fun isMandateCreationNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()

        return (lowerMessage.contains("mandate") || lowerMessage.contains("e-mandate")) &&
               (lowerMessage.contains("successfully created a mandate") ||
                lowerMessage.contains("you have successfully created") ||
                lowerMessage.contains("successfully created") ||
                lowerMessage.contains("has been initiated") ||
                lowerMessage.contains("registration has been initiated"))
    }

    fun isDeclinedMandatePayment(message: String): Boolean {
        val lowerMessage = message.lowercase()

        return (lowerMessage.contains("e-mandate") || lowerMessage.contains("payment of")) &&
               lowerMessage.contains("declined")
    }

    fun parseEMandateSubscription(message: String): EMandateInfo? {
        if (!isMandateCreationNotification(message)) {
            return null
        }

        val amountPattern = Regex("""(?:for\s+a\s+)?maximum\s+amount\s+of\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val amount = amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        val datePattern = Regex("""starting\s+from\s+(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE)
        val startDate = datePattern.find(message)?.groupValues?.get(1)

        val merchantPattern = Regex("""(?:created\s+a\s+mandate\s+on|mandate\s+on)\s+([^.\n]+?)(?:\s+for|\s*$)""", RegexOption.IGNORE_CASE)
        val merchant = merchantPattern.find(message)?.let { match ->
            cleanMerchantName(match.groupValues[1].trim())
        } ?: "Unknown Subscription"

        val umnPattern = Regex("""Mandate\s+Ref\s+No-?\s*([^.\s]+)""", RegexOption.IGNORE_CASE)
        val umn = umnPattern.find(message)?.groupValues?.get(1)

        return EMandateInfo(
            amount = amount,
            nextDeductionDate = startDate,
            merchant = merchant,
            umn = umn
        )
    }

    fun parseFutureDebit(message: String): EMandateInfo? {
        val lowerMessage = message.lowercase()

        if (!lowerMessage.contains("payment due") || !lowerMessage.contains("will be processed")) {
            return null
        }

        val amountPattern = Regex("""INR\s+(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val amount = amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        val datePattern = Regex("""on\s+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)
        val dueDate = datePattern.find(message)?.groupValues?.get(1)?.let { dateStr ->
            try {
                val parts = dateStr.split("/")
                if (parts.size == 3) {
                    "${parts[0]}/${parts[1]}/${parts[2].takeLast(2)}"
                } else {
                    dateStr
                }
            } catch (e: Exception) {
                dateStr
            }
        }

        val merchantPattern = Regex("""for\s+([^.\n]+?)\s*,\s*INR""", RegexOption.IGNORE_CASE)
        val merchant = merchantPattern.find(message)?.let { match ->
            cleanMerchantName(match.groupValues[1].trim())
        } ?: "Unknown Subscription"

        return EMandateInfo(
            amount = amount,
            nextDeductionDate = dueDate,
            merchant = merchant,
            umn = null
        )
    }

    data class EMandateInfo(
        override val amount: BigDecimal,
        override val nextDeductionDate: String?,
        override val merchant: String,
        override val umn: String?
    ) : MandateInfo {
        override val dateFormat = "dd-MM-yyyy"
    }

    fun isTransactionMessageForTesting(message: String): Boolean = isTransactionMessage(message)
}