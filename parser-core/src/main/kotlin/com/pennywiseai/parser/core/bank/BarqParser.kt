package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Barq (Saudi Arabia) transaction messages.
 *
 * Sender variants are intentionally narrow: the primary sender is barqapp and
 * the observed Android-delivery variant is barqapp-AD. Transactions are SAR
 * denominated; foreign card purchases include the settled SAR amount in
 * parentheses, which is preferred over the foreign authorization amount.
 */
class BarqParser : BankParser() {

    override fun getBankName() = "Barq"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("[\\s_-]"), "")
        return normalized == "BARQAPP" || normalized == "BARQAPPAD"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // The helper accepts only Barq's explicit Amount field and prefers the
        // parenthesized SAR settlement over a foreign authorization amount.
        FinancialMessageFields.sarSettlementOrAmount(message, listOf("Amount"))?.let { return it }

        // Older Barq purchase alerts can place a standalone SAR amount directly
        // below the purchase title. This remains purchase-title-gated and line
        // anchored so a later Balance or Fees value cannot become the amount.
        if (isHistoricalPurchase(message)) {
            HISTORICAL_DIRECT_SAR_AMOUNT.find(message)?.let { match ->
                return try {
                    BigDecimal(match.groupValues[1].replace(",", ""))
                } catch (_: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsed = super.parse(smsBody, sender, timestamp) ?: return null
        // Barq has one wallet balance: the card spends from it and top-ups fund it.
        // Whenever the message carries that balance, route it to the single wallet
        // account so funding and spending never split it across a card and a wallet.
        // Funding instruments and counterparty A/Cs are never the user's account either.
        val walletOwned = parsed.balance != null ||
            (parsed.type == TransactionType.TRANSFER && isWalletFunding(smsBody.lowercase())) ||
            COUNTERPARTY_ACCOUNT_FIELD.containsMatchIn(smsBody)
        return if (walletOwned) {
            parsed.copy(accountLast4 = null, isFromCard = false, isMobileWallet = true)
        } else {
            parsed
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // Classify by the title line only: field values such as "At: Refund Services"
        // must not turn a purchase into a refund.
        val lower = titleLine(message)
        return when {
            // Return wording must take precedence because reversal titles often
            // repeat the original purchase or transfer description.
            lower.contains("refund") || lower.contains("reversal") || lower.contains("reverse transaction") -> TransactionType.INCOME

            // Funding the user's own Barq wallet is an internal movement.
            isWalletFunding(lower) -> TransactionType.TRANSFER

            lower.contains("credit transfer internal") || lower.contains("incoming local transfer") -> TransactionType.INCOME
            lower.contains("outgoing local transfer") || lower.contains("debit transfer local") -> TransactionType.EXPENSE

            // Transfers to another Barq user are outgoing payments, rather than
            // own-account movements. Infer direction only from labelled structure.
            lower.contains("barq wallet transfer") -> when (FinancialMessageFields.transferDirection(message)) {
                FinancialMessageFields.TransferDirection.OUTGOING -> TransactionType.EXPENSE
                FinancialMessageFields.TransferDirection.INCOMING -> TransactionType.INCOME
                null -> null
            }

            lower.contains("online purchase") || lower.contains("pos purchase") ||
                lower.contains("atm withdrawal") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = titleLine(message)
        return when {
            lower.contains("credit transfer internal") || lower.contains("incoming local transfer") ||
                (lower.contains("barq wallet transfer") &&
                    FinancialMessageFields.transferDirection(message) == FinancialMessageFields.TransferDirection.INCOMING) ->
                extractFieldMerchant(message, "From")

            lower.contains("barq wallet transfer") || lower.contains("outgoing local transfer") ||
                lower.contains("debit transfer local") -> extractFieldMerchant(message, "To")

            lower.contains("online purchase") || lower.contains("pos purchase") ||
                lower.contains("refund") || lower.contains("reversal") || lower.contains("reverse transaction") ||
                lower.contains("atm withdrawal") ->
                extractFieldMerchant(message, "At")
                    ?: historicalMerchant(message)

            else -> null
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // Prefer the card ID for purchases/refunds over any separately listed A/C.
        CARD_MASK.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        STANDALONE_CARD_MASK.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        // Internal-credit source account and foreign-purchase account fallback.
        ACCOUNT_MASK.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? =
        FinancialMessageFields.sarAmount(message, listOf("Balance"))
            ?: FinancialMessageFields.labelledValue(message, listOf("Balance"))?.replace(",", "")?.toBigDecimalOrNull()

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("visa card") ||
            lower.contains("online purchase") || lower.contains("pos purchase") ||
            super.detectIsCard(message)
    }

    private fun titleLine(message: String): String =
        message.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().lowercase()

    // Older purchase alerts carry no Amount label: a standalone SAR line under the title,
    // then Balance, then the merchant. Modern layouts never use this fallback, and a
    // following "Label: value" field is never taken as the merchant.
    private fun historicalMerchant(message: String): String? {
        if (FinancialMessageFields.labelledValue(message, listOf("Amount")) != null) return null
        if (!HISTORICAL_DIRECT_SAR_AMOUNT.containsMatchIn(message)) return null
        val raw = HISTORICAL_MERCHANT_AFTER_BALANCE.find(message)?.groupValues?.get(1)?.trim() ?: return null
        if (LABELLED_FIELD_LINE.containsMatchIn(raw)) return null
        return cleanMerchantName(raw).takeIf { isValidMerchantName(it) }
    }

    private fun isHistoricalPurchase(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("online purchase") || lower.contains("online purchases") ||
            lower.contains("pos purchase") || lower.contains("pos purchases")
    }

    private fun isWalletFunding(lower: String): Boolean =
        lower.contains("money added to your barq wallet") ||
            lower.contains("barq wallet top-up") || lower.contains("barq wallet top up")

    override fun isTransactionMessage(message: String): Boolean {
        if (SaudiTransactionMessageGuards.isDeclinedOrFailed(message) ||
            SaudiTransactionMessageGuards.isPromotionalOrOperationalNotice(message)
        ) {
            return false
        }

        val lower = message.lowercase()
        if (FinancialMessageSafety.isSecurityCode(message)) return false

        // Limit and Apple Pay notices are operational messages, not transactions.
        val transactionTitles = listOf(
            "credit transfer internal",
            "incoming local transfer",
            "outgoing local transfer",
            "debit transfer local",
            "barq wallet transfer",
            "money added to your barq wallet",
            "barq wallet top-up",
            "barq wallet top up",
            "online purchase",
            "pos purchase",
            "atm withdrawal",
            "refund",
            "reversal",
            "reverse transaction"
        )
        return transactionTitles.any { lower.contains(it) }
    }

    private fun extractFieldMerchant(message: String, label: String): String? =
        FinancialMessageFields.labelledValue(message, listOf(label))?.let { raw ->
            cleanMerchantName(raw).takeIf { isValidMerchantName(it) }
        }

    private companion object {
        private val HISTORICAL_DIRECT_SAR_AMOUNT = Regex(
            """(?im)^\s*(?:SAR|SR)\s*:?[ \t]*([0-9][0-9,]*(?:\.\d{1,2})?)\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val HISTORICAL_MERCHANT_AFTER_BALANCE = Regex(
            """(?m)^Balance\s*:?[^\n]*\n([^\n]+)""",
            RegexOption.IGNORE_CASE
        )
        private val LABELLED_FIELD_LINE = Regex("""^[A-Za-z][A-Za-z /]{0,24}:""")
        private val CARD_MASK = Regex("""(?:VISA\s+)?card\s*:?\s*\*+(\d{4})\b""", RegexOption.IGNORE_CASE)
        private val STANDALONE_CARD_MASK = Regex("""(?m)^\*+(\d{4})\b""")
        private val ACCOUNT_MASK = Regex("""(?:Sender\s+A/C|A/C)\s*:?\s*\*+(\d{4})\b""", RegexOption.IGNORE_CASE)
        private val COUNTERPARTY_ACCOUNT_FIELD = Regex(
            """(?:Sender\s+A/C|To\s*:[^\n]*\n(?:Bank\s*:[^\n]*\n)?\s*A/C)\s*:?\s*\*+\d{4}\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
