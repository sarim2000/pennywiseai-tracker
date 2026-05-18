package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Enpara (digital bank by QNB Finansbank / Enpara Bank, Turkey).
 *
 * Source: push notifications from the Enpara Android app (not SMS).
 * Sender alias supplied by BankNotificationConfig: "Enpara".
 * Currency: TRY (Turkish Lira; appears in notifications as "TL").
 *
 * Number formatting (Turkish locale):
 *  - `.` is the thousands separator, `,` is the decimal separator (e.g. "1.175,28 TL").
 *  - Strip `.` and replace `,` with `.` before constructing BigDecimal.
 *
 * Supported notification shapes:
 *
 *  1) Card spend (Encard) -> EXPENSE, isFromCard = true.
 *     "Vadesiz TL hesabınıza bağlı 2589 ile biten Encard'ınızla 10/05/2026 tarihinde
 *      105100000024364-OBILET ISTANBUL TR firmasında 520,00 TL tutarında harcama yapıldı."
 *     - accountLast4: 4 digits between "bağlı" and "ile biten Encard".
 *     - merchant: the segment after the leading numeric reference and before " firmasında",
 *       with the trailing " TR" stripped.
 *     - balanceAfter: not present in this notification shape.
 *
 *  2) Outgoing FAST transfer -> EXPENSE.
 *     "13/05/2026 tarihinde vadesiz TL hesabınızdan <RECIPIENT> adlı alıcıya 500,00 TL
 *      tutarında para transferi (FAST) yapıldı. İşlem sonrası hesap bakiyesi: 1.175,28 TL"
 *     - merchant: recipient name between "hesabınızdan" and "adlı alıcıya".
 *     - balance: number after "İşlem sonrası hesap bakiyesi:".
 *
 *  3) Incoming FAST transfer -> INCOME.
 *     "Vadesiz TL hesabınıza 11/05/2026 tarihinde <SENDER> tarafından yapılan para transferi
 *      (FAST) sonucunda 200,00 TL giriş oldu. İşlem sonrası hesap bakiyesi: 3.231,27 TL"
 *     - merchant: sender name between the date and "tarafından".
 *     - balance: number after "İşlem sonrası hesap bakiyesi:".
 */
class EnparaBankParser : BankParser() {

    override fun getBankName() = "Enpara"

    override fun getCurrency() = "TRY"

    override fun canHandle(sender: String): Boolean {
        return sender.equals("Enpara", ignoreCase = true)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Skip OTP / verification codes if they ever appear in notifications.
        if (lower.contains("otp") ||
            lower.contains("doğrulama kodu") ||
            lower.contains("tek kullanımlık şifre") ||
            lower.contains("şifreniz")
        ) {
            return false
        }

        // Must look like one of the three known transaction notification shapes.
        return lower.contains("harcama yapıldı") ||
                lower.contains("para transferi") ||
                lower.contains("giriş oldu")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Card spend: "... 520,00 TL tutarında harcama yapıldı."
        CARD_AMOUNT_REGEX.find(message)?.let {
            return parseTurkishNumber(it.groupValues[1])
        }

        // Outgoing transfer: "... 500,00 TL tutarında para transferi (FAST) yapıldı."
        OUTGOING_AMOUNT_REGEX.find(message)?.let {
            return parseTurkishNumber(it.groupValues[1])
        }

        // Incoming transfer: "... sonucunda 200,00 TL giriş oldu."
        INCOMING_AMOUNT_REGEX.find(message)?.let {
            return parseTurkishNumber(it.groupValues[1])
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("giriş oldu") -> TransactionType.INCOME
            lower.contains("harcama yapıldı") -> TransactionType.EXPENSE
            lower.contains("para transferi") && lower.contains("yapıldı") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Card spend: merchant lives between the leading numeric reference and " firmasında".
        CARD_MERCHANT_REGEX.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            val stripped = stripTrailingCountryCode(raw)
            val cleaned = cleanMerchantName(stripped)
            if (isValidMerchantName(cleaned)) return cleaned
        }

        // Outgoing transfer: recipient between "hesabınızdan" and "adlı alıcıya".
        OUTGOING_RECIPIENT_REGEX.find(message)?.let { match ->
            val cleaned = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(cleaned)) return cleaned
        }

