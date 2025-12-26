package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/**
 * Jammu & Kashmir Bank (JK Bank) specific parser.
 * Handles JK Bank's message formats including:
 * - Standard debit/credit messages
 * - UPI transactions
 * - Account number patterns
 * - Balance updates
 */
class JKBankParser : BaseIndianBankParser() {

    override fun getBankName() = "JK Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        // Common JK Bank sender IDs
        val jkBankSenders = setOf(
            "JKBANK",
            "JKB",
            "JKBANKL",
            "JKBNK"
        )

        // Direct match
        if (upperSender in jkBankSenders) return true

        // DLT patterns (AD-JKBANK-S, etc.)
        val dltPatterns = listOf(
            Regex("^[A-Z]{2}-JKBANK.*$"),
            Regex("^[A-Z]{2}-JKB.*$"),
            Regex("^[A-Z]{2}-JKBNK.*$"),
            Regex("^JKBANK-[A-Z]+$"),
            Regex("^JKB-[A-Z]+$")
        )

        return dltPatterns.any { it.matches(upperSender) }
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsedTransaction = super.parse(smsBody, sender, timestamp) ?: return null

        // Generate JK Bank specific transaction hash
        val customHash = generateJKBankHash(parsedTransaction, smsBody, sender)

        // Return with custom hash
        return parsedTransaction.copy(
            transactionHash = customHash
        )
    }

    private fun generateJKBankHash(
        transaction: ParsedTransaction,
        smsBody: String,
        sender: String
    ): String {
        val normalizedAmount = transaction.amount.setScale(2, RoundingMode.HALF_UP)

        // Use the already parsed reference to ensure consistency
        // This avoids issues where extractJKBankReference might have different patterns than extractReference
        val reference = transaction.reference
        val transactionTime = extractTransactionTime(smsBody)

        // Build hash based on what's available (in order of preference)
        val hashData = when {
            // BEST CASE: Amount + UTR/Ref + Transaction Time
            // This uniquely identifies the transaction even if SMS is sent multiple times
            reference != null && transactionTime != null -> {
                "JKBANK|${normalizedAmount}|REF:${reference}|TIME:${transactionTime}"
            }

            // GOOD: Amount + UTR/Ref (unique per transaction)
            reference != null -> {
                "JKBANK|${normalizedAmount}|REF:${reference}"
            }

            // GOOD: Amount + Transaction Time + Balance
            // Transaction time helps identify the actual transaction, not SMS time
            transactionTime != null && transaction.balance != null -> {
                val normalizedBalance = transaction.balance.setScale(2, RoundingMode.HALF_UP)
                "JKBANK|${normalizedAmount}|TIME:${transactionTime}|BAL:${normalizedBalance}"
            }

            // FALLBACK: Amount + Transaction Time
            transactionTime != null -> {
                "JKBANK|${normalizedAmount}|TIME:${transactionTime}"
            }

            // FALLBACK: Amount + Sender + Closing Balance
            // Balance helps differentiate multiple transactions of same amount
            transaction.balance != null -> {
                val normalizedBalance = transaction.balance.setScale(2, RoundingMode.HALF_UP)
                "JKBANK|${normalizedAmount}|${sender}|BAL:${normalizedBalance}"
            }

            // LAST RESORT: Original method (Amount + Sender + SMS Timestamp)
            // Only used if no transaction-specific data is available
            else -> {
                "${sender}|${normalizedAmount}|${transaction.timestamp}"
            }
        }

        return MessageDigest.getInstance("MD5")
            .digest(hashData.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun extractJKBankReference(message: String): String? {
        val patterns = listOf(
            // RTGS-JAKAH25085024027
            Regex("""RTGS-([A-Z0-9]+)"""),
            // NEFT/IMPS references
            Regex("""NEFT-([A-Z0-9]+)"""),
            Regex("""IMPS-([A-Z0-9]+)"""),
            // UTR/TRN numbers
            Regex("""UTR\s+([A-Z0-9]+)"""),
            Regex("""TRN\s+([A-Z0-9]+)"""),
            // CHRGS/RTGS/BWY - unique charge reference
            Regex("""by\s+(CHRGS/[^.]+)"""),
            // eTFR/mTFR references
            Regex("""by\s+(eTFR/[^.]+)"""),
            Regex("""by\s+(mTFR/\d+/[^.]+)"""),
            // UPI reference
            Regex("""UPI\s+Ref[:\s]+(\d+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        return null
    }

    private fun extractTransactionTime(message: String): String? {
        // Extract transaction time from JK Bank messages
        // Pattern: "at 10:43 by" or "on 17-Sep-24 at 10:43"
        val patterns = listOf(
            // Time only: "at 10:43"
            Regex("""at\s+(\d{1,2}:\d{2}(?::\d{2})?)""", RegexOption.IGNORE_CASE),
            // Date and time: "on 17-Sep-24 at 10:43"
            Regex(
                """on\s+(\d{1,2}-\w{3}-\d{2,4})\s+at\s+(\d{1,2}:\d{2})""",
                RegexOption.IGNORE_CASE
            ),
            // Date only: "on 17-Sep-24"
            Regex("""on\s+(\d{1,2}-\w{3}-\d{2,4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return when (match.groupValues.size) {
                    2 -> match.groupValues[1] // Time only
                    3 -> "${match.groupValues[1]} ${match.groupValues[2]}" // Date and time
                    else -> null
                }
            }
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Check for IMPS Fund transfer pattern first
        // "Amt received from TRUEFILLINGS ADVISOR having A/C No."
        if (message.contains("IMPS Fund transfer", ignoreCase = true)) {
            val impsPattern = Regex(
                """Amt\s+received\s+from\s+([^h]+?)(?:\s+having\s+A/C|$)""",
                RegexOption.IGNORE_CASE
            )
            impsPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }

            // Fallback pattern for "received from"
            val fromPattern = Regex(
                """received\s+from\s+([^.\n]+?)(?:\s+having|\s+with|$)""",
                RegexOption.IGNORE_CASE
            )
            fromPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }

            return "IMPS Transfer"
        }

        // Check for TIN/Tax Information Network (handles both full and truncated versions)
        if (message.contains("TIN/Tax Information", ignoreCase = true) ||
            message.contains("TIN/Tax Informat", ignoreCase = true)
        ) {
            return "Tax Information Network"
        }

        // Check for ATM Recovery and other charges
        if (message.contains("ATM RECOVERY", ignoreCase = true)) {
            return "ATM Recovery Charge"
        }

        // Check for "towards" pattern - common for tax and other payments
        val towardsPattern = Regex(
            """towards\s+([^.\n]+?)(?:\.\s*Avl|\.\s*Available|\.\s*To\s+dispute|$)""",
            RegexOption.IGNORE_CASE
        )
        towardsPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()

            // Special handling for TIN/Tax patterns
            if (merchant.contains("TIN/Tax Informat", ignoreCase = true) ||
                merchant.contains("TIN/Tax Information", ignoreCase = true)
            ) {
                return "Tax Information Network"
            }

            // Return the merchant name, cleaning it up
            return cleanMerchantName(merchant)
        }

        // Check for transaction patterns "by XXX" but skip the amount part
        // Pattern: "Debited by INR 402393 at 10:43 by RTGS-..."
        val transactionByPattern = Regex(
            """(?:Debited|Credited)\s+by\s+INR\s+[\d,]+(?:\.\d{2})?\s+at\s+[\d:]+\s+by\s+([^.\n]+?)(?:\.|Available|$)""",
            RegexOption.IGNORE_CASE
        )
        transactionByPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()

            return when {
                // Bank charges patterns - return null as these are internal bank charges
                merchant.contains("CHRGS", ignoreCase = true) ||
                        merchant.contains("CHARGES", ignoreCase = true) -> null
                // Check for specific institutions
                merchant.contains(
                    "INDIAN CLEARING CORPO",
                    ignoreCase = true
                ) -> "Indian Clearing Corporation"

                merchant.contains("CLEARING CORPO", ignoreCase = true) -> "Clearing Corporation"
                merchant.contains("NSE CLEARING", ignoreCase = true) -> "NSE Clearing"
                merchant.contains("BSE CLEARING", ignoreCase = true) -> "BSE Clearing"
                // Generic transfer types (only if not charges)
                merchant.contains("RTGS", ignoreCase = true) && !merchant.contains(
                    "CLEARING",
                    ignoreCase = true
                ) -> "RTGS Transfer"

                merchant.contains("NEFT", ignoreCase = true) -> "NEFT Transfer"
                merchant.contains("IMPS", ignoreCase = true) -> "IMPS Transfer"
                merchant.contains("eTFR", ignoreCase = true) -> "Transfer"
                merchant.contains("mTFR", ignoreCase = true) -> {
                    // Extract the actual recipient name from mTFR/phone/NAME pattern
                    val mtfrMatch =
                        Regex("""mTFR/\d+/(.+)""", RegexOption.IGNORE_CASE).find(merchant)
                    mtfrMatch?.let {
                        cleanMerchantName(it.groupValues[1].trim())
                    } ?: "Mobile Transfer"
                }

                merchant.contains("TIN", ignoreCase = true) -> "Tax Information Network"
                else -> cleanMerchantName(merchant.substringBefore("/"))
            }
        }

        // Fallback pattern for simpler "by XXX" format
        val simpleByPattern =
            Regex("""by\s+([^.\n]+?)(?:\.|Available|$)""", RegexOption.IGNORE_CASE)
        simpleByPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Skip if it starts with INR (amount)
            if (!merchant.startsWith("INR", ignoreCase = true)) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 1: "via UPI from SENDER NAME on" (for credits)
        if (message.contains("via UPI from", ignoreCase = true)) {
            val fromPattern =
                Regex("""via\s+UPI\s+from\s+([^.\n]+?)\s+on""", RegexOption.IGNORE_CASE)
            fromPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (isValidMerchantName(merchant)) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Pattern 2: "by mTFR/962211111/SENDER NAME" (mPay transfer)
        // mTFR = mPay transfer, followed by mobile number, then sender name
        val mtfrPattern = Regex("""mTFR/\d+/([^.\n]+?)(?:\.|A/C|$)""", RegexOption.IGNORE_CASE)
        mtfrPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (isValidMerchantName(merchant)) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 3: UPI transactions to merchant
        if (message.contains("via UPI", ignoreCase = true)) {
            // Look for UPI VPA pattern
            val vpaPattern = Regex("""to\s+([^@\s]+@[^\s]+)""", RegexOption.IGNORE_CASE)
            vpaPattern.find(message)?.let { match ->
                val vpa = match.groupValues[1].trim()
                // Extract the part before @ as merchant name
                val merchantName = vpa.substringBefore("@")
                if (merchantName.isNotEmpty() && merchantName != "upi") {
                    return cleanMerchantName(merchantName)
                }
            }

            // Look for merchant after "to" but before "via UPI"
            val toMerchantPattern =
                Regex("""to\s+([^.\n]+?)\s+via\s+UPI""", RegexOption.IGNORE_CASE)
            toMerchantPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (isValidMerchantName(merchant)) {
                    return cleanMerchantName(merchant)
                }
            }

            // Default to "UPI" if no specific merchant found
            return "UPI"
        }

        // Check for ATM withdrawals
        if (message.contains("ATM", ignoreCase = true) ||
            message.contains("withdrawn", ignoreCase = true)
        ) {
            return "ATM"
        }

        // Standard patterns for merchant extraction
        val merchantPatterns = listOf(
            // Pattern for "to MERCHANT via"
            Regex("""to\s+([^.\n]+?)\s+via""", RegexOption.IGNORE_CASE),
            // Pattern for "from MERCHANT"
            Regex("""from\s+([^.\n]+?)(?:\s+on|\s+Ref|$)""", RegexOption.IGNORE_CASE),
            // Pattern for "at MERCHANT"
            Regex("""at\s+([^.\n]+?)(?:\s+on|\s+Ref|$)""", RegexOption.IGNORE_CASE),
            // Pattern for "for MERCHANT"
            Regex("""for\s+([^.\n]+?)(?:\s+on|\s+Ref|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Fall back to base extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Check for investment-related transactions first
        if (lowerMessage.contains("clearing corpo") ||
            lowerMessage.contains("indian clearing") ||
            lowerMessage.contains("nse clearing") ||
            lowerMessage.contains("bse clearing") ||
            lowerMessage.contains("iccl") ||
            lowerMessage.contains("nsccl")
        ) {
            // Clearing corporations handle investment transactions
            // Credits are redemptions/dividends, debits are investments
            return when {
                lowerMessage.contains("credited") -> TransactionType.INVESTMENT
                lowerMessage.contains("debited") -> TransactionType.INVESTMENT
                else -> null
            }
        }

        return when {
            // JK Bank specific patterns
            lowerMessage.contains("has been debited") -> TransactionType.EXPENSE
            lowerMessage.contains("has been credited") -> TransactionType.INCOME

            // Standard expense keywords
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("transferred") -> TransactionType.EXPENSE

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
        // JK Bank specific reference patterns
        val jkBankPatterns = listOf(
            // RRN No.1234567890 for IMPS transfers
            Regex("""RRN\s+No\.?\s*(\d+)""", RegexOption.IGNORE_CASE),
            // UPI Ref: 115458170728
            Regex("""UPI\s+Ref[:\s]+(\d+)""", RegexOption.IGNORE_CASE),
            // txn Ref: XXXXX
            Regex("""txn\s+Ref[:\s]+([A-Z0-9]+)""", RegexOption.IGNORE_CASE),
            // Reference: XXXXX
            Regex("""Reference[:\s]+([A-Z0-9]+)""", RegexOption.IGNORE_CASE),
            // Ref No: XXXXX
            Regex("""Ref\s+No[:\s]+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in jkBankPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        // Fall back to base extraction
        return super.extractReference(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // JK Bank specific account patterns
        val jkBankPatterns = listOf(
            // Your A/c XXXXXXXX1111
            Regex("""Your\s+A\/c\s+[X]+(\d{4})""", RegexOption.IGNORE_CASE),
            // JK Bank A/c no. XXXXXXXX9651
            Regex("""JK\s+Bank\s+A\/c\s+no\.\s+[X]+(\d{4})""", RegexOption.IGNORE_CASE),
            // A/c XXX8953 (3 X's followed by 4 digits)
            Regex("""A\/c\s+X{3}(\d{4})""", RegexOption.IGNORE_CASE),
            // A/c XXXXXXXX1111 or A/c XX1111
            Regex("""A\/c\s+[X]*(\d{4})""", RegexOption.IGNORE_CASE),
            // Account XXXXXXXX1111
            Regex("""Account\s+[X]+(\d{4})""", RegexOption.IGNORE_CASE),
            // from A/c ending 1111
            Regex("""A\/c\s+ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in jkBankPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        // Fall back to base extraction
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // JK Bank specific balance patterns
        val balancePatterns = listOf(
            // Available Bal is INR XXXX Cr/Dr
            Regex(
                """Available\s+Bal\s+is\s+INR\s*([0-9,]+(?:\.\d{2})?)\s*(?:Cr|Dr)?""",
                RegexOption.IGNORE_CASE
            ),
            // A/C Bal is INR XXXX Cr/Dr
            Regex(
                """A/C\s+Bal\s+is\s+INR\s*([0-9,]+(?:\.\d{2})?)\s*(?:Cr|Dr)?""",
                RegexOption.IGNORE_CASE
            ),
            // Avl Bal: Rs.XXXX
            Regex("""Avl\s+Bal[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // Balance: Rs.XXXX
            Regex("""Balance[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // Bal Rs.XXXX
            Regex("""Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        // Fall back to base extraction
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

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

        // Skip payment request messages
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment")
        ) {
            return false
        }

        // Skip RTGS/NEFT/IMPS confirmation messages
        // These are confirmations of transactions that already happened
        // Example: "Your RTGS Txn with UTR ... has been credited on ..."
        if (lowerMessage.contains("your rtgs txn") && lowerMessage.contains("has been credited")) {
            return false
        }
        if (lowerMessage.contains("your neft txn") && lowerMessage.contains("has been credited")) {
            return false
        }
        if (lowerMessage.contains("your imps txn") && lowerMessage.contains("has been credited")) {
            return false
        }

        // Skip messages asking to report fraud
        // But make sure the transaction keywords are present
        if (lowerMessage.contains("if not done by you") ||
            lowerMessage.contains("report immediately")
        ) {
            // These are usually part of transaction messages, so check for transaction keywords
            val transactionKeywords = listOf(
                "debited", "credited", "withdrawn", "deposited",
                "spent", "received", "transferred", "paid"
            )
            return transactionKeywords.any { lowerMessage.contains(it) }
        }

        // JK Bank specific transaction keywords
        val jkBankTransactionKeywords = listOf(
            "has been debited",
            "has been credited",
            "debited",
            "credited",
            "withdrawn",
            "deposited",
            "spent",
            "received",
            "transferred",
            "paid"
        )

        return jkBankTransactionKeywords.any { lowerMessage.contains(it) }
    }
}
