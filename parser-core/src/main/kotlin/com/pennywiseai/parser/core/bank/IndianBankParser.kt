package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Indian Bank
 *
 * Common sender patterns:
 * - Service Implicit (transactions): XX-INDBNK-S (e.g., AD-INDBNK-S, AX-INDBNK-S)
 * - OTP: XX-INDBNK-T
 * - Promotional: XX-INDBNK-P
 * - Direct: INDBNK, INDIAN
 */
class IndianBankParser : BaseIndianBankParser() {
    override fun getBankName() = "Indian Bank"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("INDIAN BANK") ||
                normalized.contains("INDIANBANK") ||
                normalized.contains("INDIANBK") ||
                // Match DLT patterns for transactions (-S suffix)
                normalized.matches(Regex("^[A-Z]{2}-INDBNK-S$")) ||
                // Also handle other patterns for completeness
                normalized.matches(Regex("^[A-Z]{2}-INDBNK-[TPG]$")) ||
                // Legacy patterns without suffix
                normalized.matches(Regex("^[A-Z]{2}-INDBNK$")) ||
                // Direct sender IDs
                normalized == "INDBNK" ||
                normalized == "INDIAN"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: debited Rs. 19000.00
        val debitPattern =
            Regex("""debited\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        debitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: credited Rs. 5000.00
        val creditPattern =
            Regex("""credited\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        creditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2a: Rs.589.00 credited to (amount before credited)
        val creditPatternReverse = Regex(
            """Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)\s+credited\s+to""",
            RegexOption.IGNORE_CASE
        )
        creditPatternReverse.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 3: withdrawn Rs. 2000
        val withdrawnPattern =
            Regex("""withdrawn\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        withdrawnPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 4: UPI payment of Rs. 500
        val upiPattern = Regex(
            """UPI\s+payment\s+of\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
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

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "to Merchant Name"
        val toPattern = Regex("""to\s+([^.\n]+?)(?:\.\s*UPI:|UPI:|$)""", RegexOption.IGNORE_CASE)
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "from Sender Name"
        val fromPattern =
            Regex("""from\s+([^.\n]+?)(?:\.\s*UPI:|UPI:|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2a: "linked to VPA 7970282159-2@axl" - extract VPA
        val vpaPattern = Regex("""VPA\s+([\w.-]+@[\w]+)""", RegexOption.IGNORE_CASE)
        vpaPattern.find(message)?.let { match ->
            val vpa = match.groupValues[1]
            // Extract the part before @ as merchant name
            val merchantFromVpa = vpa.substringBefore("@")
            return cleanMerchantName(merchantFromVpa)
        }

        // Pattern 3: ATM withdrawal at location
        val atmPattern =
            Regex("""ATM\s+(?:withdrawal\s+)?at\s+([^.\n]+?)(?:\s+on|$)""", RegexOption.IGNORE_CASE)
        atmPattern.find(message)?.let { match ->
            val location = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(location)) {
                return "ATM - $location"
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: A/c *1234
        val pattern1 = Regex("""A/c\s+\*(\d{4})""", RegexOption.IGNORE_CASE)
        pattern1.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Account XX1234 or XXXX1234
        val pattern2 = Regex("""Account\s+X*(\d{4})""", RegexOption.IGNORE_CASE)
        pattern2.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: A/c ending 1234
        val pattern3 = Regex("""A/c\s+ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        pattern3.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: UPI:515314436916
        val upiRefPattern = Regex("""UPI:(\d+)""", RegexOption.IGNORE_CASE)
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 1a: UPI Ref no 917477824021
        val upiRefNoPattern = Regex("""UPI\s+Ref\s+no\s+(\d+)""", RegexOption.IGNORE_CASE)
        upiRefNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Ref No. 123456
        val refNoPattern = Regex("""Ref\s+No\.?\s*(\w+)""", RegexOption.IGNORE_CASE)
        refNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: Transaction ID: ABC123
        val txnIdPattern = Regex("""Transaction\s+ID:?\s*(\w+)""", RegexOption.IGNORE_CASE)
        txnIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern 1: Bal Rs. 50000.00
        val balPattern1 =
            Regex("""Bal\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        balPattern1.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: Available Balance: Rs. 25000
        val balPattern2 = Regex(
            """Available\s+Balance:?\s+Rs\.?\s*(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balPattern2.find(message)?.let { match ->
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

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Indian Bank specific patterns
        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("upi payment") && !lowerMessage.contains("received") -> TransactionType.EXPENSE

            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME

            // Fall back to base class for other patterns
            else -> super.extractTransactionType(message)
        }
    }

    // ==========================================
    // Mandate / Subscription Logic
    // ==========================================

    /**
     * Checks if this is a mandate notification (not a transaction).
     * Delegates to base class E-Mandate and future debit checks.
     */
    fun isMandateNotification(message: String): Boolean {
        return isEMandateNotification(message) || isFutureDebitNotification(message)
    }

    /**
     * Parses mandate subscription information from Indian Bank messages.
     * Uses base class logic but returns bank-specific type.
     */
    override fun parseMandateSubscription(message: String): IndianMandateInfo? {
        val baseInfo = super.parseMandateSubscription(message) ?: return null

        return IndianMandateInfo(
            amount = baseInfo.amount,
            nextDeductionDate = baseInfo.nextDeductionDate,
            merchant = baseInfo.merchant,
            umn = baseInfo.umn
        )
    }

    /**
     * Mandate information for Indian Bank
     */
    data class IndianMandateInfo(
        override val amount: BigDecimal,
        override val nextDeductionDate: String?,
        override val merchant: String,
        override val umn: String?
    ) : MandateInfo {
        override val dateFormat = "dd-MMM-yy"
    }
}


