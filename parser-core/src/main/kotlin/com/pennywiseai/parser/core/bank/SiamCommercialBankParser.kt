package com.pennywiseai.parser.core.bank

/**
 * Siam Commercial Bank (SCB) parser for Thai banking SMS messages.
 */
class SiamCommercialBankParser : BaseThailandBankParser() {

    override fun getBankName() = "Siam Commercial Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "SCB" ||
                upperSender.contains("SIAM COMMERCIAL") ||
                upperSender.contains("SIAMCOMMERCIAL")
    }
}
