package com.pennywiseai.parser.core.bank

/**
 * Krungsri (Bank of Ayudhya - BAY) parser for Thai banking SMS messages.
 */
class KrungsriBankParser : BaseThailandBankParser() {

    override fun getBankName() = "Krungsri"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "BAY" ||
                upperSender.contains("KRUNGSRI") ||
                upperSender.contains("AYUDHYA")
    }
}