        // Incoming transfer: sender between "tarihinde" and "tarafından".
        INCOMING_SENDER_REGEX.find(message)?.let { match ->
            val cleaned = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(cleaned)) return cleaned
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "bağlı 2589 ile biten Encard" — 4 digits between "bağlı" and "ile biten Encard".
        CARD_LAST4_REGEX.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "İşlem sonrası hesap bakiyesi: 1.175,28 TL"
        BALANCE_REGEX.find(message)?.let { match ->
            return parseTurkishNumber(match.groupValues[1])
        }
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        // Card spend notifications mention "Encard".
        return message.contains("Encard", ignoreCase = true)
    }

    /**
     * Removes a trailing standalone " TR" (country code) from a card-spend merchant
     * candidate. We intentionally leave the city token (e.g. "ISTANBUL", "KIRIKKALE")
     * intact since the issue allows it.
     */
    private fun stripTrailingCountryCode(raw: String): String {
        return raw.trimEnd().removeSuffix(" TR").trimEnd()
    }

    private fun parseTurkishNumber(raw: String): BigDecimal? {
        // Turkish format: "1.175,28" -> "1175.28"; "520,00" -> "520.00"
        val normalized = raw
            .replace(".", "")
            .replace(",", ".")
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }

    companion object {
        // Card spend amount: "<number> TL tutarında harcama yapıldı"
        private val CARD_AMOUNT_REGEX = Regex(
            """([0-9.,]+)\s*TL\s+tutarında\s+harcama\s+yapıldı""",
            RegexOption.IGNORE_CASE
        )

        // Outgoing transfer amount: "<number> TL tutarında para transferi"
        private val OUTGOING_AMOUNT_REGEX = Regex(
            """([0-9.,]+)\s*TL\s+tutarında\s+para\s+transferi""",
            RegexOption.IGNORE_CASE
        )

        // Incoming transfer amount: "sonucunda <number> TL giriş oldu"
        private val INCOMING_AMOUNT_REGEX = Regex(
            """sonucunda\s+([0-9.,]+)\s*TL\s+giriş\s+oldu""",
            RegexOption.IGNORE_CASE
        )

        // Card spend merchant: after "tarihinde", optional leading numeric reference
        // (e.g. "105100000024364-" or "2288088 -"), up to " firmasında".
        private val CARD_MERCHANT_REGEX = Regex(
            """tarihinde\s+\d+\s*-\s*(.+?)\s+firmasında""",
            RegexOption.IGNORE_CASE
        )

        // Outgoing recipient: between "hesabınızdan" and "adlı alıcıya".
        private val OUTGOING_RECIPIENT_REGEX = Regex(
            """hesabınızdan\s+(.+?)\s+adlı\s+alıcıya""",
            RegexOption.IGNORE_CASE
        )

        // Incoming sender: between "tarihinde" and "tarafından".
        private val INCOMING_SENDER_REGEX = Regex(
            """tarihinde\s+(.+?)\s+tarafından""",
            RegexOption.IGNORE_CASE
        )

        // Card last 4: digits between "bağlı" and "ile biten Encard".
        private val CARD_LAST4_REGEX = Regex(
            """bağlı\s+(\d{4})\s+ile\s+biten\s+Encard""",
            RegexOption.IGNORE_CASE
        )

        // Post-transaction balance: "İşlem sonrası hesap bakiyesi: <number> TL"
        private val BALANCE_REGEX = Regex(
            """İşlem\s+sonrası\s+hesap\s+bakiyesi:?\s*([0-9.,]+)\s*TL""",
            RegexOption.IGNORE_CASE
        )
    }
}
