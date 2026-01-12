package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Dhanlaxmi Bank SMS messages
 *
 * Supported formats:
 * - UPI debits: "INR 20.00 is debited from A/c XXXX1234 on 28-NOV-2025 - "UPI TXN: ..."
 * - UPI credits: "INR 10.00 is credited to A/c XXXX1234 on 24-APR-2025 - "UPI TXN: ..."
 * - Internal transfers: "Your a/c no. XXXXXXXX1234 is credited for Rs.10.00 on 24-04-25..."
 *
 * Sender patterns: TL-DHANBK-S, VM-DHANBK, etc.
 */
class DhanlaxmiBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Dhanlaxmi Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("DHANBK") ||
                normalizedSender.contains("DHANLAXMI") ||
                normalizedSender.matches(Regex("^[A-Z]{2}-DHANBK-?[A-Z]?$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-DHANBK$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "INR 20.00 is debited" or "INR 10.00 is credited"
        val inrPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+is\s+(?:debited|credited)""",
            RegexOption.IGNORE_CASE
        )
        inrPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "credited for Rs.10.00" or "debited for Rs.10.00"
        val rsPattern = Regex(
            """(?:credited|debited)\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        rsPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            lowerMessage.contains("is credited") -> TransactionType.INCOME
            lowerMessage.contains("debited from") -> TransactionType.EXPENSE
            lowerMessage.contains("credited to") -> TransactionType.INCOME
            lowerMessage.contains("credited for") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "A/c XXXX1234" or "A/c XX1234"
        val acPattern = Regex(
            """A/c\s+X+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        acPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "a/c no. XXXXXXXX1234"
        val acNoPattern = Regex(
            """a/c\s+no\.\s*X+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        acNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Aval Bal is INR 26,578.49" or "Aval Bal is INR  26,578.49"
        val balancePattern = Regex(
            """Aval\s+Bal\s+is\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // For UPI transactions, try to extract from the transaction description
        // Pattern: "UPI TXN: /675325120952-MR /Payment from PhonePe/..."
        if (message.contains("UPI TXN", ignoreCase = true)) {
            // Try to extract payment app or merchant from description
            // Stop at /, ", or end of quoted section
            val paymentFromPattern = Regex(
                """Payment\s+from\s+([^/"]+)""",
                RegexOption.IGNORE_CASE
            )
            paymentFromPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

            // Try to extract "payment on <merchant>" pattern
            // Stop at whitespace, /, ", or using
            val paymentOnPattern = Regex(
                """payment\s+on\s+(\w+)""",
                RegexOption.IGNORE_CASE
            )
            paymentOnPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

            return "UPI Payment"
        }

        // For internal transfers
        if (message.contains("debited from a/c", ignoreCase = true) &&
            message.contains("credited", ignoreCase = true)
        ) {
            return "Internal Transfer"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: UPI Ref no in transaction description
        val upiRefPattern = Regex(
            """UPI\s+Ref\s+no\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Reference number from UPI TXN pattern - e.g., "/675325120952-MR"
        val txnRefPattern = Regex(
            """UPI\s+TXN:\s*/(\d+)""",
            RegexOption.IGNORE_CASE
        )
        txnRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Dhanlaxmi Bank specific transaction keywords
        val dhanlaxmiKeywords = listOf(
            "is debited from",
            "is credited to",
            "credited for",
            "debited from a/c"
        )

        if (dhanlaxmiKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}
