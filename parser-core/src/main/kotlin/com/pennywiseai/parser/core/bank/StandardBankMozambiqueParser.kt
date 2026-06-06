package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Bank Mozambique SMS messages (Portuguese).
 *
 * Amounts use European decimal formatting (thousands separator '.', decimal ',')
 * e.g. "1.234,56". Currency can be MZN (also written "MT") or USD.
 *
 * Supported formats:
 * - Credit (income):
 *   "Caro Cliente, ocorreu um credito de 1.234,56 MZN na sua conta 9999999999999 a 22/05/2026, 14:54, disponivel em 22/05/2026. Mais info: ..."
 * - Purchase (expense):
 *   "Caro Cliente, ocorreu uma operacao de compra de 1.234,56, MT na sua conta 9999999999999 a 09/05/2026, 13:41, MM INVESTMENTS S. Comissao: 0.00MT e Imposto de selo: 0.00MT. Mais info: ..."
 * - Debit (expense):
 *   "Caro Cliente, ocorreu um debito de 1.234,56 USD na sua conta 9999999999999 a 15/05/2026, 13:30, C01 AGENCIA DA BEIRA. Comissao: 0.00USD e Imposto de selo: 0.00USD. Mais info: ..."
 *
 * Portuguese keys:
 * - "credito"            = credit  (INCOME)
 * - "debito"             = debit   (EXPENSE)
 * - "operacao de compra" = purchase (EXPENSE)
 * - "na sua conta <digits>" = account number
 *
 * Sender patterns: "STDBank" (case-insensitive) and short code "7832265".
 */
class StandardBankMozambiqueParser : BankParser() {

    override fun getBankName() = "Standard Bank Mozambique"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("STDBANK") ||
                normalizedSender == "7832265"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (!isTransactionMessage(smsBody)) {
            return null
        }

        val amount = extractAmount(smsBody) ?: return null
        val type = extractTransactionType(smsBody) ?: return null
        val currency = extractCurrency(smsBody) ?: getCurrency()

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            isFromCard = detectIsCard(smsBody),
            currency = currency
        )
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("credito") ||
                lower.contains("debito") ||
                lower.contains("operacao de compra")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "credito de 1.234,56 MZN", "compra de 1.234,56, MT", "debito de 1.234,56 USD"
        val pattern = Regex(
            """de\s+([0-9.]+,[0-9]{2})""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return parseEuropeanAmount(match.groupValues[1])
        }
        return null
    }

    /**
     * Converts a European-formatted amount string (e.g. "1.234,56") to BigDecimal.
     */
    private fun parseEuropeanAmount(raw: String): BigDecimal? {
        val normalized = raw.replace(".", "").replace(",", ".")
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractCurrency(message: String): String? {
        // Currency follows the amount: "1.234,56 MZN", "1.234,56, MT", "1.234,56 USD".
        // Anchor to a standalone token and accept only known codes so a Portuguese
        // word (e.g. "na") is never mistaken for a currency when the code is absent;
        // callers fall back to getCurrency() (MZN) when this returns null.
        val pattern = Regex(
            """de\s+[0-9.]+,[0-9]{2},?\s+([A-Za-z]{2,3})(?=\s|,|${'$'})""",
            RegexOption.IGNORE_CASE
        )
        val token = pattern.find(message)?.groupValues?.get(1)?.uppercase() ?: return null
        return when (token) {
            "MT", "MZN" -> "MZN"
            "USD" -> "USD"
            else -> null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credito") -> TransactionType.INCOME
            lower.contains("debito") -> TransactionType.EXPENSE
            lower.contains("operacao de compra") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractAccountLast4(message: String): String? {
        // "na sua conta 9999999999999"
        val pattern = Regex(
            """na\s+sua\s+conta\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return null
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Merchant/location appears after the time stamp "HH:MM, " and before the
        // next ". Comissao" (purchase/debit) or ". " segment.
        // Examples:
        //   "..., 13:41, MM INVESTMENTS S. Comissao: ..."
        //   "..., 13:30, C01 AGENCIA DA BEIRA. Comissao: ..."
        val pattern = Regex(
            """\d{2}:\d{2},\s+([^.]+?)\.\s*Comissao""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? = null

    override fun extractReference(message: String): String? = null
}
