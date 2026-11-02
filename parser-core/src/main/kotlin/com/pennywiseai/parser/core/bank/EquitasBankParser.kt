package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Equitas Small Finance Bank SMS messages
 *
 * Sender patterns: XX-EQUTAS-S, XX-EQUITA-S, EQUTAS, EQUITA
 *
 * SMS Formats:
 * - UPI Debit: "INR XXX.00 debited via UPI from Equitas A/c 12XX -Ref:57198707XXXX on 19-12-25 to MERCHANT. Avl Bal is INR XX,XXX.XX..."
 * - UPI Credit: "INR XXX.00 credited via UPI to Equitas A/c 12XX -Ref:XXXX on DD-MM-YY from SENDER. Avl Bal is INR XX,XXX.XX..."
 */
class EquitasBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Equitas Small Finance Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("EQUTAS") ||
                normalizedSender.contains("EQUITA") ||
                normalizedSender.contains("EQUITS")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "INR XXX.00 debited" or "INR XXX.00 credited"
        val amountPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+(?:debited|credited)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
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
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Check if it's a debit or credit transaction
        val isDebit = lowerMessage.contains("debited")
        val isCredit = lowerMessage.contains("credited")

        if (isDebit) {
            // Pattern for UPI debit: "to MERCHANT_NAME." or "to MERCHANT_NAME. Avl"
            // Look for "to" after the date pattern (on DD-MM-YY)
            val toAfterDatePattern = Regex(
                """on\s+\d{2}-\d{2}-\d{2}\s+to\s+([^.]+?)(?:\.\s*Avl|\.\s*Not|\.Not|\.$)""",
                RegexOption.IGNORE_CASE
            )
            toAfterDatePattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

            // Pattern for UPI debit: "to <merchant> on <date>" (e.g. to Zomato Media Pri on 30-10-26)
            val toBeforeDatePattern = Regex(
                """to\s+([^.]+?)\s+on\s+\d{2}[-/]\d{2}[-/]\d{2,4}""",
                RegexOption.IGNORE_CASE
            )
            toBeforeDatePattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        if (isCredit) {
            // Pattern for UPI credit: "on DD-MM-YY from SENDER_NAME. Avl"
            val fromAfterDatePattern = Regex(
                """on\s+\d{2}-\d{2}-\d{2}\s+from\s+([^.]+?)(?:\.\s*Avl|\.\s*Not|\.Not|\.$)""",
                RegexOption.IGNORE_CASE
            )
            fromAfterDatePattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }

            // Pattern for credit: "from SENDER_NAME on DD-MM-YY"
            val fromBeforeDatePattern = Regex(
                """from\s+([^.]+?)\s+on\s+\d{2}[-/]\d{2}[-/]\d{2,4}""",
                RegexOption.IGNORE_CASE
            )
            fromBeforeDatePattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Check for UPI pattern fallback only if no merchant could be extracted
        if (message.contains("via UPI", ignoreCase = true)) {
            return "UPI Transaction"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "Equitas-1234", "Equitas A/c 12XX", or "A/c 1234"
        val acPatterns = listOf(
            Regex("""Equitas-([X\d]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Equitas\s+)?A/c\s+([X\d]+)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in acPatterns) {
            pattern.find(message)?.let { match ->
                extractLast4Digits(match.groupValues[1])?.let { return it }
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Avl Bal is INR XX,XXX.XX", "Avl Bal is INR XXXXX", or "Bal INR XX,XXX.XX"
        val balancePattern = Regex(
            """(?:Avl\s+Bal\s+is|Bal)\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
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

    override fun extractReference(message: String): String? {
        // Pattern: "-Ref:57198707XXXX" or "Ref:XXXX"
        val refPattern = Regex(
            """-?Ref[:\s]*([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer")
        ) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "transferred", "received", "paid"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}
