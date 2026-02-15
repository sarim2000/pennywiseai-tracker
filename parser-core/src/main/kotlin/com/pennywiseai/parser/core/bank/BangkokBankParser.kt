package com.pennywiseai.parser.core.bank

/**
 * Bangkok Bank (BBL) parser for Thai banking SMS messages.
 */
class BangkokBankParser : BaseThailandBankParser() {

    override fun getBankName() = "Bangkok Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "BBL" ||
                upperSender.contains("BANGKOK BANK") ||
                upperSender.contains("BANGKOKBANK")
    }
}
