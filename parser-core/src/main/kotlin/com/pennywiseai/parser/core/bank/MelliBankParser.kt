package com.pennywiseai.parser.core.bank

/**
 * Bank Melli parser for Iranian banking SMS messages.
 * Handles Persian language transaction messages with amounts in Rials and Tomans.
 */
class MelliBankParser : BaseIranianBankParser() {

    override fun getBankName() = "Melli Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        val melliSenders = setOf(
            "+98700717",
            "MELLI",
            "MELLIBANK",
            "MELLI BANK",
            "BANK MELLI",
            "BANKMELLI"
        )

        return upperSender in melliSenders
    }
}
