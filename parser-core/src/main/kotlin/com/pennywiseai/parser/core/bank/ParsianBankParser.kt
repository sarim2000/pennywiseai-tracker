package com.pennywiseai.parser.core.bank

/**
 * Parsian Bank parser for Iranian banking SMS messages.
 * Handles Persian language transaction messages with amounts in Rials and Tomans.
 */
class ParsianBankParser : BaseIranianBankParser() {

    override fun getBankName() = "Parsian Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        val parsianSenders = setOf(
            "PARSIANBANK",
            "PARSIAN",
            "PARSIAN BANK",
            "PERSIANBANK",
            "PERSIAN"
        )

        return upperSender in parsianSenders
    }
}
