package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Chartered Bank Nepal SMS messages.
 * Contains generic fallback logic for NPR transactions from typical SCB Nepal senders.
 *
 * Currency: NPR (Nepalese Rupee)
 */
class StandardCharteredNepalParser : BankParser() {

    override fun getBankName() = "Standard Chartered Nepal"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        // Assuming typical SCB patterns for Nepal. 
        // We include specific names and also standard names where they might cross over,
        // relying on NPR currency checks later.
        return upperSender.contains("SCBNL") || 
               upperSender.contains("SCB_NP") ||
               upperSender.contains("SCBNP")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val nprPattern = Regex(
            """NPR\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        nprPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        return super.extractAmount(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("npr") && super.isTransactionMessage(message)
    }
}
