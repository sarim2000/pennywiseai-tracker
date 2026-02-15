package com.pennywiseai.parser.core.bank

/**
 * CIMB Thai Bank parser for Thai banking SMS messages.
 */
class CIMBThaiParser : BaseThailandBankParser() {

    override fun getBankName() = "CIMB Thai"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "CIMB" ||
                upperSender.contains("CIMB THAI") ||
                upperSender.contains("CIMBTHAI")
    }
}
