package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for UCO Bank SMS messages
 *
 * Supported formats:
 * - Debit: "A/c XX1111 Debited with Rs.2000.00 on 21-09-2025 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in/Home_Page.jsp"
 * - Credit: "A/c XX1111 Credited with Rs.2,000.00 on 21-09-2025 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in/Home_Page.jsp -UCO Bank"
 *
 * Sender patterns: XX-UCOBNK-S (where XX can be any two letters)
 */
class UCOBankParser : BankParser() {

    override fun getBankName() = "UCO Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("UCOBNK") ||
                normalizedSender.contains("UCOBANK") ||
                normalizedSender.contains("UCO BANK") ||
                // DLT patterns with any two-letter prefix followed by -UCOBNK-S
                normalizedSender.matches(Regex("^[A-Z]{2}-UCOBNK-[ST]$")) ||
                // Other variations
                normalizedSender.matches(Regex("^[A-Z]{2}-UCOBNK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-UCOBANK$"))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Read the amount from the "Debited/Credited with" clause itself. A bare
        // "first Rs. in the message" search is unsafe here: when the amount
        // doesn't match (e.g. "Rs..50", a sub-rupee amount printed without its
        // leading zero) it walks on to the next "Rs." — the Avl Bal — and
        // records the whole balance as the transaction.
        DEBIT_CREDIT_AMOUNT.find(message)?.let { match ->
            return toAmount(match.groupValues[1])
        }

        // Anything else: only look before the balance clause, so the fallback
        // can never pick up the balance either.
        val beforeBalance = BALANCE_CLAUSE.find(message)?.let { message.substring(0, it.range.first) } ?: message
        ANY_AMOUNT.find(beforeBalance)?.let { match ->
            return toAmount(match.groupValues[1])
        }

        return super.extractAmount(beforeBalance)
    }

    /** "2,000.00" -> 2000.00; ".50" -> 0.50. */
    private fun toAmount(raw: String): BigDecimal? = try {
        val cleaned = raw.replace(",", "")
        BigDecimal(if (cleaned.startsWith(".")) "0$cleaned" else cleaned)
    } catch (e: NumberFormatException) {
        null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debited with") -> TransactionType.EXPENSE
            lowerMessage.contains("credited with") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // UCO Bank format: "by UCO-UPI" or "by <merchant>"
        val merchantPattern = Regex("""by\s+([^.]+?)(?:\.Avl|$)""", RegexOption.IGNORE_CASE)
        merchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()

            // Handle UCO-UPI transactions
            if (merchant.contains("UCO-UPI", ignoreCase = true)) {
                return "UPI Transfer"
            }

            // Clean up common suffixes
            return cleanMerchantName(merchant)
        }

        // Fall back to base class extraction
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // UCO Bank format: "A/c XX1111"
        val accountPatterns = listOf(
            Regex("""A/c\s+([X*\d]+)""", RegexOption.IGNORE_CASE),
            Regex("""Account\s+([X*\d]+)""", RegexOption.IGNORE_CASE),
            Regex("""Acc\s+([X*\d]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                return extractLast4Digits(match.groupValues[1])
            }
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // UCO Bank format: "Avl Bal Rs.11111.11"
        val balancePatterns = listOf(
            // "Avl Bal Rs.11111.11" and "Avl Bal in your A/c is Rs.2,992.54".
            // The connecting words are spelled out rather than skipped with a
            // wildcard: an open gap would let "Avl Bal unavailable ... charge
            // Rs.10.00" record the charge as the balance.
            Regex("""Avl\s+Bal(?:\s+in\s+your\s+A/c\s+is)?\s*[:.]?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex(
                """Available\s+Balance\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""Balance[:.]?\s*Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
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

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Look for any transaction reference patterns specific to UCO Bank
        val refPatterns = listOf(
            Regex("""ref[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""txn[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE),
            Regex("""transaction\s+id[:#]?\s*([\w]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in refPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        return super.extractReference(message)
    }

    private companion object {
        /** Rupees with or without a leading zero: "2,000.00", "50.00", ".50". */
        private const val AMOUNT = """(\d[\d,]*(?:\.\d{1,2})?|\.\d{1,2})"""

        val DEBIT_CREDIT_AMOUNT = Regex(
            """(?:debited|credited)\s+with\s+Rs\.?\s*$AMOUNT""",
            RegexOption.IGNORE_CASE
        )
        val ANY_AMOUNT = Regex("""Rs\.?\s*$AMOUNT""", RegexOption.IGNORE_CASE)
        val BALANCE_CLAUSE = Regex("""Avl\s+Bal|Available\s+Balance""", RegexOption.IGNORE_CASE)
    }
}
