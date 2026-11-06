package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Mobily Pay (Saudi Arabia) transaction SMS messages.
 *
 * Handles card purchases, wallet funding and explicitly directed transfers.
 */
class MobilyPayParser : BankParser() {

    override fun getBankName() = "Mobily Pay"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        // Normalize only display punctuation/whitespace, then require the whole sender
        // to match. This supports common display variants without matching unrelated SMS.
        val normalized = sender.trim().uppercase().replace(Regex("[\\s\\-_]"), "")
        return normalized == "MOBILYPAY"
    }

    override fun extractAmount(message: String): BigDecimal? =
        FinancialMessageFields.sarAmount(message, listOf("Amount"))

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsed = super.parse(smsBody, sender, timestamp) ?: return null
        if (parsed.type == TransactionType.TRANSFER && isWalletTopUp(smsBody.lowercase())) {
            // The funding instrument is separate from the wallet that owns the balance.
            return parsed.copy(accountLast4 = null, isFromCard = false, isMobileWallet = true)
        }
        return parsed
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            isWalletTopUp(lower) ->
                // Funding the user's own Mobily Pay wallet from their card is an internal movement.
                TransactionType.TRANSFER
            lower.contains("purchase reversal") || lower.contains("international purchase reversal") ||
                lower.contains("cashback reversal") || lower.contains("refund") || lower.contains("reversal") -> TransactionType.INCOME
            lower.contains("local transfer") -> when (FinancialMessageFields.transferDirection(message)) {
                FinancialMessageFields.TransferDirection.INCOMING -> TransactionType.INCOME
                FinancialMessageFields.TransferDirection.OUTGOING -> TransactionType.EXPENSE
                null -> when {
                    lower.contains("incoming") || lower.contains("received") -> TransactionType.INCOME
                    lower.contains("outgoing") || lower.contains("sent") -> TransactionType.EXPENSE
                    else -> null
                }
            }
            lower.contains("transfer received") -> TransactionType.INCOME
            lower.contains("transfer sent") -> TransactionType.EXPENSE
            // Adjustment alerts are financial only when their direction is explicit.
            // Unqualified adjustment/status wording remains unsupported.
            (lower.contains("credit adjustment") ||
                (lower.contains("adjustment") && lower.contains("credit"))) -> TransactionType.INCOME
            (lower.contains("debit adjustment") ||
                (lower.contains("adjustment") && lower.contains("debit"))) -> TransactionType.EXPENSE
            lower.contains("card purchase") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // A funding source labelled "From Card" is not a transfer counterparty.
        if (message.contains("Wallet Top", ignoreCase = true)) return "Mobily Pay Wallet"

        listOf("At", "From", "To").forEach { label ->
            FinancialMessageFields.labelledValue(message, listOf(label))?.let { rawValue ->
                val counterparty = cleanMerchantName(rawValue)
                if (isValidMerchantName(counterparty)) return counterparty
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        val cardNumberPattern = Regex(
            """Card\s+Number\s*:\s*(?:[A-Z]+\s*)?\*+(\d{4})\b""",
            RegexOption.IGNORE_CASE
        )
        cardNumberPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        val fromCardPattern = Regex(
            """From\s+Card\s*:\s*(\d{4})\*?[A-Z]*\b""",
            RegexOption.IGNORE_CASE
        )
        fromCardPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? =
        FinancialMessageFields.sarAmount(message, listOf("Current Balance"))

    override fun detectIsCard(message: String): Boolean {
        if (Regex("""(?:Card\s+Number|From\s+Card)\s*:""", RegexOption.IGNORE_CASE)
                .containsMatchIn(message)
        ) {
            return true
        }
        return super.detectIsCard(message)
    }

    private fun isWalletTopUp(lower: String): Boolean =
        lower.contains("mobily pay wallet top-up") || lower.contains("mobily pay wallet top up") ||
            lower.contains("wallet top-up") || lower.contains("wallet top up")

    override fun isTransactionMessage(message: String): Boolean {
        if (SaudiTransactionMessageGuards.isDeclinedOrFailed(message) ||
            SaudiTransactionMessageGuards.isPromotionalOrOperationalNotice(message)
        ) {
            return false
        }

        val lower = message.lowercase()

        // Security messages can include a purchase amount; reject them before any
        // transaction keyword is considered.
        if (FinancialMessageSafety.isSecurityCode(message)) return false

        return lower.contains("card purchase") ||
                lower.contains("wallet top-up") || lower.contains("wallet top up") ||
                lower.contains("refund") || lower.contains("reversal") ||
                lower.contains("local transfer") ||
                lower.contains("transfer received") || lower.contains("transfer sent") ||
                (lower.contains("adjustment") &&
                    (lower.contains("credit") || lower.contains("debit")))
    }

}
