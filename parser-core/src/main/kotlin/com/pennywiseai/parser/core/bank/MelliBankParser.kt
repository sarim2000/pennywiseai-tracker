package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Bank Melli (Meli) Bank specific parser for Iranian banking SMS messages.
 * Handles Bank Melli's unique message formats including:
 * - Persian language transaction messages
 * - Amounts in Rials and Tomans
 * - Various transaction types (debit/credit/transfer)
 */
class MelliBankParser : BaseIranianBankParser() {

    override fun getBankName() = "Melli Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        // Common Melli Bank sender IDs
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