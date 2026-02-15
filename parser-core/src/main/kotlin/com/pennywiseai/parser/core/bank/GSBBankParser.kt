package com.pennywiseai.parser.core.bank

/**
 * Government Savings Bank (GSB) parser for Thai banking SMS messages.
 */
class GSBBankParser : BaseThailandBankParser() {

    override fun getBankName() = "Government Savings Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "GSB" ||
                upperSender.contains("GOVERNMENT SAVINGS") ||
                upperSender.contains("GOVT SAVINGS")
    }
}
