package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Everest Bank (Nepal) - handles NPR currency transactions
 */
class EverestBankParser : BankParser() {

    override fun getBankName() = "Everest Bank"

    override fun getCurrency() = "NPR"  // Nepalese Rupee

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return when {
            // Numeric senders (phone numbers, short codes)
            sender.matches(Regex("""^\d{7,10}$""")) -> true

            // Text-based senders
            upperSender == "EVEREST" ||
                    upperSender.contains("EVERESTBANK") ||
                    upperSender == "UJJ SH" ||
                    upperSender == "CWRD" ||  // ATM withdrawal code

                    // DLT patterns for Nepal
                    upperSender.matches(Regex("""^[A-Z]{2}-EVEREST-[A-Z]$""")) -> true

            else -> false
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Everest Bank patterns: "NPR 520.00", "NPR 15,000.00"
        val patterns = listOf(
            Regex("""NPR\s+([0-9,]+(?:\.[0-9]{2})?)\s""", RegexOption.IGNORE_CASE),
            Regex("""NPR\s+([0-9,]+(?:\.[0-9]{2})?)(?:\s|$)""", RegexOption.IGNORE_CASE),
            Regex(
                """(?:debited|credited)\s+by\s+NPR\s+([0-9,]+(?:\.[0-9]{2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Debit transactions are expenses
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            lowerMessage.contains("debited by") -> TransactionType.EXPENSE

            // Credit transactions are income
            lowerMessage.contains("is credited") -> TransactionType.INCOME
            lowerMessage.contains("credited by") -> TransactionType.INCOME

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "For: 9843368/{Payment type},{Receiver}"
        // Pattern: "For: {Receiver}/{Payment type},UJJ SH"
        // Pattern: "For: CWDR/521708008016/202508050854" (ATM)

        val forPattern = Regex("""For:\s*([^.]+?)(?:\.\s|$)""", RegexOption.IGNORE_CASE)
        forPattern.find(message)?.let { match ->
            val forContent = match.groupValues[1].trim()

            // Handle different patterns
            return when {
                // ATM withdrawal pattern: "CWDR/521708008016/202508050854"
                forContent.startsWith("CWDR/", ignoreCase = true) -> {
                    "ATM Withdrawal"
                }

                // Transfer pattern: "9843368/{Payment type},{Receiver}" or "{Receiver}/{Payment type},UJJ SH"
                forContent.contains("/") && forContent.contains(",") -> {
                    val parts = forContent.split(",")
                    if (parts.size >= 2) {
                        val beforeComma = parts[0].trim()
                        val afterComma = parts[1].trim()

                        // If before comma contains slash, take the part after slash
                        if (beforeComma.contains("/")) {
                            val slashParts = beforeComma.split("/")
                            if (slashParts.size >= 2) {
                                val paymentType = slashParts[1].trim()
                                if (paymentType.isNotEmpty() && !paymentType.matches(Regex("""\d+"""))) {
                                    return cleanMerchantName(paymentType)
                                }
                            }
                        }

                        // Otherwise use the part after comma if it's not just a code
                        if (afterComma.isNotEmpty() && afterComma != "UJJ SH") {
                            return cleanMerchantName(afterComma)
                        }
                    }

                    // Fallback to first meaningful part
                    val allParts = forContent.replace(",", "/").split("/")
                    for (part in allParts) {
                        val cleanPart = part.trim()
                        if (cleanPart.isNotEmpty() &&
                            !cleanPart.matches(Regex("""\d+""")) &&
                            cleanPart != "UJJ SH"
                        ) {
                            return cleanMerchantName(cleanPart)
                        }
                    }
                    null
                }

                // Simple pattern without slashes/commas
                else -> {
                    if (forContent.isNotEmpty()) {
                        cleanMerchantName(forContent)
                    } else {
                        null
                    }
                }
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Your A/c {Account}" - but {Account} is a placeholder
        // Since the actual account number is masked in the examples,
        // we'll look for any account-like patterns

        val accountPattern = Regex("""A/c\s+([^\s]+)""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            val account = match.groupValues[1].trim()
            // If it's not a placeholder, extract last 4 digits
            if (account != "{Account}" && account.length >= 4) {
                return account.takeLast(4)
            }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // Look for reference numbers in "For:" section
        // Pattern: "For: CWDR/521708008016/202508050854" - extract the reference numbers
        val forPattern = Regex("""For:\s*([^.]+?)(?:\.\s|$)""", RegexOption.IGNORE_CASE)
        forPattern.find(message)?.let { match ->
            val forContent = match.groupValues[1].trim()

            // For ATM withdrawals, extract the reference numbers
            if (forContent.contains("CWDR/")) {
                val parts = forContent.split("/")
                if (parts.size >= 3) {
                    // Return the transaction reference (middle part) and timestamp
                    return "${parts[1]}/${parts[2]}"
                }
            }

            // For transfers, extract any numeric references
            val refPattern = Regex("""(\d{6,})""")
            refPattern.find(forContent)?.let { refMatch ->
                return refMatch.groupValues[1]
            }
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Everest Bank specific transaction keywords
        val everestTransactionKeywords = listOf(
            "dear customer",
            "your a/c",
            "is debited",
            "is credited",
            "debited by",
            "credited by",
            "for:",
            "never share password",
            "npr"
        )

        if (everestTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}