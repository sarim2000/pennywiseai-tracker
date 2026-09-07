package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction

/**
 * Banco Agricola (El Salvador) — senders "Agricola" and "Transfer365", Spanish, USD.
 *
 * Messages are prefixed "B.AGRICOLA-TRANSFER365:" and carry no account number.
 */
class BancoAgricolaParser : BaseElSalvadorBankParser() {

    override fun getBankName() = "Banco Agricola"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s.contains("AGRICOLA") || s.contains("TRANSFER365")
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Transfer365 is El Salvador's shared instant-payment rail — other banks send from
        // that sender too, so only claim it when the body actually names Banco Agricola.
        // Content-aware dispatch in BankParserFactory then lets another parser try it.
        if (!sender.uppercase().contains("AGRICOLA") && !smsBody.uppercase().contains("AGRICOLA")) {
            return null
        }
        return super.parse(smsBody, sender, timestamp)
    }
}
