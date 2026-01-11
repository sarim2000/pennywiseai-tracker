package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.CompiledPatterns
import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * HDFC Bank specific parser.
 * Handles HDFC's unique message formats including:
 * - Standard debit/credit messages
 * - UPI transactions with VPA details
 * - Salary credits with company names
 * - E-Mandate notifications
 * - Card transactions
 */
class HDFCBankParser : BaseIndianBankParser() {

    override fun getBankName() = "HDFC Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        // Common HDFC sender IDs
        val hdfcSenders = setOf(
            "HDFCBK",
            "HDFCBANK",
            "HDFC",
            "HDFCB"
        )

        // Direct match
        if (upperSender in hdfcSenders) return true

        // DLT patterns
        return CompiledPatterns.HDFC.DLT_PATTERNS.any { it.matches(upperSender) }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Check for HDFC Bank Card debit transactions - "Spent Rs.xxx From HDFC Bank Card xxxx At [MERCHANT] On xxx"
        if (message.contains("From HDFC Bank Card", ignoreCase = true) &&
            message.contains(" At ", ignoreCase = true) &&
            message.contains(" On ", ignoreCase = true)
        ) {
            // Extract merchant between "At" and "On" using string operations for reliability
            val atIndex = message.indexOf(" At ", ignoreCase = true)
            val onIndex = message.indexOf(" On ", ignoreCase = true)
            if (atIndex != -1 && onIndex != -1 && onIndex > atIndex) {
                val merchant = message.substring(atIndex + 4, onIndex).trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Check for ATM withdrawals - extract location
        if (message.contains("withdrawn", ignoreCase = true)) {
            // Pattern: "At +18 Random Location" or "At ATM Location On"
            val atLocationPattern = Regex("""At\s+\+?([^O]+?)\s+On""", RegexOption.IGNORE_CASE)
            atLocationPattern.find(message)?.let { match ->
                val location = match.groupValues[1].trim()
                return if (location.isNotEmpty()) {
                    "ATM at ${cleanMerchantName(location)}"
                } else {
                    "ATM"
                }
            }
            return "ATM" // Fallback if no location found
        }

        // Check for generic ATM mentions (without "withdrawn")
        if (message.contains("ATM", ignoreCase = true)) {
            return "ATM"
        }

        // For credit card transactions (with BLOCK CC/PCC instruction), extract merchant after "At"
        if (message.contains("card", ignoreCase = true) &&
            message.contains(" at ", ignoreCase = true) &&
            (message.contains("block cc", ignoreCase = true) || message.contains(
                "block pcc",
                ignoreCase = true
            ))
        ) {
            // Pattern for "at [merchant] by UPI" or just "at [merchant]"
            val atPattern = Regex(
                """at\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)(?:\s+by\s+|\s+on\s+|$)""",
                RegexOption.IGNORE_CASE
            )
            atPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                // For UPI VPA, extract the part before @ (e.g., "paytmqr" from "paytmqr@paytm")
                val cleanedMerchant = if (merchant.contains("@")) {
                    val vpaName = merchant.substringBefore("@").trim()
                    // Clean up common UPI prefixes/suffixes
                    when {
                        vpaName.endsWith("qr", ignoreCase = true) -> vpaName.dropLast(2)
                        else -> vpaName
                    }
                } else {
                    merchant
                }
                if (cleanedMerchant.isNotEmpty()) {
                    return cleanMerchantName(cleanedMerchant)
                }
            }
        }

        // Try HDFC specific patterns

        // Pattern 1: Salary credit - "for XXXXX-ABC-XYZ MONTH SALARY-COMPANY NAME"
        if (message.contains("SALARY", ignoreCase = true) && message.contains(
                "deposited",
                ignoreCase = true
            )
        ) {
            CompiledPatterns.HDFC.SALARY_PATTERN.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }

            // Simpler salary pattern
            CompiledPatterns.HDFC.SIMPLE_SALARY_PATTERN.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.all { it.isDigit() }) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Pattern 2: "Info: UPI/merchant/category" format
        if (message.contains("Info:", ignoreCase = true)) {
            CompiledPatterns.HDFC.INFO_PATTERN.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.equals("UPI", ignoreCase = true)) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Pattern 3: "VPA merchant@bank (Merchant Name)" format
        if (message.contains("VPA", ignoreCase = true)) {
            // Special case for UPI credit: "from VPA username@provider (UPI reference)" or "from VPA username@provider (UPI reference)"
            if (message.contains("from VPA", ignoreCase = true) && message.contains(
                    "credited",
                    ignoreCase = true
                )
            ) {
                val fromVpaPattern = Regex(
                    """from\s+VPA\s*([^@\s]+)@[^\s]+\s*\(UPI\s+\d+\)""",
                    RegexOption.IGNORE_CASE
                )
                fromVpaPattern.find(message)?.let { match ->
                    val vpaUsername = match.groupValues[1].trim()
                    if (vpaUsername.isNotEmpty()) {
                        return cleanMerchantName(vpaUsername)
                    }
                }
            }

            // First try to get name in parentheses
            CompiledPatterns.HDFC.VPA_WITH_NAME.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }

