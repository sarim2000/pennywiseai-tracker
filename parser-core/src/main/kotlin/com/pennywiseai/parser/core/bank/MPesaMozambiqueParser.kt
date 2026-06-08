package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for M-Pesa Mozambique (Vodacom) mobile money SMS messages.
 *
 * Language: Portuguese. Currency: MZN (written "MT" in messages).
 * Number format: US style (comma thousands, dot decimal e.g. 12,345.67). Dates D/M/YY.
 *
 * Handles formats like (names/numbers below are masked example values):
 * - "Confirmado DF50KDFDHWK. Transferiste 1,234.56MT e a taxa foi de 1.23MT para 258841234567 - JOHNDOE ..."
 * - "Confirmado DF36KCPECLC. Registamos uma operacao de compra no valor de 1,234.56MT ... na entidade EDM ..."
 * - "Confirmado DF30KCJDIIA. Aos ... levantaste 1,234.56MT no agente 425300 - BENJAMIM FERAGE ..."
 * - "Confirmado DEV6KB6GAUI. Depositaste o valor de 12,345.67MT no agente JOHN DOE ..."
 * - "Confirmado DET0KAIXP5E. Recebeste 12,345.67MT de 123456 - SIMO ..."
 *
 * Gating: Mozambique messages start with "Confirmado" (Portuguese), whereas Kenya/Tanzania
 * use "Confirmed" (English). parse()/isTransactionMessage are gated on "Confirmado" AND the
 * presence of "MT", so Kenyan/Tanzanian SMS fall through to their parsers under the
 * content-aware dispatch in BankParserFactory.
 *
 * Country: Mozambique
 */
class MPesaMozambiqueParser : BankParser() {

    override fun getBankName() = "M-Pesa Mozambique"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        // M-Pesa Mozambique uses the same sender ID; differentiation is by content.
        return normalizedSender.contains("MPESA") ||
                normalizedSender.contains("M-PESA")
    }

    /**
     * Only parse Mozambique M-Pesa messages: Portuguese "Confirmado" + "MT" currency token.
     * Returning null lets Kenya/Tanzania parsers handle their own SMS under the dispatch.
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (!isMozambiqueMessage(smsBody)) {
            return null
        }
        return super.parse(smsBody, sender, timestamp)
    }

    private fun isMozambiqueMessage(message: String): Boolean {
        return message.contains("Confirmado", ignoreCase = true) &&
                message.contains("MT", ignoreCase = false)
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val amountStr = raw.replace(",", "")
        return try {
            BigDecimal(amountStr)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Purchase: "operacao de compra no valor de 1,234.56MT"
        // Deposit: "Depositaste o valor de 12,345.67MT"
        val valorPattern = Regex(
            """no\s+valor\s+de\s+([0-9,]+(?:\.[0-9]{2})?)\s*MT""",
            RegexOption.IGNORE_CASE
        )
        valorPattern.find(message)?.let { return parseAmount(it.groupValues[1]) }

        val oValorPattern = Regex(
            """o\s+valor\s+de\s+([0-9,]+(?:\.[0-9]{2})?)\s*MT""",
            RegexOption.IGNORE_CASE
        )
        oValorPattern.find(message)?.let { return parseAmount(it.groupValues[1]) }

        // Verb-led amounts: "Transferiste X MT", "levantaste X MT", "Recebeste X MT"
        val verbPattern = Regex(
            """(?:Transferiste|levantaste|Recebeste)\s+([0-9,]+(?:\.[0-9]{2})?)\s*MT""",
            RegexOption.IGNORE_CASE
        )
        verbPattern.find(message)?.let { return parseAmount(it.groupValues[1]) }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("transferiste") -> TransactionType.EXPENSE
            lower.contains("operacao de compra") -> TransactionType.EXPENSE
            lower.contains("levantaste") -> TransactionType.EXPENSE
            lower.contains("depositaste") -> TransactionType.INCOME
            lower.contains("recebeste") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Transfer: "para 258841234567 - JOHNDOE aos ..."
        val paraPattern = Regex(
            """para\s+\S+\s+-\s+(.+?)\s+aos\b""",
            RegexOption.IGNORE_CASE
        )
        paraPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Received: "de 123456 - SIMO aos ..."
        val dePattern = Regex(
            """\bde\s+\S+\s+-\s+(.+?)\s+aos\b""",
            RegexOption.IGNORE_CASE
        )
        dePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Withdrawal: "no agente 425300 - BENJAMIM FERAGE." (number + name)
        val agenteNumNamePattern = Regex(
            """no\s+agente\s+\S+\s+-\s+(.+?)(?:\.|\s+O\s+novo\s+saldo|\s+aos\b)""",
            RegexOption.IGNORE_CASE
        )
        agenteNumNamePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Deposit: "no agente JOHN DOE aos ..." (name only, no leading number/dash)
        val agenteNamePattern = Regex(
            """no\s+agente\s+(.+?)\s+aos\b""",
            RegexOption.IGNORE_CASE
        )
        agenteNamePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Purchase: "na entidade EDM com referencia ..."
        val entidadePattern = Regex(
            """na\s+entidade\s+(.+?)(?:\s+com\s+referencia|\s+aos\b)""",
            RegexOption.IGNORE_CASE
        )
        entidadePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "novo saldo M-Pesa e de X MT" — tolerate extra/irregular spacing around words.
        val balancePattern = Regex(
            """novo\s+saldo\s+M-Pesa\s+e\s+de\s+([0-9,]+(?:\.[0-9]{2})?)\s*MT""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { return parseAmount(it.groupValues[1]) }
        return null
    }

    override fun extractReference(message: String): String? {
        // Reference is the code after "Confirmado " (e.g. DF50KDFDHWK).
        val refPattern = Regex(
            """Confirmado\s+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { return it.groupValues[1] }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (!isMozambiqueMessage(message)) {
            return false
        }
        val lower = message.lowercase()
        val keywords = listOf(
            "transferiste",
            "operacao de compra",
            "levantaste",
            "depositaste",
            "recebeste"
        )
        return keywords.any { lower.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")  // Remove trailing parentheses
            .replace(Regex("""\.\s*$"""), "")            // Remove trailing period
            .replace(Regex("""\s*-\s*$"""), "")          // Remove trailing dash
            .trim()
    }
}
