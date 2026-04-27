package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Machchhapuchre Bank (Nepal) SMS messages
 *
 * Handles formats like:
 * - "Dear ARUN,NPR 3,190.00 Withdrawn from your A/C ###0018 on 29/03/2026 Remarks: medicine,K Available Bal: 20532.09. For app: http://bit.ly/3QZrCFj"
 * - "Dear ARUN,NPR 50,000.00 Deposited in your A/C ###0018 on 26/02/2026 Remarks: Salary (Ma Available Bal: 55652.24. For app: http://bit.ly/3QZrCFj"
 *
 * Common sender: MBL_ALERT
 * Currency: NPR (Nepalese Rupee)
 */
class MachchhapuchreBankParser : BankParser() {

    override fun getBankName() = "Machchhapuchre Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender == "MBL_ALERT" ||
                normalizedSender.contains("MACHHAPUCHHRE") ||
                normalizedSender.contains("MBL")
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
        
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("withdrawn from") || lowerMessage.contains("debited")) {
            return TransactionType.EXPENSE
        }

        if (lowerMessage.contains("deposited in") || lowerMessage.contains("credited")) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val remarksPattern = Regex(
            """Remarks:\s*(.*?)\s*Available Bal:""",
            RegexOption.IGNORE_CASE
        )
        
        remarksPattern.find(message)?.let { match ->
            val remarks = match.groupValues[1].trim()
            if (remarks.isNotBlank()) {
                return cleanMerchantName(remarks)
            }
        }
        
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        val accountPattern = Regex(
            """A/C\s+#+([X#*\d]+)\s+on""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val avBalPattern = Regex(
            """Available Bal:\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        avBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val hasAmount = lowerMessage.contains("npr")
        val hasTransactionKeyword = lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("deposited")

        return hasAmount && hasTransactionKeyword && lowerMessage.contains("remarks:") && lowerMessage.contains("available bal:")
    }
}
