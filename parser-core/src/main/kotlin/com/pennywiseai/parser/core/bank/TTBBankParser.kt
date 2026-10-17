package com.pennywiseai.parser.core.bank

/**
 * TTB (TMBThanachart Bank) parser for Thai banking SMS messages.
 */
class TTBBankParser : BaseThailandBankParser() {

    override fun getBankName() = "TTB"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        // Not contains("TMB"): that also captured India's Tamilnad Mercantile Bank
        // ("VA-TMBANK-S") and dropped every one of its SMS (#917).
        return upperSender == "TTB" ||
                upperSender.contains("TMBTHANACHART") ||
                upperSender == "TMB"
    }
}
