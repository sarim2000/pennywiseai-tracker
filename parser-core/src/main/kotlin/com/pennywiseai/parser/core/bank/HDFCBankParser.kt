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
class HDFCBankParser : BankParser() {

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

    /**
     * Checks if this is an E-Mandate notification (not a transaction).
     */
    fun isEMandateNotification(message: String): Boolean {
        return message.contains("E-Mandate!", ignoreCase = true)
    }

    /**
     * Checks if this is a future debit notification (subscription alert, not a current transaction).
     */
    fun isFutureDebitNotification(message: String): Boolean {
        return message.contains("will be", ignoreCase = true)
    }

    /**
     * Parses E-Mandate subscription information.
     */
    fun parseEMandateSubscription(message: String): EMandateInfo? {
        if (!isEMandateNotification(message)) {
            return null
        }

        // Extract amount
        val amount = CompiledPatterns.HDFC.AMOUNT_WILL_DEDUCT.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        // Extract date
        val dateStr = CompiledPatterns.HDFC.DEDUCTION_DATE.find(message)?.groupValues?.get(1)

        // Extract merchant name
        val merchant = CompiledPatterns.HDFC.MANDATE_MERCHANT.find(message)?.let { match ->
            cleanMerchantName(match.groupValues[1].trim())
        } ?: "Unknown Subscription"

        // Extract UMN (Unique Mandate Number)
        val umn = CompiledPatterns.HDFC.UMN_PATTERN.find(message)?.groupValues?.get(1)

        return EMandateInfo(
            amount = amount,
            nextDeductionDate = dateStr,
            merchant = merchant,
            umn = umn
        )
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

    /**
     * Parses future debit notifications for subscription tracking.
     * Similar to E-Mandate but for regular future debit alerts.
     */
    fun parseFutureDebit(message: String): EMandateInfo? {
        if (!isFutureDebitNotification(message)) {
            return null
        }

        // Extract amount
        val amountPattern = Regex("""INR\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val amount = amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        // Extract date (DD/MM/YYYY format)
        val datePattern =
            Regex("""will\s+be\s+debited\s+on\s+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)
        val debitDate = datePattern.find(message)?.groupValues?.get(1)?.let { dateStr ->
            // Convert DD/MM/YYYY to DD/MM/YY for consistency with EMandateInfo
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

        // Extract merchant using the existing method
        val merchant = extractMerchant(message, "HDFCBK") ?: "Unknown Subscription"

        // Return as EMandateInfo to reuse existing subscription creation logic
        return EMandateInfo(
            amount = amount,
            nextDeductionDate = debitDate,
            merchant = merchant,
            umn = null // Future debits don't have UMN
        )
    }

    /**
     * E-Mandate subscription information.
     */
    data class EMandateInfo(
        override val amount: BigDecimal,
        override val nextDeductionDate: String?,
        override val merchant: String,
        override val umn: String?
    ) : MandateInfo {
        // HDFC uses dd/MM/yy format
        override val dateFormat = "dd/MM/yy"
    }

    /**
     * Balance update information.
     */
    data class BalanceUpdateInfo(
        val bankName: String,
        val accountLast4: String,
        val balance: BigDecimal,
        val asOfDate: LocalDateTime? = null
    )

    /**
     * Checks if this is a balance update notification (not a transaction).
     */
    fun isBalanceUpdateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for balance update patterns
        return (lowerMessage.contains("available bal") ||
                lowerMessage.contains("avl bal") ||
                lowerMessage.contains("account balance") ||
                lowerMessage.contains("a/c balance")) &&
                lowerMessage.contains("as on") &&
                !lowerMessage.contains("debited") &&
                !lowerMessage.contains("credited") &&
                !lowerMessage.contains("withdrawn") &&
                !lowerMessage.contains("spent") &&
                !lowerMessage.contains("transferred")
    }

    /**
     * Parses balance update notification.
     */
    fun parseBalanceUpdate(message: String): BalanceUpdateInfo? {
        if (!isBalanceUpdateNotification(message)) {
            return null
        }

        // Extract account last 4 digits
        val accountLast4 = extractAccountLast4(message) ?: return null

        // Extract balance amount - pattern for "is INR 12,678.00"
        val balancePattern = Regex("""is\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val balance = balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        // Extract date if present (e.g., "as on yesterday:21-AUG-25")
        val datePattern =
            Regex("""as\s+on\s+(?:yesterday:)?(\d{1,2}-[A-Z]{3}-\d{2})""", RegexOption.IGNORE_CASE)
        val asOfDate = datePattern.find(message)?.let { match ->
            // Parse date format DD-MMM-YY
            val dateStr = match.groupValues[1]
            try {
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    val day = parts[0].toInt()
                    val month = getMonthNumber(parts[1])
                    val year = 2000 + parts[2].toInt() // Assuming 20XX for YY format

                    LocalDateTime.of(year, month, day, 0, 0)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        return BalanceUpdateInfo(
            bankName = getBankName(),
            accountLast4 = accountLast4,
            balance = balance,
            asOfDate = asOfDate
        )
    }

    /**
     * Helper function to convert month abbreviation to number.
     */
    private fun getMonthNumber(monthAbbr: String): Int {
        return when (monthAbbr.uppercase()) {
            "JAN" -> 1
            "FEB" -> 2
            "MAR" -> 3
            "APR" -> 4
            "MAY" -> 5
            "JUN" -> 6
            "JUL" -> 7
            "AUG" -> 8
            "SEP" -> 9
            "OCT" -> 10
            "NOV" -> 11
            "DEC" -> 12
            else -> 1
        }
    }
}
