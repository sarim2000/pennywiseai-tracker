package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Citizens Bank (Nepal) SMS messages
 *
 * Handles formats like:
 * - "Dear ARUN, ###4041 is debited by NPR 5,000.00 on 29/03/2026, Remarks: ATM/459521x2018/CTZW Av Bal: 140270.04. Support Center:01-5970068"
 * - "Dear ARUN, ###4041 is credited by NPR 150,000.00 on 25/03/2026, Remarks: cIPS/NP2603250066636 Av Bal: 153520.04. Support Center:01-5970068"
 *
 * Common sender: CTZN_Alert
 * Currency: NPR (Nepalese Rupee)
 */
class CitizensBankParser : BankParser() {

    override fun getBankName() = "Citizens Bank"

    override fun getCurrency() = "NPR"

    override fun canHandle(sender: String): Boolean {
    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender == "CTZN_ALERT" ||
                normalizedSender == "CITIZENS" ||
                normalizedSender == "CTZN"
    }

    override fun extractAmount(message: String): BigDecimal? {
        val nprPattern = Regex(
            """(?:debited|credited)\s+by\s+NPR\s+([0-9,]+(?:\.\d{2})?)""",
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

        if (lowerMessage.contains("is debited") || lowerMessage.contains("debited by")) {
            return TransactionType.EXPENSE
        }

        if (lowerMessage.contains("is credited") || lowerMessage.contains("credited by")) {
            return TransactionType.INCOME
        }

        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // More robust: Remarks is until "Av Bal:"
        val remarksUntilAvBalPattern = Regex(
            """Remarks:\s*(.*?)\s*Av Bal:""",
            RegexOption.IGNORE_CASE
        )
        
        remarksUntilAvBalPattern.find(message)?.let { match ->
            val remarks = match.groupValues[1].trim()
            
            return when {
                remarks.startsWith("ATM/", ignoreCase = true) -> "ATM Withdrawal"
                remarks.startsWith("cIPS/", ignoreCase = true) -> "connectIPS"
                remarks.contains("/") -> remarks.split("/").first().trim()
                else -> cleanMerchantName(remarks)
            }
        }
        
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // "###4041 is debited"
        val accountPattern = Regex(
            """#+([X*\d]+)\s+is\s+(?:debited|credited)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val avBalPattern = Regex(
            """Av Bal:\s*([0-9,]+(?:\.\d{2})?)""",
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
    
    override fun extractReference(message: String): String? {
        val remarksPattern = Regex(
            """Remarks:\s*(.*?)\s*Av Bal:""",
            RegexOption.IGNORE_CASE
        )
        
        remarksPattern.find(message)?.let { match ->
            val remarks = match.groupValues[1].trim()
    override fun isTransactionMessage(message: String): Boolean {
        if (!super.isTransactionMessage(message)) return false
        val lowerMessage = message.lowercase()

        val hasAmount = lowerMessage.contains("npr")
        val hasTransactionKeyword = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited")

        return hasAmount && hasTransactionKeyword && lowerMessage.contains("remarks:") && lowerMessage.contains("av bal:")
    }
    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val hasAmount = lowerMessage.contains("npr")
        val hasTransactionKeyword = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited")

        return hasAmount && hasTransactionKeyword && lowerMessage.contains("remarks:") && lowerMessage.contains("av bal:")
    }
}
