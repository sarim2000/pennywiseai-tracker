package com.pennywiseai.parser.core.bank

/**
 * Banco Promerica (El Salvador) — sender "Promerica", Spanish, USD.
 *
 * Covers incoming Transfer365 credits ("Ha recibido un abono …"), outgoing transfers
 * ("Su Op. Transfer365 … ha sido aplicada") and card purchases ("tu TTA *1234 … compra").
 */
class BancoPromericaParser : BaseElSalvadorBankParser() {

    override fun getBankName() = "Banco Promerica"

    override fun canHandle(sender: String) = sender.uppercase().contains("PROMERICA")

    override val accountPatterns = listOf(
        // "tu TTA *1234:" — the card, the only number these messages carry.
        Regex("""TTA\s*\*?(\d{4})""", RegexOption.IGNORE_CASE)
    )
}
