package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for BSF - Banque Saudi Fransi (Saudi Arabia) SMS messages.
 *
 * Handles Arabic transfer formats. The two known layouts use different field
 * wording:
 *
 * Outgoing (debit):
 *   عملية حوالة مالية صادرة مقبولة        (outgoing money transfer, accepted)
 *   خصمت من حساب ****0136                 (debited from account ****0136)
 *   إلى <RECIPIENT>                       (to <recipient>)
 *   بنك ...                               (beneficiary bank)
 *   آيبان ****8287                        (IBAN)
 *   القيمة SAR 505.00                     (amount SAR 505.00)
 *   الرسوم SAR 0.58                       (fee SAR 0.58 — NOT the transaction amount)
 *   في28-07-2026 14:33:53                (on 28-07-2026 14:33:53)
 *
 * Incoming (credit):
 *   حوالة واردة                          (incoming transfer)
 *   مبلغ: 359.0 SAR                      (amount: 359.0 SAR)
 *   لـ:SA**********R1******8             (to: masked IBAN)
 *   من: <SENDER>                         (from: sender)
 *   آيبان: ****4318                       (IBAN)
 *   في: 04-08-2026 15:51                (on: 04-08-2026 15:51)
 *
 * Sender: BSF
 */
class BSFBankParser : BankParser() {

    override fun getBankName() = "Banque Saudi Fransi"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("[\\s\\-_]"), "")
        if (normalized == "BSF" || normalized.contains("BSFR")) return true
        // DLT-style headers e.g. "AD-BSF-S", "VK-BSF-T"
        if (Regex("""(?:^|[^A-Z])BSF(?:[^A-Z]|$)""").containsMatchIn(sender.uppercase())) return true
        // Arabic name for Banque Saudi Fransi (Al Bank Al Saudi Al Fransi)
        if (sender.contains("الفرنسي")) return true
        return false
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Amount is either labelled القيمة (value) or مبلغ (amount). Never الرسوم (fee).
        val amountLabel = """(?:القيمة|مبلغ)"""

        // "القيمة SAR 505.00" / "مبلغ: SAR 505.00" (currency first)
        Regex(
            """$amountLabel\s*:?\s*SAR\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        ).find(message)?.let { return parseSarAmount(it.groupValues[1]) }

        // "مبلغ: 359.0 SAR" / "القيمة 359.0 SAR" (currency last)
        Regex(
            """$amountLabel\s*:?\s*([0-9,]+(?:\.\d{1,2})?)\s*SAR""",
            RegexOption.IGNORE_CASE
        ).find(message)?.let { return parseSarAmount(it.groupValues[1]) }

        return null
    }

    private fun parseSarAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        return when {
            message.contains("واردة") -> TransactionType.INCOME   // incoming transfer
            message.contains("صادرة") -> TransactionType.EXPENSE  // outgoing transfer
            message.contains("خصم") -> TransactionType.EXPENSE    // debited
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val isIncoming = message.contains("واردة")
        val isOutgoing = message.contains("صادرة")

        // Outgoing transfer: recipient follows "إلى" (to).
        if (isOutgoing) {
            Regex("""إلى\s*:?\s*([^\n]+?)(?:\n|$)""").find(message)?.let { match ->
                cleanCounterparty(match.groupValues[1])?.let { return it }
            }
        }

        // Incoming transfer: sender follows "من:" (from).
        if (isIncoming) {
            Regex("""من\s*:?\s*([^\n]+?)(?:\n|$)""").find(message)?.let { match ->
                cleanCounterparty(match.groupValues[1])?.let { return it }
            }
        }

        return null
    }

    /**
     * Cleans a captured counterparty: trims, strips trailing masking chars,
     * rejects purely masked / numeric values (e.g. account or IBAN fragments).
     */
    private fun cleanCounterparty(raw: String): String? {
        var value = raw.trim().trimEnd('*', '×', ' ', '\t')
        if (value.isBlank()) return null
        if (value.all { it == '*' || it == '×' || it.isDigit() || it.isWhitespace() }) return null
        val cleaned = cleanMerchantName(value)
        return if (isValidMerchantName(cleaned)) cleaned else null
    }

    override fun extractAccountLast4(message: String): String? {
        // "خصمت من حساب ****0136" — own account with last 4.
        Regex("""حساب\s*\*+\s*(\d{3,4})""").find(message)?.let {
            return extractLast4Digits(it.groupValues[1])
        }
        // No plain account present (incoming layout only carries IBANs) — return
        // null rather than risk picking digits out of a masked IBAN.
        return null
    }

    override fun extractReference(message: String): String? {
        // Beneficiary IBAN: "آيبان ****8287" / "آيبان: ****4318" (masked).
        Regex("""آيبان\s*:?\s*([\*A-Z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(message)?.let {
                val value = it.groupValues[1].trim()
                if (value.isNotBlank()) return value
            }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (message.contains("رمز") || message.contains("OTP", ignoreCase = true) ||
            message.contains("كلمة المرور")
        ) {
            return false
        }
        val keywords = listOf(
            "حوالة",   // transfer
            "واردة",   // incoming
            "صادرة",   // outgoing
            "خصم",     // debit
            "SAR"
        )
        return keywords.any { message.contains(it) }
    }
}
