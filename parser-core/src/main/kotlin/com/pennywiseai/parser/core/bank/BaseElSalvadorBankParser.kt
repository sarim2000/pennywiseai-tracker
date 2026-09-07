package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Shared base for El Salvador banks (Banco Cuscatlan, Banco Promerica, Banco Agricola).
 *
 * They all send Spanish SMS in USD (the country is dollarised) and share one skeleton:
 * `<verb> ... por USD<amount>` optionally followed by `en <merchant>`, or preceded by
 * `a <payee>` / `de <payer>`. Subclasses only add their sender match and their own
 * account/card number patterns.
 */
abstract class BaseElSalvadorBankParser : BankParser() {

    override fun getCurrency() = "USD"

    /** Bank-specific account/card patterns; group 1 is the last 4. Empty = SMS carries none. */
    protected open val accountPatterns: List<Regex> = emptyList()

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("codigo") || lower.contains("clave") || lower.contains("otp")) return false
        return extractTransactionType(message) != null
    }

    override fun extractAmount(message: String): BigDecimal? =
        AMOUNT.find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            // "Ha recibido un Credito/Abono ..." — money in, even when the body also says
            // "Tarjeta de Debito" or "Transferencia", so this is checked first.
            lower.contains("recibido") -> TransactionType.INCOME
            DEBIT_MARKERS.any { lower.contains(it) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        PURCHASE.find(message)?.let { return it.groupValues[1].trim().trimEnd('.', ',') }
        // Counterparty: the payer for money in ("de NAME por USD…"), the payee for money
        // out ("a NAME por USD…"). The prefix is greedy so the connector closest to the
        // amount wins ("Transferencia de Fondos a NAME por $ 26.00" -> NAME).
        val counterparty =
            if (extractTransactionType(message) == TransactionType.INCOME) FROM_PARTY else TO_PARTY
        return counterparty.find(message)?.groupValues?.get(1)?.trim()
    }

    override fun extractAccountLast4(message: String): String? =
        accountPatterns.firstNotNullOfOrNull { it.find(message)?.groupValues?.get(1) }

    override fun detectIsCard(message: String) = CARD.containsMatchIn(message)

    private companion object {
        // "por USD10.00", "por USD 5.00", "por $ 80.00" — US decimal format.
        val AMOUNT = Regex("""(?:USD|US\$|\$)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val DEBIT_MARKERS = listOf("debito", "consumo", "compra", "retiro", "aplicada", "pago")
        val PURCHASE = Regex(
            """por\s+(?:USD|US\$|\$)\s*[\d,.]+\s+en\s+(.+?)(?:\s+el\s+\d|\.\s|\.?$)""",
            RegexOption.IGNORE_CASE
        )
        val FROM_PARTY = Regex(""".*\bde\s+(.+?)\s+por\s+(?:USD|US\$|\$)""", RegexOption.IGNORE_CASE)
        val TO_PARTY = Regex(""".*\ba\s+(.+?)\s+por\s+(?:USD|US\$|\$)""", RegexOption.IGNORE_CASE)
        val CARD = Regex("""tarjeta|\bTTA\b""", RegexOption.IGNORE_CASE)
    }
}
