package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Google Pay (GPay) peer-to-peer transaction notifications.
 */
class GPayParser : BankParser() {

    override fun getBankName() = "Google Pay"

    override fun canHandle(sender: String): Boolean {
        return sender.equals("GPay", ignoreCase = true) || 
               sender.equals("com.google.android.apps.nbu.paisa.user", ignoreCase = true)
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Explicit peer-to-peer incoming UPI notification: "[Sender] paid you [Amount]"
        // e.g. "DAD paid you ₹1.00" or "DAD paid you Rs. 10.00" or "DAD paid you 1.00"
        val pattern = Regex("""^(.+?)\s+paid\s+you\s+(?:Rs\.?|₹|inr)?\s*([0-9.,]+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(smsBody) ?: return null

        val merchant = match.groupValues[1].trim()
        val amountStr = match.groupValues[2].replace(",", "")
        val amount = try {
            BigDecimal(amountStr)
        } catch (e: Exception) {
            return null
        }

        return ParsedTransaction(
            amount = amount,
            type = TransactionType.INCOME, // map as INCOMING/CREDIT
            merchant = merchant,
            reference = null,
            accountLast4 = null,
            balance = null,
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = "Google Pay",
            isFromCard = false,
            currency = "INR"
        )
    }

    override fun extractAmount(message: String): BigDecimal? {
        val pattern = Regex("""paid\s+you\s+(?:Rs\.?|₹|inr)?\s*([0-9.,]+)""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        if (message.contains("paid you", ignoreCase = true)) {
            return TransactionType.INCOME
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val pattern = Regex("""^(.+?)\s+paid\s+you""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }
        return "Google Pay User"
    }

    override fun isTransactionMessage(message: String): Boolean {
        return message.contains("paid you", ignoreCase = true)
    }
}
