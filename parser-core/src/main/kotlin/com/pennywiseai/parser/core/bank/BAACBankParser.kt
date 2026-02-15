package com.pennywiseai.parser.core.bank

/**
 * Bank for Agriculture and Agricultural Cooperatives (BAAC) parser for Thai banking SMS messages.
 */
class BAACBankParser : BaseThailandBankParser() {

    override fun getBankName() = "BAAC"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "BAAC" ||
                upperSender.contains("AGRICULTURE")
    }
}
