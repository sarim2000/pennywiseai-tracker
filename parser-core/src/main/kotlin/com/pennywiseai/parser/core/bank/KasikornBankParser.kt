package com.pennywiseai.parser.core.bank

/**
 * Kasikorn Bank (KBank) parser for Thai banking SMS messages.
 */
class KasikornBankParser : BaseThailandBankParser() {

    override fun getBankName() = "Kasikorn Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "KBANK" ||
                upperSender.contains("KASIKORN") ||
                upperSender.contains("KASIKORNBANK")
    }
}
