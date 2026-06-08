package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Emirates Islamic Bank (UAE) transactions.
 * Inherits from UAEBankParser for AED / multi-currency support.
 *
 * Sender is "EI SMS" for all messages. Handles debit/credit card purchases,
 * ATM withdrawals, telegraphic transfers, online banking transfers, credit
 * card payments and salary deposits.
 *
 * Sample formats (masked):
 *  - Debit/Credit Card Purchase ... Card Ending: 1234 ... At: <merchant> ... Amount: AED 12.34
 *  - Telegraphic Transfer Deducted/Received ... Account: 123XXX12XXX12 ... Amount: AED 12.00
 *  - Payment towards Credit Card ... From Account: 12345XXXXX123 ... Amount: AED 1,123.12
 *  - ATM Withdrawal ... Debit Card Ending: 1234 ... Amount: AED 123.00
 *  - Salary Deposited Account: 123XXX12XXX12 ... Amount: AED 123,123.12
 */
class EmiratesIslamicParser : UAEBankParser() {

    override fun getBankName() = "Emirates Islamic"

    override fun canHandle(sender: String): Boolean {
        // Sender is "EI SMS"; normalize by uppercasing and stripping spaces.
        // Match the exact "EISMS" token and the full bank name, but never a bare
        // two-letter "EI" substring (far too broad; would steal other banks' SMS).
        val normalizedSender = sender.uppercase().replace(Regex("\\s+"), "")
        return normalizedSender == "EISMS" ||
                normalizedSender == "EMIRATESISLAMIC" ||
                normalizedSender == "EMIRATESISLAMICBANK"
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP / verification messages.
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Credit Card payment receipt (sample #5) is a confirmation/duplicate of the
        // "Payment towards Credit Card" debit (sample #3). We classify it as a
        // NON-transaction and return null here to avoid double-counting the payment.
        if (lowerMessage.contains("confirm receipt of your payment")) {
            return false
        }

        // Emirates Islamic transaction indicators.
        val transactionKeywords = listOf(
            "debit card purchase",
            "credit card purchase",
            "payment towards credit card",
            "telegraphic transfer",
            "atm withdrawal",
            "online banking transfer",
            "salary deposited",
            "deducted",
            "received",
            "deposited"
        )
        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Income: incoming telegraphic transfers and salary deposits.
            lowerMessage.contains("telegraphic transfer received") -> TransactionType.INCOME
            lowerMessage.contains("salary deposited") -> TransactionType.INCOME

            // Expenses: card purchases (per issue, credit card purchases are EXPENSE too),
            // ATM withdrawals, outgoing transfers and credit-card payments.
            lowerMessage.contains("credit card purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("debit card purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("atm withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("payment towards credit card") -> TransactionType.EXPENSE
            lowerMessage.contains("telegraphic transfer deducted") -> TransactionType.EXPENSE
            lowerMessage.contains("online banking transfer") -> TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Card purchases use "At: <merchant>" on its own line.
        val atPattern = Regex("""At:\s*(.+?)(?:\r?\n|$)""", RegexOption.IGNORE_CASE)
        atPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        // ATM withdrawals use "From: <location>" on its own line.
        if (message.contains("ATM Withdrawal", ignoreCase = true)) {
            val fromPattern = Regex("""From:\s*(.+?)(?:\r?\n|$)""", RegexOption.IGNORE_CASE)
            fromPattern.find(message)?.let { match ->
                val location = match.groupValues[1].trim()
                if (location.isNotEmpty()) {
                    return "ATM Withdrawal: ${cleanMerchantName(location)}"
                }
            }
            return "ATM Withdrawal"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Prefer the card number when present: "Card Ending: 1234" / "Debit Card Ending: 1234".
        val cardEndingPattern = Regex("""Card Ending:\s*(\d{3,})""", RegexOption.IGNORE_CASE)
        cardEndingPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }

        // Otherwise use the trailing digits of the masked account number, e.g.
        // "Account: 123XXX12XXX12" or "From Account: 12345XXXXX123".
        val accountPattern = Regex("""Account:\s*([X\d]+)""", RegexOption.IGNORE_CASE)
        accountPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Available Balance: AED 12,123.12" and credit-card "Available Limit: AED 123,123.12".
        val balancePattern = Regex(
            """Available\s+(?:Balance|Limit):\s*([A-Z]{3})\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[2].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }
}
