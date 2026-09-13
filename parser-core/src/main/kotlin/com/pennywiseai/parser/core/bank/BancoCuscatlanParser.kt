package com.pennywiseai.parser.core.bank

/**
 * Banco Cuscatlan (El Salvador) — sender "B.CUSCATLAN", Spanish, USD.
 *
 * Covers credits ("Ha recibido un Credito en su Cuenta"), card purchases
 * ("Alerta de consumo con Tarjeta de Debito"), outgoing Transfer365 transfers
 * ("Su Op. Transfer365 … ha sido aplicada") and debits/withdrawals ("Se hizo un Debito").
 */
class BancoCuscatlanParser : BaseElSalvadorBankParser() {

    override fun getBankName() = "Banco Cuscatlan"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase().replace(".", "").replace(" ", "")
        return s == "BCUSCATLAN" || s == "CUSCATLAN" || s == "BANCOCUSCATLAN"
    }

    override val accountPatterns = listOf(
        // "en su Cuenta B. CUSCATLAN 1234"
        Regex("""CUSCATLAN\s+(\d{4})""", RegexOption.IGNORE_CASE),
        // "Su Op. Transfer365 CORRIENTE XXXXXX1234"
        Regex("""(?:CORRIENTE|AHORROS?)\s+X*(\d{4})""", RegexOption.IGNORE_CASE)
    )
}
