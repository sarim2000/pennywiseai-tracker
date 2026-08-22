package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for STC Bank (Saudi Arabia).
 *
 * Handles English purchase / transfer formats such as:
 *   **0001 Purchase
 *   Via:0001
 *   Amount: 3 SAR
 *   From: SYNTHETIC MERCHANT
 *   At: 01/01/30 00:00
 *   STC Bank
 *
 * Sender examples: STC Bank, STCBank, STC-Bank, STC
 */
class STCBankParser : BankParser() {

    override fun getBankName() = "STC Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("[\\s\\-_]"), "")
        return normalized.contains("STCBANK") || normalized == "STC" || normalized == "STCPAY"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (isGenericStcSender(sender) && isClearlyTelecomOnlyMessage(smsBody)) return null
        return super.parse(smsBody, sender, timestamp)
    }

    override fun extractAmount(message: String): BigDecimal? {
        FinancialMessageFields.sarAmount(message, listOf("Amount"))?.let { return it }

        RCS_PURCHASE_AMOUNT.find(message)?.let { return parseAmount(it.groupValues[1]) }
        INLINE_AMOUNT.find(message)?.let { return parseAmount(it.groupValues[1]) }

        // "Amount: 3 SAR" or "Amount:3 SAR" or "Amount: 3.50 SAR"
        val amountPattern = Regex(
            """\bAmount\s*:?\s*([0-9,]+(?:\.\d{1,2})?)\s*(?:SAR|SR)\b""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        // "SAR 3.00" fallback
        val sarFirstPattern = Regex(
            """\b(?:SAR|SR)\s+([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        sarFirstPattern.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("adding money to account") || lower.contains("wallet top") ||
                (lower.contains("apple pay") && lower.contains("funding")) -> TransactionType.TRANSFER
            lower.contains("refund") || lower.contains("reversal") || lower.contains("reverse transaction") -> TransactionType.INCOME
            lower.contains("internal transfer") -> when (FinancialMessageFields.transferDirection(message)) {
                FinancialMessageFields.TransferDirection.OUTGOING -> TransactionType.EXPENSE
                FinancialMessageFields.TransferDirection.INCOMING -> TransactionType.INCOME
                null -> TransactionType.TRANSFER
            }
            lower.contains("sarie") &&
                (lower.contains("outward") || lower.contains("outgoing")) -> TransactionType.EXPENSE
            lower.contains("purchase") -> TransactionType.EXPENSE
            lower.contains("withdrawal") || lower.contains("withdraw") -> TransactionType.EXPENSE
            lower.contains("payment") -> TransactionType.EXPENSE
            lower.contains("debit") -> TransactionType.EXPENSE
            lower.contains("transfer out") || lower.contains("sent to") -> TransactionType.EXPENSE
            lower.contains("deposit") -> TransactionType.INCOME
            lower.contains("credit") && !lower.contains("credit card") -> TransactionType.INCOME
            lower.contains("received") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // "From: MERCHANT NAME" — merchant for Purchase, sender for incoming
        val fromPattern = Regex(
            """From\s*:\s*([^\n]+?)(?:\n|At\s*:|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // "To: RECIPIENT NAME" — recipient for outgoing transfers
        val toPattern = Regex(
            """To\s*:\s*([^\n]+?)(?:\n|At\s*:|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "**0001 Purchase" / "*0001 Purchase"
        val starPattern = Regex("""\*+(\d{4})\b""")
        starPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        // "Via:0001" / "Via: 0001"
        val viaPattern = Regex("""Via\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE)
        viaPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        return super.extractAccountLast4(message)
    }

    override fun detectIsCard(message: String): Boolean {
        // Presence of masked card (**XXXX) or Via:XXXX indicates card transaction
        if (Regex("""\*+\d{4}""").containsMatchIn(message)) return true
        if (Regex("""Via\s*:\s*\d{4}""", RegexOption.IGNORE_CASE).containsMatchIn(message)) return true
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        if (SaudiTransactionMessageGuards.isDeclinedOrFailed(message) ||
            SaudiTransactionMessageGuards.isPromotionalOrOperationalNotice(message) ||
            FinancialMessageSafety.isSecurityCode(message)
        ) return false

        val keywords = listOf(
            "purchase",
            "amount",
            "withdraw",
            "transfer",
            "payment",
            "refund",
            "deposit",
            "debit",
            "credit",
            "sar", "sr"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun parseAmount(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(",", ""))
    } catch (_: NumberFormatException) {
        null
    }

    private fun isGenericStcSender(sender: String): Boolean =
        sender.uppercase().replace(Regex("[\\s\\-_]"), "") == "STC"

    private fun isClearlyTelecomOnlyMessage(message: String): Boolean {
        val lower = message.lowercase()
        val sawa = lower.contains("sawa")
        val telecomContext = lower.contains("sawa balance") ||
            lower.contains("mobile balance") || lower.contains("telecom balance") ||
            lower.contains("recharge") || lower.contains("mobile service") ||
            lower.contains("data package") || lower.contains("service credit")
        return (lower.contains("vat refund") && (sawa || telecomContext)) ||
            (sawa && telecomContext)
    }

    private companion object {
        private val INLINE_AMOUNT = Regex(
            """\bAmount\s*:?\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)\b""",
            RegexOption.IGNORE_CASE
        )
        private val RCS_PURCHASE_AMOUNT = Regex(
            """(?:online\s+)?purchase\s+transaction\s+amount\s+([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
