package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Kerala Bank (The Kerala State Co-operative Bank, India) SMS messages.
 *
 * Handles formats like:
 * - "Dear Customer Your A/c no XXXX0024 is credited with 15000.00 on 06-06-2026 by Loan Recovery
 *    From : 139451061. Balance is -579822.00 - Kerala Bank"
 *
 * Notes:
 * - Amounts are printed without a currency symbol (e.g. "credited with 15000.00").
 * - Balances can be NEGATIVE for loan accounts (e.g. "Balance is -579822.00").
 *
 * Common senders: VM-KELBNK-S
 * Currency: INR (Indian Rupee)
 *
 * Distinct from Kerala Gramin Bank (KGBANK / KERALAGR) — gates only on the KELBNK token.
 */
class KeralaBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Kerala Bank"

    override fun getCurrency() = "INR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("KELBNK")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "credited with 15000.00" / "debited with 15000.00" — no currency symbol.
        val pattern = Regex(
            """(?:credited|debited)\s+with\s+([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base patterns (Rs/INR forms) for safety.
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("is credited") -> TransactionType.INCOME
            lowerMessage.contains("is debited") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // "by Loan Recovery From : 139451061" -> "Loan Recovery"
        // Stop at the "From :" clause, a ".", the trailing " - Kerala Bank"
        // signature, or end — so a message lacking both "From :" and a period
        // doesn't over-capture the bank-name suffix into the merchant.
        val byPattern = Regex(
            """\bby\s+(.+?)(?:\s+From\s*:|\s*-\s*Kerala\s+Bank|\.|$)""",
            RegexOption.IGNORE_CASE
        )
        byPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Balance is -579822.00" — supports an optional leading minus sign.
        val pattern = Regex(
            """Balance\s+is\s+(-?[0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }
}
