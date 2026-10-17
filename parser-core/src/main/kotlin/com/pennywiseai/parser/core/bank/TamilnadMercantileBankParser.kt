package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Tamilnad Mercantile Bank (India, INR) — #917.
 *
 * UPI debit:
 * "Your A/c No.XXXX1111 is debited with Rs.487.21 on 09-10-2026 06:19 PM and UTIB A/c
 *  linked to testshop.offline@axl is credited (UPI Ref No.628200000001).
 *  Current AVBL bal is Rs.1234.73 - TMB"
 *
 * The message also says the PAYEE's account "is credited", so the direction comes
 * from "is debited" on the user's own account.
 *
 * Senders: VA-/VD-…TMBANK-S. Thailand's TTB (formerly TMB Bank) used to claim any
 * sender containing "TMB", so this parser is registered ahead of it.
 */
class TamilnadMercantileBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Tamilnad Mercantile Bank"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "").contains("TMBANK")

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            Regex("""your\s+a/c\s+no\.?\s*\S+\s+is\s+debited""").containsMatchIn(lower) -> TransactionType.EXPENSE
            Regex("""your\s+a/c\s+no\.?\s*\S+\s+is\s+credited""").containsMatchIn(lower) -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractAmount(message: String): BigDecimal? =
        Regex("""is\s+(?:debited|credited)\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractAmount(message)

    override fun extractMerchant(message: String, sender: String): String? {
        // "… A/c linked to testshop.offline@axl is credited" — the payee's VPA; show its handle.
        Regex("""linked\s+to\s+([^\s@]+)@\S+""", RegexOption.IGNORE_CASE).find(message)?.let { m ->
            val merchant = cleanMerchantName(m.groupValues[1].trim('*', ' '))
            if (isValidMerchantName(merchant)) return merchant
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? =
        Regex("""Your\s+A/c\s+No\.?\s*[X*]*(\d{4})\b""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)
            ?: super.extractAccountLast4(message)

    // "UPI Ref No.628200000001" — no space after "No.", which the shared pattern misreads.
    override fun extractReference(message: String): String? =
        Regex("""UPI\s+Ref\s+No\.?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)
            ?: super.extractReference(message)

    // "Current AVBL bal is Rs.1234.73"
    override fun extractBalance(message: String): BigDecimal? =
        Regex("""AVBL\s+bal\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractBalance(message)
}
