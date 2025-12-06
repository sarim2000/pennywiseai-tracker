package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Huntington Bank SMS messages (USA)
 *
 * Supported formats:
 * - Debit card: "Huntington Heads Up. We processed a debit card withdrawal: $25.00 at Bob Inc. Acct CK0000 has a $10.12 bal (10/19/25 5:43 AM ET)."
 * - ATM: "Huntington Heads Up. We processed an ATM withdrawal: $162.45 at POS John Inc. Acct CK0000 has a $20.20 bal (9/03/25 12:12 PM ET)."
 * - ACH: "Huntington Heads Up. We processed an ACH withdrawal: $50.67 at GEICO           . Acct CK0000 has a $6211.32 bal (8/09/25 3:23 PM ET)."
 *
 * Common senders: Huntington Bank, HUNTINGTON
 */
class HuntingtonBankParser : BankParser() {

    override fun getBankName() = "Huntington Bank"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("HUNTINGTON") ||
                upperSender == "HUNTINGTON BANK" ||
                upperSender.matches(Regex("""^[A-Z]{2}-HUNTINGTON-[A-Z]$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern: "withdrawal: $25.00 at" or "withdrawal: $162.45 at"
        val withdrawalPattern = Regex(
            """withdrawal:\s+\$([0-9,]+(?:\.\d{2})?)\s+at""",
            RegexOption.IGNORE_CASE
        )
        withdrawalPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Huntington uses "withdrawal" for all expense transactions
        return when {
            lowerMessage.contains("withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("debit card withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("atm withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("ach withdrawal") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern: "at Bob Inc. Acct" or "at BC *UBER CASH. Acct" or "at POS John Inc. Acct"
        // Note: Merchant name may end with a period, so we match up to ". Acct" (period + space + Acct)
        val merchantPattern = Regex(
            """at\s+(.+?)\.\s+Acct""",
            RegexOption.IGNORE_CASE
        )
        merchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Fall back to base class patterns
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Acct CK0000" - extract the last 4 digits from the account number
        val accountPattern = Regex(
            """Acct\s+CK(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Generic pattern for account ending
        val endingPattern = Regex(
            """account\s+ending\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        endingPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "has a $10.12 bal" or "has a -$15.01 bal"
        val balancePattern = Regex(
            """has\s+a\s+(-?\$[0-9,]+(?:\.\d{2})?)\s+bal""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace("$", "").replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip non-transaction messages
        if (lowerMessage.contains("heads up") && !lowerMessage.contains("withdrawal")) {
            return false
        }

        // Huntington specific transaction keywords
        val huntingtonTransactionKeywords = listOf(
            "we processed a debit card withdrawal",
            "we processed an atm withdrawal",
            "we processed an ach withdrawal"
        )

        if (huntingtonTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Huntington sends specific alerts for different transaction types
        return when {
            lowerMessage.contains("debit card withdrawal") -> true
            lowerMessage.contains("atm withdrawal") -> true  // ATM transactions are card-based
            lowerMessage.contains("ach withdrawal") -> false // ACH transactions are not card-based
            else -> super.detectIsCard(message)
        }
    }
}
