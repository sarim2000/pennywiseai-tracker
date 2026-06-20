package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for NSDL Payments Bank (NSDLPB) SMS messages.
 *
 * NSDL Payments Bank was rebranded to Jio Payments Bank, but users still receive
 * legacy SMS from the NSDLPB sender in a distinct format. This parser handles that
 * legacy format only; the JIOPBS sender/format is handled by [JioPaymentsBankParser].
 */
class NSDLPaymentsBankParser : BaseIndianBankParser() {

    override fun getBankName() = "NSDL Payments Bank"

    override fun canHandle(sender: String): Boolean {
        // Sender ID is NSDLPB, optionally with a DLT prefix (e.g. AD-NSDLPB, VM-NSDLPB-S).
        return sender.uppercase().contains("NSDLPB")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Handles "Rs 1.00" (space) and "Rs.100.00" (no space).
        val amountPattern = Regex(
            """Rs\.?\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractAccountLast4(message: String): String? {
        // Patterns: "A/c XX1234" (debit) and "A/c no XX1234" (credit).
        val accountPattern = Regex(
            """A/c(?:\s+no)?\s+([X\d]+)""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }

        return super.extractAccountLast4(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Debit: "for linked myupihandle@oksbi. UPI Ref ..." — capture the whole VPA up to
        // the sentence boundary (". " / end), then keep the handle before "@". Capturing
        // up to the first dot would truncate dotted handles like business.name@oksbi.
        val linkedPattern = Regex(
            """for\s+linked\s+(.+?)(?:\.\s|$)""",
            RegexOption.IGNORE_CASE
        )
        linkedPattern.find(message)?.let { match ->
            var name = match.groupValues[1].trim()
            if (name.contains("@")) name = name.substringBefore("@")
            name = name.trimEnd('.', ',', ';')
            val merchant = cleanMerchantName(name)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Credit SMS has no VPA/merchant — leave null.
        return null
    }

    override fun extractReference(message: String): String? {
        // Debit: "UPI Ref 122345526539"
        // Credit: "(UPI Ref No 617109835321)"
        val refPattern = Regex(
            """UPI\s+Ref(?:\s+No)?\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("credited") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("upi ref") &&
            (lowerMessage.contains("debited") || lowerMessage.contains("credited"))
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}
