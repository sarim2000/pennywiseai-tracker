package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Faysal Bank (Pakistan) app notifications and SMS.
 *
 * Handles formats like:
 * "PKR 55.000.00 sent to RECIPIENT A/C *9901 via IBFT from FBL A/C *4647 on 06-FEB-2026 02:22 PM Ref # 960855."
 */
class FaysalBankParser : BankParser() {

    override fun getBankName() = "Faysal Bank"

    override fun getCurrency() = "PKR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(" ", "")
        return normalized.contains("FAYSAL") ||
                normalized.contains("FBL") ||
                normalized.contains("AVANZA.AMBITWIZFBL") ||
                normalized == "8756"
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val hasCurrency = lowerMessage.contains("pkr")
        val hasTransferKeyword = lowerMessage.contains("sent to") ||
                lowerMessage.contains("transfer") ||
                lowerMessage.contains("ibft") ||
                lowerMessage.contains("received") ||
                lowerMessage.contains("debit card purchase") ||
                lowerMessage.contains("atm cash withdrawal")
        return hasCurrency && hasTransferKeyword
    }

    override fun extractAmount(message: String): BigDecimal? {
        val amountPattern = Regex("""PKR\s*([0-9.,]+)""", RegexOption.IGNORE_CASE)
        amountPattern.find(message)?.let { match ->
            val rawAmount = match.groupValues[1].replace(",", "")
            val normalizedAmount = if (rawAmount.count { it == '.' } > 1) {
                val lastDot = rawAmount.lastIndexOf('.')
                val wholePart = rawAmount.substring(0, lastDot).replace(".", "")
                val fractionalPart = rawAmount.substring(lastDot + 1)
                "$wholePart.$fractionalPart"
            } else {
                rawAmount
            }
            return try {
                BigDecimal(normalizedAmount)
            } catch (_: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("debit card purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("atm cash withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("sent to") -> TransactionType.TRANSFER
            lowerMessage.contains("received") || lowerMessage.contains("credited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val cardPattern = Regex("""debit card purchase at\s+(.+?)\s+from""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return cleanMerchantName(
                match.groupValues[1]
                    .replace("*", "")
                    .replace(",", "")
                    .trim()
            )
        }

        val receivedFromPattern = Regex(
            """received\s+(?:pkr\s+[0-9.,]+\s+)?(?:via\s+\w+\s+)?from\s+([A-Za-z\s.]+?)\s+(?:A/C|IBAN)""",
            RegexOption.IGNORE_CASE
        )
        receivedFromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        val beneficiaryPattern = Regex("""sent to\s+([A-Za-z.\s]+?)\s+A/C""", RegexOption.IGNORE_CASE)
        beneficiaryPattern.find(message)?.let { match ->
            return match.groupValues[1].trim().replace("\\s+".toRegex(), " ")
        }

        if (message.lowercase().contains("atm cash withdrawal")) {
            return "ATM Cash Withdrawal"
        }

        if (message.lowercase().contains("received from")) {
            val fallbackPattern = Regex("""received from\s+([A-Za-z\s.]+)""", RegexOption.IGNORE_CASE)
            fallbackPattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) return merchant
            }
        }

        return "IBFT Transfer"
    }

    override fun extractAccountLast4(message: String): String? {
        val patterns = listOf(
            Regex("""FBL\s+A/C\s*[*#Xx]+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""A/c\s*#?\s*[*#Xx]+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""A/C\s*[*#Xx]+(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val matches = pattern.findAll(message).toList()
            if (matches.isNotEmpty()) {
                return matches.last().groupValues[1]
            }
        }

        return null
    }

    override fun extractReference(message: String): String? {
        val referencePattern = Regex("""Ref\s*#?:?\s*([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
        referencePattern.find(message)?.let { match ->
            return match.groupValues[1]
        }
        return super.extractReference(message)
    }

    override fun detectIsCard(message: String): Boolean {
        if (message.lowercase().contains("debit card purchase")) {
            return true
        }
        return super.detectIsCard(message)
    }
}
