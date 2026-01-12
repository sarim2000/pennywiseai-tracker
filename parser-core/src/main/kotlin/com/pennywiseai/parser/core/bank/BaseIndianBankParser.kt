package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.CompiledPatterns
import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Base abstract class for Indian bank parsers.
 * Handles common patterns across Indian banks (INR currency, UPI, etc.).
 */
abstract class BaseIndianBankParser : BankParser() {

    override fun getCurrency() = "INR"

    /**
     * Checks if the message is for an investment transaction.
     * Contains keywords specific to Indian investment platforms and terms.
     */
    override fun isInvestmentTransaction(lowerMessage: String): Boolean {
        val investmentKeywords = listOf(
            // Clearing corporations
            "iccl",                         // Indian Clearing Corporation Limited
            "indian clearing corporation",
            "nsccl",                        // NSE Clearing Corporation
            "nse clearing",
            "clearing corporation",

            // Auto-pay indicators (excluding mandate/UMRN to avoid subscription false positives)
            "nach",                         // National Automated Clearing House
            "ach",                          // Automated Clearing House
            "ecs",                          // Electronic Clearing Service

            // Investment platforms
            "groww",
            "zerodha",
            "upstox",
            "kite",
            "kuvera",
            "paytm money",
            "etmoney",
            "coin by zerodha",
            "smallcase",
            "angel one",
            "angel broking",
            "5paisa",
            "icici securities",
            "icici direct",
            "hdfc securities",
            "kotak securities",
            "motilal oswal",
            "sharekhan",
            "edelweiss",
            "axis direct",
            "sbi securities",

            // Investment types
            "mutual fund",
            "sip",                          // Systematic Investment Plan
            "elss",                         // Tax saving funds
            "ipo",                          // Initial Public Offering
            "folio",                        // Mutual fund folio
            "demat",
            "stockbroker",
            "digital gold",                 // Digital Gold investments
            "sovereign gold",               // Sovereign Gold Bonds

            // Stock exchanges
            "nse",                          // National Stock Exchange
            "bse",                          // Bombay Stock Exchange
            "cdsl",                         // Central Depository Services
            "nsdl"                          // National Securities Depository
        )

        return investmentKeywords.any { lowerMessage.contains(it) }
    }

    // ==========================================
    // Unified Mandate / Subscription Logic
    // ==========================================

    /**
     * Checks if this is an E-Mandate notification (not a transaction).
     */
    open fun isEMandateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("e-mandate") ||
                lowerMessage.contains("upi-mandate") ||
                (lowerMessage.contains("mandate") && lowerMessage.contains("successfully created"))
    }

    /**
     * Checks if this is a future debit notification (subscription alert, not a current transaction).
     */
    open fun isFutureDebitNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("will be debited") ||
                lowerMessage.contains("mandate set for") ||
                (lowerMessage.contains("upcoming") && lowerMessage.contains("mandate"))
    }

    /**
     * Parses combined Mandate / E-Mandate / UPI-Mandate subscription information.
     * Returns a general MandateInfo implementation.
     */
    open fun parseMandateSubscription(message: String): MandateInfo? {
        if (!isEMandateNotification(message) && !isFutureDebitNotification(message)) {
            return null
        }

        // 1. Extract amount
        // Patterns: "Rs.1050.00", "INR 59.00", "Rs 123.45"
        val amount = CompiledPatterns.Amount.INR_PATTERN.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: CompiledPatterns.Amount.RS_PATTERN.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        // 2. Extract merchant
        // Patterns: "towards Google Play", "for Netflix", "Info: Spotify"
        var merchant = "Unknown Subscription"
        val merchantPatterns = listOf(
            Regex("""towards\s+([^.\n]+?)(?:\s+from|\s+A/c|\s+UMRN|\s+ID:|\s+Alert:|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""for\s+([^.\n]+?)(?:\s+ID:|\s+Act:|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""Info:\s*([^.\n]+?)(?:\s*$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            pattern.find(message)?.let { match ->
                val m = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(m)) merchant = m
            }
        }

        // 3. Extract date (for future debits)
        // Patterns: "on 29-May-25", "set for 29-May-25"
        // Matches DD-MMM-YY, dd/MM/yyyy formats common in Indian banks
        val datePattern = Regex("""(?:on|for)\s+(${CompiledPatterns.Date.DD_MMM_YY.pattern}|${CompiledPatterns.Date.DD_MM_YYYY.pattern})""", RegexOption.IGNORE_CASE)
        val dateStr = datePattern.find(message)?.groupValues?.get(1)?.let { rawDate ->
            // Normalize slashes to dashes if needed or keep as is, consumer will parse
            rawDate
        }

        // 4. Extract UMN (Unique Mandate Number) if present
        val umnPattern = Regex("""UMN[:\s]+([^.\s]+)""", RegexOption.IGNORE_CASE)
        val umn = umnPattern.find(message)?.groupValues?.get(1)

        return object : MandateInfo {
            override val amount = amount
            override val nextDeductionDate = dateStr
            override val merchant = merchant
            override val umn = umn
            override val dateFormat = "dd-MMM-yy" // Default fallback
        }
    }

    // ==========================================
    // Unified Balance Update Logic
    // ==========================================

    /**
     * Checks if this is a balance update notification (not a transaction).
     */
    open fun isBalanceUpdateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for balance update patterns
        // Must contain "Available Balance" or similar keywords
        // And typically "as on" or "is Rs." without transaction words like "debited", "spent"
        val hasBalanceKeyword = lowerMessage.contains("available bal") ||
                lowerMessage.contains("avl bal") ||
                lowerMessage.contains("account balance") ||
                lowerMessage.contains("a/c balance") ||
                lowerMessage.contains("updated balance")

        val hasTxnKeyword = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("spent") ||
                lowerMessage.contains("transferred") ||
                lowerMessage.contains("payment of")

        return hasBalanceKeyword && !hasTxnKeyword
    }

    data class BaseBalanceUpdateInfo(
        val bankName: String,
        val accountLast4: String?,
        val balance: BigDecimal,
        val asOfDate: LocalDateTime? = null
    )

    /**
     * Parses generic balance update notification.
     */
    open fun parseBalanceUpdate(message: String): BaseBalanceUpdateInfo? {
        if (!isBalanceUpdateNotification(message)) {
            return null
        }

        // Extract account last 4 digits
        val accountLast4 = extractAccountLast4(message)

        // Extract balance amount
        // Patterns: "is Rs. 12,345", "Avl Bal Rs 12345"
        val balance = extractBalance(message) ?: return null

        return BaseBalanceUpdateInfo(
            bankName = getBankName(),
            accountLast4 = accountLast4,
            balance = balance
        )
    }

    // ==========================================
    // Common Helper Methods
    // ==========================================

    /**
     * Helper function to convert month abbreviation to number.
     */
    protected fun getMonthNumber(monthAbbr: String): Int {
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
