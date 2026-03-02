package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * HDFC Mutual Fund parser for SIP purchase and redemption messages.
 * Handles senders like "AD-HDFCMF-AC", "VM-HDFCMF".
 */
class HDFCMutualFundParser : BaseIndianBankParser() {

    override fun getBankName() = "HDFC Mutual Fund"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("HDFCMF")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val keywords = listOf(
            "sip purchase",
            "has been processed",
            "folio",
            "nav",
            "redemption"
        )
        return keywords.any { lowerMessage.contains(it) }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val pattern = Regex("""Rs\.?\s*([\d,]+\.?\d*)""")
        pattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val pattern = Regex("""under\s+(.+?)\s+for""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("sip purchase") || lowerMessage.contains("purchase") -> TransactionType.INVESTMENT
            lowerMessage.contains("redemption") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? = null

    override fun extractAccountLast4(message: String): String? = null
}
