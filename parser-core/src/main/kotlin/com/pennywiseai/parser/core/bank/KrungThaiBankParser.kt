package com.pennywiseai.parser.core.bank

/**
 * Krungthai Bank (KTB) parser for Thai banking SMS messages.
 */
class KrungThaiBankParser : BaseThailandBankParser() {

    override fun getBankName() = "Krungthai Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender == "KTB" ||
                upperSender.contains("KRUNGTHAI") ||
                upperSender.contains("KRUNG THAI")
    }
}
