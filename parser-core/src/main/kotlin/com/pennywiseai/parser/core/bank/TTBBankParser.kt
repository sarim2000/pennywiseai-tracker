package com.pennywiseai.parser.core.bank

/**
 * TTB (TMBThanachart Bank) parser for Thai banking SMS messages.
 */
class TTBBankParser : BaseThailandBankParser() {

    override fun getBankName() = "TTB"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "TTB" ||
                upperSender.contains("TMBTHANACHART") ||
                upperSender.contains("TMB")
    }
}
