package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parsian Bank specific parser for Iranian banking SMS messages.
 * Handles Parsian Bank's unique message formats including:
 * - Persian language transaction messages
 * - Amounts in Rials and Tomans
 * - Various transaction types (debit/credit/transfer)
 */
class ParsianBankParser : BaseIranianBankParser() {

    override fun getBankName() = "Parsian Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()

        // Common Parsian Bank sender IDs
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