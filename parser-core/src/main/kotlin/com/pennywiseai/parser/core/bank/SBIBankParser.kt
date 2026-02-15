package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Parser for State Bank of India (SBI) SMS messages
 */
class SBIBankParser : BaseIndianBankParser() {

    override fun getBankName() = "State Bank of India"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("SBI") ||
                normalizedSender.contains("SBIINB") ||
                normalizedSender.contains("SBIUPI") ||
                normalizedSender.contains("SBICRD") ||
                normalizedSender.contains("ATMSBI") ||
                // Direct sender IDs
                normalizedSender == "SBIBK" ||
                normalizedSender == "SBIBNK" ||
                // SBI Card RCS sender
                normalizedSender.contains("SBI CARDS") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-SBIBK-S$")) ||
                // Other DLT patterns (OTP, Promotional, Govt)
                normalizedSender.matches(Regex("^[A-Z]{2}-SBIBK-[TPG]$")) ||
                // Legacy patterns without suffix
                normalizedSender.matches(Regex("^[A-Z]{2}-SBIBK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-SBI$"))
    }

    // Check if this is a credit card message
    private fun isCreditCardMessage(sender: String, message: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("SBICRD") ||
                upperSender.contains("SBI CARDS") ||
                message.contains("Credit Card", ignoreCase = true)
    }

    // Extract credit card last 4 digits
    private fun extractCreditCardLast4(message: String): String? {
        // Pattern: "ending with 1234" or "ending 1234"
        val patterns = listOf(
            Regex("""ending\s+with\s+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }
        return null
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Normalize Unicode text (SBI Card uses Mathematical Sans-Serif characters in RCS)
        val normalizedBody = normalizeUnicodeText(smsBody)

        val parsed = super.parse(normalizedBody, sender, timestamp) ?: return null

        // Handle credit card messages
        if (isCreditCardMessage(sender, normalizedBody)) {
            // Extract credit card last 4 digits
            val cardLast4 = extractCreditCardLast4(normalizedBody) ?: parsed.accountLast4

            // Extract available limit for credit card messages
            val creditLimit = extractAvailableLimit(normalizedBody) ?: parsed.creditLimit

            // Determine transaction type based on message content
            val transactionType = when {
                // Payment TO credit card (reducing debt)
                normalizedBody.contains("payment of", ignoreCase = true) &&
                        normalizedBody.contains("credited to your SBI Credit Card", ignoreCase = true) -> {
                    TransactionType.INCOME  // Payment received by credit card
                }
                // Credit card spending
                normalizedBody.contains("spent on", ignoreCase = true) ||
                        normalizedBody.contains("spent", ignoreCase = true) -> {
                    TransactionType.CREDIT  // Credit card expense
                }
                // Default for other credit card transactions
                else -> TransactionType.CREDIT
            }

            // Extract merchant for credit card transactions
            val merchant = when {
                normalizedBody.contains("via BBPS", ignoreCase = true) -> "BBPS Payment"
                else -> extractCreditCardMerchant(normalizedBody) ?: parsed.merchant
            }

            return parsed.copy(
                accountLast4 = cardLast4,
                type = transactionType,
                merchant = merchant ?: parsed.merchant,
                creditLimit = creditLimit,
                isFromCard = true
            )
        }

        return parsed
    }

    private fun normalizeUnicodeText(text: String): String {
        // NFKD decomposes Unicode Math Sans-Serif characters to ASCII equivalents
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("[^\\p{ASCII}]"), "")
    }

    private fun extractCreditCardMerchant(message: String): String? {
        // Pattern: "at MERCHANT on DD/MM/YY"
        val atPattern = Regex("""at\s+([A-Za-z0-9\s&._-]+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
        atPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }
        return null
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Pattern: "available limit is Rs.1,235.00"
        val patterns = listOf(
            Regex(
                """available\s+limit\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """Your\s+available\s+limit\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in patterns) {
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

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern for transaction number format: "transaction number 1234 for Rs.383.00"
        val transactionNumberPattern = Regex(
            """transaction\s+number\s+\d+\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        transactionNumberPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern for credit card payment: "payment of Rs.1,644.55"
        val paymentPattern =
            Regex("""payment\s+of\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        paymentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern for credit card spending: "Rs.259.00 spent"
        val spentPattern =
            Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+spent""", RegexOption.IGNORE_CASE)
        spentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 0: A/C debited by 20.0 (UPI format)
        val upiDebitPattern =
            Regex("""debited\s+by\s+(\d+(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        upiDebitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 0a: A/c credited by Rs.500 (UPI format)
        val upiCreditPattern = Regex(
            """credited\s+by\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        upiCreditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 1: Rs 500 debited
        val debitPattern1 = Regex(
            """Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+(?:has\s+been\s+)?debited""",
            RegexOption.IGNORE_CASE
        )
        debitPattern1.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: INR 500 debited
        val debitPattern2 = Regex(
            """INR\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+(?:has\s+been\s+)?debited""",
            RegexOption.IGNORE_CASE
        )
        debitPattern2.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: Rs 500 credited
        val creditPattern1 = Regex(
            """Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+(?:has\s+been\s+)?credited""",
            RegexOption.IGNORE_CASE
        )
        creditPattern1.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 4: INR 500 credited
        val creditPattern2 = Regex(
            """INR\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+(?:has\s+been\s+)?credited""",
            RegexOption.IGNORE_CASE
        )
        creditPattern2.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 5: withdrawn Rs 500
        val withdrawPattern =
            Regex("""withdrawn\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        withdrawPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 6: transferred Rs 500
        val transferPattern =
            Regex("""transferred\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        transferPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 7: UPI patterns - "paid to MERCHANT@upi Rs 500"
        val upiPattern = Regex(
            """paid\s+to\s+[\w.-]+@[\w]+\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        upiPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 8: ATM withdrawal - "ATM withdrawal of Rs 500"
        val atmPattern = Regex(
            """ATM\s+withdrawal\s+of\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        atmPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 9: YONO Cash withdrawal - "Yono Cash Rs 3000 w/d@SBI ATM"
        val yonoCashPattern =
            Regex("""Yono\s+Cash\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        yonoCashPattern.find(message)?.let { match ->
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

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // SBI-specific patterns
        return when {
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("transferred") -> TransactionType.EXPENSE
            lowerMessage.contains("paid to") -> TransactionType.EXPENSE
            lowerMessage.contains("atm withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("by sbi debit card") -> TransactionType.EXPENSE

            // Fall back to base class for common patterns
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern for "done at <location>": "done at -string of number redacted- on"
        val doneAtPattern =
            Regex("""done\s+at\s+([^.\n]+?)(?:\s+on\s+|$)""", RegexOption.IGNORE_CASE)
        doneAtPattern.find(message)?.let { match ->
            val location = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(location)) {
                return location
            }
        }

        // Pattern 0: trf to Merchant (UPI format)
        val trfPattern =
            Regex("""trf\s+to\s+([^.\n]+?)(?:\s+Ref|\s+ref|$)""", RegexOption.IGNORE_CASE)
        trfPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 0a: transfer from Sender (credit format)
        val transferFromPattern =
            Regex("""transfer\s+from\s+([^.\n]+?)(?:\s+Ref|\s+ref|$)""", RegexOption.IGNORE_CASE)
        transferFromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 1: paid to MERCHANT@upi
        val upiMerchantPattern = Regex("""paid\s+to\s+([\w.-]+)@[\w]+""", RegexOption.IGNORE_CASE)
        upiMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1])
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: YONO Cash ATM - "w/d@SBI ATM S1NW000093009"
        val yonoAtmPattern = Regex("""w/d@SBI\s+ATM\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        yonoAtmPattern.find(message)?.let { match ->
            val atmId = match.groupValues[1]
            return "YONO Cash ATM - $atmId"
        }

        // Pattern 2a: Regular ATM location
        val atmPattern = Regex(
            """ATM\s+(?:withdrawal\s+)?(?:at\s+)?([^.\n]+?)(?:\s+on|\s+Avl)""",
            RegexOption.IGNORE_CASE
        )
        atmPattern.find(message)?.let { match ->
            val location = cleanMerchantName(match.groupValues[1])
            if (isValidMerchantName(location)) {
                return "ATM - $location"
            }
        }

        // Pattern 3: NEFT/IMPS/RTGS with beneficiary
        val neftPattern = Regex(
            """(?:NEFT|IMPS|RTGS)[^:]*:\s*([^.\n]+?)(?:\s+Ref|\s+on|$)""",
            RegexOption.IGNORE_CASE
        )
        neftPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1])
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern for debit card: "by SBI Debit Card <last4>" - can be digits or redacted
        val debitCardPattern =
            Regex("""by\s+SBI\s+Debit\s+Card\s+([\w\-]+)""", RegexOption.IGNORE_CASE)
        debitCardPattern.find(message)?.let { match ->
            val cardInfo = match.groupValues[1]
            // If it's all digits and 4 characters, return as is
            // Otherwise try to extract last 4 digits if present
            return if (cardInfo.matches(Regex("""\d{4}"""))) {
                cardInfo
            } else {
                // Extract last 4 digits if available
                val digits = cardInfo.filter { it.isDigit() }
                if (digits.length >= 4) digits.takeLast(4) else cardInfo
            }
        }

        // Pattern 1: A/c XNNNN or A/c XXNNNN - extract everything after A/c
        val pattern1 = Regex("""A/c\s+([X\*]*\d+)""", RegexOption.IGNORE_CASE)
        pattern1.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            // Extract just the digits and take last 4
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else {
                digitsOnly
            }
        }

        // Pattern 2: from A/c ending 1234
        val pattern2 = Regex("""A/c\s+ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        pattern2.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: a/c no. XX1234
        val pattern3 = Regex("""a/c\s+no\.?\s+(?:XX|X\*+)?(\d{4})""", RegexOption.IGNORE_CASE)
        pattern3.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern for updated balance: "Your updated available balance is Rs.999999999"
        val updatedBalancePattern = Regex(
            """Your\s+updated\s+available\s+balance\s+is\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        updatedBalancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 1: Avl Bal Rs 1000.00
        val pattern1 =
            Regex("""Avl\s+Bal\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        pattern1.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Available Balance: Rs 1000
        val pattern2 = Regex(
            """Available\s+Balance:?\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pattern2.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: Bal: Rs 1000
        val pattern3 =
            Regex("""Bal:?\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        pattern3.find(message)?.let { match ->
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
        // Pattern for transaction number: "transaction number <alphanumeric>"
        val transactionNumberPattern =
            Regex("""transaction\s+number\s+([\w\-]+)""", RegexOption.IGNORE_CASE)
        transactionNumberPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 1: Ref No 123456789
        val pattern1 = Regex("""Ref\s+No\.?\s*(\w+)""", RegexOption.IGNORE_CASE)
        pattern1.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Txn# 123456
        val pattern2 = Regex("""Txn#\s*(\w+)""", RegexOption.IGNORE_CASE)
        pattern2.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: transaction ID 123456
        val pattern3 = Regex("""transaction\s+ID:?\s*(\w+)""", RegexOption.IGNORE_CASE)
        pattern3.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        // Normalize Unicode first (SBI Card uses Math Sans-Serif characters)
        val normalizedMessage = normalizeUnicodeText(message)
        val lowerMessage = normalizedMessage.lowercase()

        // Skip e-statement notifications
        if (lowerMessage.contains("e-statement of sbi credit card")) {
            return false
        }

        // Skip future/pending transactions
        if (lowerMessage.contains("is due for")) {
            return false
        }

        // Skip credit card application status messages
        if (lowerMessage.contains("sbi card application") ||
            lowerMessage.contains("process your app.no") ||
            lowerMessage.contains("track your application status")
        ) {
            return false
        }

        // Skip UPI-Mandate creation notifications
        if (isEMandateNotification(normalizedMessage) || isUPIMandateNotification(normalizedMessage)) {
            return false
        }

        // SBI Debit Card transactions
        if (lowerMessage.contains("by sbi debit card")) {
            return true
        }

        // SBI Credit Card spending
        if (lowerMessage.contains("spent") && lowerMessage.contains("credit card")) {
            return true
        }

        // Fall back to base class for other checks
        return super.isTransactionMessage(normalizedMessage)
    }

    // ==========================================
    // UPI-Mandate / Subscription Logic
    // ==========================================

    /**
     * Checks if this is a UPI-Mandate notification (not a transaction).
     * SBI sends specific UPI-Mandate messages for recurring payments.
     */
    fun isUPIMandateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("upi-mandate") ||
                lowerMessage.contains("upi mandate") ||
                (lowerMessage.contains("mandate") && lowerMessage.contains("created") && lowerMessage.contains("upi"))
    }

    /**
     * Parses UPI-Mandate subscription information from SBI messages.
     */
    fun parseUPIMandateSubscription(message: String): UPIMandateInfo? {
        if (!isUPIMandateNotification(message) && !isEMandateNotification(message)) {
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
            Regex("""mandate\s+created\s+for\s+([^.\n]+?)(?:\s+UMN|\s+of|\s*\.|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            pattern.find(message)?.let { match ->
                val m = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(m)) {
                    merchant = m
                }
            }
            if (merchant != "Unknown Subscription") break
        }

        // Extract next deduction date
        val datePatterns = listOf(
            Regex("""on\s+(\d{2}-\w{3}-\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""date[:\s]+(\d{2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE)
        )

        var nextDeductionDate: String? = null
        for (pattern in datePatterns) {
            pattern.find(message)?.let { match ->
                nextDeductionDate = match.groupValues[1]
            }
            if (nextDeductionDate != null) break
        }

        // Extract UMN (Unique Mandate Number)
        val umnPattern = Regex("""UMN[:\s]+([^.\s]+)""", RegexOption.IGNORE_CASE)
        val umn = umnPattern.find(message)?.groupValues?.get(1)

        return UPIMandateInfo(
            amount = amount!!,
            nextDeductionDate = nextDeductionDate,
            merchant = merchant,
            umn = umn
        )
    }

    /**
     * UPI-Mandate information for SBI Bank
     */
    data class UPIMandateInfo(
        override val amount: BigDecimal,
        override val nextDeductionDate: String?,
        override val merchant: String,
        override val umn: String?
    ) : MandateInfo {
        override val dateFormat = "dd-MMM-yy"
    }
}


