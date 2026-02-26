package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for ICICI Bank SMS messages
 *
 * Supported formats:
 * - Debit: "Your account has been successfully debited with Rs xxx.00"
 * - Credit: "Acct XXxxx is credited with Rs xxx.00"
 * - UPI: "ICICI Bank Acct XXxxx debited for Rs xxx.00"
 * - Cash Deposit: "Cash deposit transaction of Rs xxx in ICICI Bank Account 1234XXXX1234 has been completed"
 * - AutoPay transactions
 * - Multi-currency: "USD 11.80 spent using ICICI Bank Card"
 *
 * Common senders: XX-ICICIB-S, ICICIB, ICICIBANK
 */
class ICICIBankParser : BaseIndianBankParser() {

    override fun getBankName() = "ICICI Bank"

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Skip non-transaction messages
        if (!isTransactionMessage(smsBody)) {
            return null
        }

        val amount = extractAmount(smsBody)
        if (amount == null) {
            return null
        }

        val type = extractTransactionType(smsBody)
        if (type == null) {
            return null
        }

        // Extract currency dynamically for multi-currency support
        val currency = extractCurrencyFromMessage(smsBody) ?: "INR"

        // Extract available limit for credit card transactions
        val availableLimit = if (type == TransactionType.CREDIT) {
            val limit = extractAvailableLimit(smsBody)
            limit
        } else {
            null
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            creditLimit = availableLimit,
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = detectIsCard(smsBody),
            currency = currency
        )
    }

    /**
     * Extract currency from ICICI transaction messages
     * Handles formats like "USD 11.80 spent" or "EUR 50.00 spent"
     */
    private fun extractCurrencyFromMessage(message: String): String? {
        // Pattern for "USD 11.80 spent" format
        val currencySpentPattern = Regex(
            """([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?\s+spent""",
            RegexOption.IGNORE_CASE
        )
        currencySpentPattern.find(message)?.let { match ->
            val currency = match.groupValues[1].uppercase()
            // Validate it's a valid currency code (3 letters, not month abbreviations)
            if (currency.length == 3 &&
                !currency.matches(Regex("^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$"))
            ) {
                return currency
            }
        }

        // Pattern for other formats if needed in future
        // Could add more patterns here

        return null
    }

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("ICICI") ||
                normalizedSender.contains("ICICIB") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-ICICIB-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-ICICI-S$")) ||
                // Other DLT patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-ICICIB-[TPG]$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-ICICIB$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-ICICI$")) ||
                // Direct sender IDs
                normalizedSender == "ICICIB" ||
                normalizedSender == "ICICIBANK"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: Multi-currency support - "USD 11.80 spent" or "EUR 50.00 spent"
        val multiCurrencySpentPattern = Regex(
            """[A-Z]{3}\s+([0-9,]+(?:\.\d{2})?)\s+spent""",
            RegexOption.IGNORE_CASE
        )
        multiCurrencySpentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Rs xxx.xx spent" or "INR xxx.xx spent" (for INR card transactions)
        val inrSpentPattern = Regex(
            """(?:Rs\.?|INR)\s+([0-9,]+(?:\.\d{2})?)\s+spent""",
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

        // Pattern 2: "debited with Rs xxx.00"
        val debitWithPattern = Regex(
            """debited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitWithPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: "debited for Rs xxx.00"
        val debitForPattern = Regex(
            """debited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitForPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 4: "credited with Rs xxx.00"
        val creditWithPattern = Regex(
            """credited\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        creditWithPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 5: "credited:Rs. xxx.xx" (colon format for cash deposits)
        val creditColonPattern = Regex(
            """credited:\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        creditColonPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 0: NEFT/RTGS transfer to beneficiary - use "NEFT Transfer" as merchant
        // These are outgoing transfers where we don't know the beneficiary name
        if (message.contains("credited to the beneficiary", ignoreCase = true) ||
            message.contains("credited to beneficiary", ignoreCase = true)
        ) {
            return "NEFT Transfer"
        }

        // Pattern 1: Salary transactions - "Info INF*...*...* SAL ..."
        // Example: "Info INF*000169831922*IQBO SAL FE"
        val salaryPattern = Regex(
            """Info\s+INF\*[^*]+\*[^*]*SAL[^.]*""",
            RegexOption.IGNORE_CASE
        )
        if (salaryPattern.find(message) != null) {
            return "Salary"
        }

        // Pattern 2: NFS Cash Withdrawal - various ATM withdrawal formats
        // Examples: "NFSCASH WDL", "NFS CASH WDL", "NFS*CASH WDL*", "CASH WDL"
        if (message.contains("NFSCASH WDL", ignoreCase = true) ||
            message.contains("NFS CASH WDL", ignoreCase = true) ||
            message.contains("NFS*CASH WDL", ignoreCase = true) ||
            message.contains("CASH WDL", ignoreCase = true) ||
            message.contains("NFSCASH", ignoreCase = true)
        ) {
            return "Cash Withdrawal"
        }

        // Pattern 3: Card transactions - "on DD-Mon-YY at MERCHANT NAME. Avl" or "on DD-Mon-YY on MERCHANT NAME"
        val cardMerchantPattern = Regex(
            """on\s+\d{1,2}-\w{3}-\d{2}\s+(?:at|on)\s+([^.]+?)(?:\.|\s+Avl|$)""",
            RegexOption.IGNORE_CASE
        )
        cardMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 3: ACH/NACH dividend payments - "Info ACH*COMPANY NAME*XXX"
        val achNachPattern = Regex(
            """Info\s+(?:ACH|NACH)\*([^*]+)\*""",
            RegexOption.IGNORE_CASE
        )
        achNachPattern.find(message)?.let { match ->
            val companyName = cleanMerchantName(match.groupValues[1].trim())
            // Append "Dividend" to make categorization clear
            return "$companyName Dividend"
        }

        // Pattern 3: "towards <merchant> for"
        val towardsPattern = Regex(
            """towards\s+([^.\n]+?)\s+for""",
            RegexOption.IGNORE_CASE
        )
        towardsPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "from <name>. UPI"
        val fromUpiPattern = Regex(
            """from\s+([^.\n]+?)\.\s*UPI""",
            RegexOption.IGNORE_CASE
        )
        fromUpiPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 5: "; <name> credited. UPI"
        val creditedPattern = Regex(
            """;\s*([^.\n]+?)\s+credited\.\s*UPI""",
            RegexOption.IGNORE_CASE
        )
        creditedPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 6: Cash deposit via "Info BY CASH" pattern
        if (message.contains("Info BY CASH", ignoreCase = true)) {
            return "Cash Deposit"
        }

        // Pattern 7: AutoPay specific - extract service name
        if (message.contains("AutoPay", ignoreCase = true)) {
            // Look for common AutoPay services
            val lowerMessage = message.lowercase()
            return when {
                lowerMessage.contains("google play") -> "Google Play Store"
                lowerMessage.contains("netflix") -> "Netflix"
                lowerMessage.contains("spotify") -> "Spotify"
                lowerMessage.contains("amazon prime") -> "Amazon Prime"
                lowerMessage.contains("disney") || lowerMessage.contains("hotstar") -> "Disney+ Hotstar"
                lowerMessage.contains("youtube") -> "YouTube Premium"
                else -> "AutoPay Subscription"
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "ICICI Bank Card XXNNNN" - for card transactions
        val cardPattern = Regex(
            """ICICI\s+Bank\s+Card\s+[X\*]*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            val cardNumber = match.groupValues[1]
            return if (cardNumber.length >= 4) {
                cardNumber.takeLast(4)
            } else {
                cardNumber
            }
        }

        // Pattern 2: "ICICI Bank Credit Card XX1234" or ending with digits
        val creditCardPattern = Regex(
            """ICICI\s+Bank\s+Credit\s+Card\s+[X\*]*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: "ICICI Bank Account XXNNNN" or "ICICI Bank Account XX566"
        val accountPattern = Regex(
            """ICICI\s+Bank\s+Account\s+([X\*]*\d+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else if (digitsOnly.isNotEmpty()) {
                digitsOnly
            } else {
                null
            }
        }

        // Pattern 4: "ICICI Bank Acct XXNNNN" - more specific pattern first
        val bankAcctPattern = Regex(
            """ICICI\s+Bank\s+Acct\s+[X\*]*(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
        bankAcctPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // Pattern 5: "ICICI Bank Acc XX921" - short "Acc" format
        val bankAccPattern = Regex(
            """ICICI\s+Bank\s+Acc\s+[X\*]*(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
        bankAccPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // Pattern 6: "Acct XX1234" - ONLY when followed by specific ICICI patterns
        // Must be exactly XX followed by 3-4 digits to avoid false positives
        val acctXXPattern = Regex(
            """Acct\s+XX(\d{3,4})(?:\s|$|[,;.])""",
            RegexOption.IGNORE_CASE
        )
        acctXXPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // Pattern 7: "Acc XX921" - short form with XX mask
        val accXXPattern = Regex(
            """Acc\s+XX(\d{3,4})(?:\s|$|[,;.])""",
            RegexOption.IGNORE_CASE
        )
        accXXPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // Pattern 8: "Acct *1234" - asterisk masked pattern
        val acctStarPattern = Regex(
            """Acct\s+\*+(\d{3,4})(?:\s|$|[,;.])""",
            RegexOption.IGNORE_CASE
        )
        acctStarPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // DO NOT fall back to base class - base class patterns are too generic
        // and cause false positives. Better to return null than wrong account.
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: "Available Balance is Rs. 28,076.14" (ICICI-specific format with "is")
        val availBalIsPattern = Regex(
            """Available\s+Balance\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        availBalIsPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Avl Bal Rs 10,000.00" or "Avb Bal Rs 10,000.00" (typo variant)
        val avlBalPattern = Regex(
            """Av[lb]\s+Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
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

        // Pattern 3: "Updated Bal: Rs 5,000.00"
        val updatedBalPattern = Regex(
            """Updated\s+Bal[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        updatedBalPattern.find(message)?.let { match ->
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

    override fun extractReference(message: String): String? {
        // Pattern 1: "RRN 1xxxxx3xxxxx"
        val rrnPattern = Regex(
            """RRN\s+([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        rrnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "UPI:5xxxxx8xxxxx"
        val upiPattern = Regex(
            """UPI:([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: "transaction reference no.MCDA001746000000"
        val txnRefPattern = Regex(
            """transaction\s+reference\s+no\.?([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        txnRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip SMS BLOCK instructions (not a transaction)
        if (lowerMessage.contains("sms block") && lowerMessage.contains("to 9215676766")) {
            // This is just instruction text at the end of transaction messages
            // Don't skip the entire message, just ignore this part
        }

        // Skip cash deposit confirmation messages (these are duplicates)
        // We only want to process the actual credit notification
        if (lowerMessage.contains("cash deposit transaction") &&
            lowerMessage.contains("has been completed")
        ) {
            return false // Skip this confirmation message
        }

        // Skip payment due reminders
        if (lowerMessage.contains("is due by")) {
            return false // Skip payment due reminders
        }

        // Skip future debit notifications - these are not actual transactions yet
        // Examples: "will be debited on", "will be debited with", "account will be debited"
        if (lowerMessage.contains("will be debited")) {
            return false // This is a future debit notification, not an actual transaction
        }

        // Skip credit card bill payment confirmations - these are transfers between own accounts
        // Example: "Payment of Rs 26,266.00 has been received on your ICICI Bank Credit Card XX9006..."
        if (lowerMessage.contains("has been received on your icici bank credit card")) {
            return false // This is a credit card bill payment, not a transaction
        }

        // Check for ICICI-specific transaction keywords
        val iciciKeywords = listOf(
            "debited with",
            "debited for",
            "credited with",
            "credited:",  // For "credited:Rs." format
            "autopay",
            "your account has been",
            "inr", // For "INR xxx spent" pattern
            "spent using" // For card transactions
        )

        // If any ICICI-specific pattern is found, it's likely a transaction
        // BUT make sure it's not a future transaction (already filtered above)
        if (iciciKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        // Fall back to base class for standard checks
        return super.isTransactionMessage(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // NEFT/RTGS transfer confirmation to sender - "credited to the beneficiary account"
        // This means the sender's money was sent OUT to the beneficiary
        if (lowerMessage.contains("credited to the beneficiary") ||
            lowerMessage.contains("credited to beneficiary")
        ) {
            return TransactionType.TRANSFER
        }

        // Credit card transactions - both "ICICI Bank Credit Card" and "ICICI Bank Card" with spent
        if ((lowerMessage.contains("icici bank credit card") ||
                    (lowerMessage.contains("icici bank card") && lowerMessage.contains("spent"))) &&
            (lowerMessage.contains("spent") || lowerMessage.contains("debited"))
        ) {
            return TransactionType.CREDIT
        }

        // Cash deposit via "Info BY CASH" is income
        if (lowerMessage.contains("info by cash")) {
            return TransactionType.INCOME
        }

        // Fall back to base class for standard checks
        return super.extractTransactionType(message)
    }
}