            // Then try just the VPA username part
            CompiledPatterns.HDFC.VPA_PATTERN.find(message)?.let { match ->
                val vpaName = match.groupValues[1].trim()
                if (vpaName.length > 3 && !vpaName.all { it.isDigit() }) {
                    return cleanMerchantName(vpaName)
                }
            }
        }

        // Pattern 4: "spent on Card XX1234 at merchant on date"
        if (message.contains("spent on Card", ignoreCase = true)) {
            CompiledPatterns.HDFC.SPENT_PATTERN.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        // Pattern 5: "debited for merchant on date"
        if (message.contains("debited for", ignoreCase = true)) {
            CompiledPatterns.HDFC.DEBIT_FOR_PATTERN.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        // Pattern 6: "To merchant name" (for UPI mandate)
        if (message.contains("UPI Mandate", ignoreCase = true)) {
            CompiledPatterns.HDFC.MANDATE_PATTERN.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }

        // Pattern 7: "towards [Merchant Name]" (for payment alerts)
        if (message.contains("towards", ignoreCase = true)) {
            val towardsPattern = Regex(
                """towards\s+([^\n]+?)(?:\s+UMRN|\s+ID:|\s+Alert:|$)""",
                RegexOption.IGNORE_CASE
            )
            towardsPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Pattern 8: "For: [Description]" (for payment alerts)
        if (message.contains("For:", ignoreCase = true)) {
            val forColonPattern =
                Regex("""For:\s+([^\n]+?)(?:\s+From|\s+Via|$)""", RegexOption.IGNORE_CASE)
            forColonPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Pattern 9: "for [Merchant Name]" (for future debit notifications)
        if (message.contains("for ", ignoreCase = true) && message.contains(
                "will be debited",
                ignoreCase = true
            )
        ) {
            val forPattern =
                Regex("""for\s+([^\n]+?)(?:\s+ID:|\s+Act:|$)""", RegexOption.IGNORE_CASE)
            forPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Fall back to generic extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Use base class investment detection
        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }

        return when {
            // Credit card transactions - ONLY if message contains CC or PCC indicators
            // Any transaction with BLOCK CC or BLOCK PCC is a credit card transaction
            lowerMessage.contains("block cc") || lowerMessage.contains("block pcc") -> TransactionType.CREDIT

            // Legacy pattern for older format that explicitly says "spent on card"
            lowerMessage.contains("spent on card") && !lowerMessage.contains("block dc") -> TransactionType.CREDIT

            // Credit card bill payments (these are regular expenses from bank account)
            lowerMessage.contains("payment") && lowerMessage.contains("credit card") -> TransactionType.EXPENSE
            lowerMessage.contains("towards") && lowerMessage.contains("credit card") -> TransactionType.EXPENSE

            // HDFC specific: "Sent Rs.X From HDFC Bank"
            lowerMessage.contains("sent") && lowerMessage.contains("from hdfc") -> TransactionType.EXPENSE

            // HDFC specific: "Spent Rs.X From HDFC Bank Card" (debit card transactions)
            lowerMessage.contains("spent") && lowerMessage.contains("from hdfc bank card") -> TransactionType.EXPENSE

            // Standard expense keywords
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") && !lowerMessage.contains("block cc") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") && !lowerMessage.contains("card") -> TransactionType.EXPENSE
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
        // HDFC specific reference patterns
        val hdfcPatterns = listOf(
            CompiledPatterns.HDFC.REF_SIMPLE,
            CompiledPatterns.HDFC.UPI_REF_NO,
            CompiledPatterns.HDFC.REF_NO,
            CompiledPatterns.HDFC.REF_END
        )

        for (pattern in hdfcPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        // Fall back to generic extraction
        return super.extractReference(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern for "Card x####" format in withdrawals
        val cardPattern = Regex("""Card\s+x(\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern for "BLOCK DC ####" format
        val blockDCPattern = Regex("""BLOCK\s+DC\s+(\d{4})""", RegexOption.IGNORE_CASE)
        blockDCPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Additional pattern for "HDFC Bank XXNNNN" format (without A/c prefix)
        val hdfcBankPattern = Regex("""HDFC\s+Bank\s+([X\*]*\d+)""", RegexOption.IGNORE_CASE)
        hdfcBankPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else {
                digitsOnly
            }
        }

        // HDFC specific patterns
        val hdfcPatterns = listOf(
            CompiledPatterns.HDFC.ACCOUNT_DEPOSITED,
            CompiledPatterns.HDFC.ACCOUNT_FROM,
            CompiledPatterns.HDFC.ACCOUNT_SIMPLE,
            CompiledPatterns.HDFC.ACCOUNT_GENERIC
        )

        for (pattern in hdfcPatterns) {
            pattern.find(message)?.let { match ->
                val accountStr = match.groupValues[1]
                // Take last 4 digits for consistency
                return if (accountStr.length >= 4) {
                    accountStr.takeLast(4)
                } else {
                    accountStr
                }
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // HDFC specific pattern for "Avl bal:INR NNNN.NN"
        val avlBalINRPattern =
            Regex("""Avl\s+bal:?\s*INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        avlBalINRPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern for "Available Balance: INR NNNN.NN"
        val availableBalINRPattern = Regex(
            """Available\s+Balance:?\s*INR\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        availableBalINRPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern for "Bal Rs.NNNN.NN" or "Bal Rs NNNN.NN"
        val balRsPattern = Regex("""Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        balRsPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns for Rs format
        return super.extractBalance(message)
    }

    override fun cleanMerchantName(merchant: String): String {
        // Use parent class implementation which already uses CompiledPatterns
        return super.cleanMerchantName(merchant)
    }



    override fun isTransactionMessage(message: String): Boolean {
        // Skip E-Mandate notifications
        if (isEMandateNotification(message)) {
            return false
        }

        // Skip future debit notifications (these are subscription alerts, not transactions)
        if (isFutureDebitNotification(message)) {
            return false
        }

        val lowerMessage = message.lowercase()

        // Skip bill alert notifications (these are reminders for future payments, not transactions)
        // Example: "New Bill Alert: Your ... Bill ... is due on ..."
        if (lowerMessage.contains("bill alert") ||
            (lowerMessage.contains("bill") && lowerMessage.contains("is due on"))
        ) {
            return false
        }

        // Check for payment alerts (current transactions)
        if (lowerMessage.contains("payment alert")) {
            // Make sure it's not a future debit
            if (!lowerMessage.contains("will be")) {
                return true
            }
        }

        // Skip payment request messages
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("to pay, download") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("ignore if already paid")
        ) {
            return false
        }


        // Skip credit card payment confirmations
        if (lowerMessage.contains("received towards your credit card")) {
            return false
        }

        // Skip credit card payment credited notifications
        if (lowerMessage.contains("payment") &&
            lowerMessage.contains("credited to your card")
        ) {
            return false
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

        // HDFC specific transaction keywords
        val hdfcTransactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid",
            "sent", // HDFC uses "Sent Rs.X From HDFC Bank"
            "deducted", // Add support for "deducted from" pattern
            "txn" // HDFC uses "Txn Rs.X" for card transactions
        )

        return hdfcTransactionKeywords.any { lowerMessage.contains(it) }
    }

    // ==========================================
    // E-Mandate / Subscription Logic
    // ==========================================

    /**
     * Parses E-Mandate subscription information from HDFC messages.
     */
    fun parseEMandateSubscription(message: String): EMandateInfo? {
        if (!isEMandateNotification(message)) {
            return null
        }

        // Extract amount - patterns like "Rs.1050.00", "INR 59.00"
        val amountPatterns = listOf(
            Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        var amount: BigDecimal? = null
        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                amount = try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
            if (amount != null) break
        }

        if (amount == null) return null

        // Extract merchant
        var merchant = "Unknown Subscription"
        val merchantPatterns = listOf(
            Regex("""towards\s+([^.\n]+?)(?:\s+from|\s+A/c|\s+UMRN|\s+ID:|\s+Alert:|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""for\s+([^.\n]+?)(?:\s+ID:|\s+Act:|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""Info:\s*([^.\n]+?)(?:\s*$)""", RegexOption.IGNORE_CASE),
            Regex("""To\s+([^.\n]+?)(?:\s+UPI|,|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            pattern.find(message)?.let { match ->
                val m = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(m) && m.length > 2) {
                    merchant = m
                }
            }
            if (merchant != "Unknown Subscription") break
        }

        // Extract next deduction date
        val datePatterns = listOf(
            Regex("""on\s+(\d{2}-\w{3}-\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""date[:\s]+(\d{2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE)
        )

        var nextDeductionDate: String? = null
        for (pattern in datePatterns) {
            pattern.find(message)?.let { match ->
                nextDeductionDate = match.groupValues[1]
            }
            if (nextDeductionDate != null) break
        }

        // Extract UMN (Unique Mandate Number)
        val umnPatterns = listOf(
            Regex("""UMN[:\s]+([^.\s]+)""", RegexOption.IGNORE_CASE),
            Regex("""UMRN[:\s]+([^.\s]+)""", RegexOption.IGNORE_CASE)
        )
        var umn: String? = null
        for (pattern in umnPatterns) {
            pattern.find(message)?.let { match ->
                umn = match.groupValues[1]
            }
            if (umn != null) break
        }

        return EMandateInfo(
            amount = amount!!,
            nextDeductionDate = nextDeductionDate,
            merchant = merchant,
            umn = umn
        )
    }

    /**
     * Parses future debit notification from HDFC messages.
     * These are alerts for upcoming subscription charges.
     */
    fun parseFutureDebit(message: String): EMandateInfo? {
        if (!isFutureDebitNotification(message)) {
            return null
        }

        // Extract amount
        val amountPatterns = listOf(
            Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        var amount: BigDecimal? = null
        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                amount = try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
            if (amount != null) break
        }

        if (amount == null) return null

        // Extract merchant
        var merchant = "Unknown Subscription"
        val merchantPatterns = listOf(
            Regex("""for\s+([^.\n]+?)(?:\s+ID:|\s+Act:|\s+will\s+be|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""towards\s+([^.\n]+?)(?:\s+from|\s+A/c|\s+UMRN|\s+ID:|\s*\.|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            pattern.find(message)?.let { match ->
                val m = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(m) && m.length > 2) {
                    merchant = m
                }
            }
            if (merchant != "Unknown Subscription") break
        }

        // Extract next deduction date
        val datePatterns = listOf(
            Regex("""on\s+(\d{2}-\w{3}-\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""on\s+(\d{2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE)
        )

        var nextDeductionDate: String? = null
        for (pattern in datePatterns) {
            pattern.find(message)?.let { match ->
                nextDeductionDate = match.groupValues[1]
            }
            if (nextDeductionDate != null) break
        }

        return EMandateInfo(
            amount = amount!!,
            nextDeductionDate = nextDeductionDate,
            merchant = merchant,
            umn = null
        )
    }

    /**
     * E-Mandate information for HDFC Bank
     */
    data class EMandateInfo(
        override val amount: BigDecimal,
        override val nextDeductionDate: String?,
        override val merchant: String,
        override val umn: String?
    ) : MandateInfo {
        override val dateFormat = "dd/MM/yy"
    }
}
