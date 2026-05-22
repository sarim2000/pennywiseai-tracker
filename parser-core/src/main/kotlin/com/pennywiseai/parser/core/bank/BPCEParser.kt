package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class BPCEParser : BankParser() {

    override fun getBankName() = "BPCE"

    override fun getCurrency() = "EUR"

    override fun canHandle(sender: String): Boolean {
        return sender == "38015"
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("ajout d'un bénéficiaire")) {
            return false
        }

        return lowerMessage.contains("virement instantané") ||
                super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""de\s+([0-9,]+(?:\.\d{2})?)\s+EUR""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.\d{2})?)\s*EUR""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", ".")
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
            lowerMessage.contains("virement instantané") && lowerMessage.contains("reçu") -> TransactionType.INCOME
            lowerMessage.contains("virement instantané") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val patterns = listOf(
            Regex("""vers\s+([^.\n]+?)(?:\s+du\s+|\s+le\s+|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        return super.extractAccountLast4(message)
    }
}
