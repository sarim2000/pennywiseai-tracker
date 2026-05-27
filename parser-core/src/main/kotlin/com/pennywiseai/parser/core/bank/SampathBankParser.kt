package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Sampath Bank (Sri Lanka) SMS messages.
 *
 * Handles both account transactions and credit-card transactions.
 *
 * Account formats (sender: SAMPATHTXN):
 * - "LKR 5,000.00 credited to AC **8758 for PICKME - 37419306"
 * - "LKR 4,025.00 debited from AC **8758 for WPY_TAOA_041772_WLT@0347310"
 * - "GBP 1,000.00 credited to AC **5012 for Remittance ID : [IR26GBP36623] : REALIZE"
 *
 * Credit-card format (sender: SAMPCCTXN):
 * - "Cr Crd no..**0282 Auth Pmt LKR 2,100.00 at SPICE ASIA - DELIVERY Avl Bal LKR 250,000.00 ..."
 *
 * Currency is a leading 3-letter code (LKR/GBP/USD), captured dynamically per message.
 */
class SampathBankParser : BankParser() {

    override fun getBankName() = "Sampath Bank"

    override fun getCurrency() = "LKR"  // Sri Lankan Rupee (default; foreign-currency variants override per-message)

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        // Containment covers bare senders and any operator/DLT prefix or suffix
        // (e.g. "AD-SAMPATHTXN", "SAMPATHTXN-S").
        return normalized.contains("SAMPATHTXN") || normalized.contains("SAMPCCTXN")
    }

    override fun detectIsCard(message: String): Boolean {
        // Sampath credit-card SMS use "Cr Crd no..**0282"; the base class only
        // recognises "card no."/"credit card", so detect the abbreviated form here.
        val lower = message.lowercase()
        if (lower.contains("crd no") || lower.contains("cr crd")) {
            return true
        }
        return super.detectIsCard(message)
    }

    /**
     * Extracts the currency code that precedes the transaction amount.
     * Falls back to the bank default if no code is present.
     */
    private fun extractMessageCurrency(message: String): String {
        // Match the FIRST "<CODE> <amount>" occurrence to capture the transaction currency
        // (balance amounts share the same code, so first match is fine).
        val pattern = Regex("""\b([A-Z]{3})\s+[0-9,]+\.\d{2}""")
        pattern.find(message)?.let { return it.groupValues[1].uppercase() }
        return getCurrency()
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Delegate to base parsing, then override the currency with the per-message code
        // (base parse hard-codes getCurrency()).
        val base = super.parse(smsBody, sender, timestamp) ?: return null
        val currency = extractMessageCurrency(smsBody)
        return if (currency == base.currency) base else base.copy(currency = currency)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Account: "LKR 5,000.00 credited", "GBP 1,000.00 credited"
        // Card: "Auth Pmt LKR 2,100.00 at ..."
        val patterns = listOf(
            Regex("""Auth\s+Pmt\s+[A-Z]{3}\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE),
            Regex("""[A-Z]{3}\s+([0-9,]+\.\d{2})\s+(?:credited|debited)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credited to") -> TransactionType.INCOME
            lower.contains("debited from") -> TransactionType.EXPENSE
            lower.contains("auth pmt") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Card: "at SPICE ASIA - DELIVERY Avl Bal LKR ..."
        val cardPattern = Regex("""\bat\s+(.+?)\s+Avl\s+Bal""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // International remittance: "for Remittance ID : [IR26GBP36623] : REALIZE"
        val remittancePattern = Regex(
            """for\s+Remittance\s+ID\s*:\s*\[[^\]]+\]\s*:\s*([^\n]+)""",
            RegexOption.IGNORE_CASE
        )
        remittancePattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Account: "for PICKME - 37419306" / "for WPY_TAOA_041772_WLT@0347310"
        // Capture everything after "for " up to newline, then strip a trailing " - <ref>".
        val forPattern = Regex("""\bfor\s+(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
        forPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Strip a trailing reference appended after " - "
            val dashRef = Regex("""\s+-\s+\S+$""")
            merchant = merchant.replace(dashRef, "").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Account: "AC **8758"  Card: "Cr Crd no..**0282"
        val pattern = Regex("""(?:AC|Crd\s+no\.*)\s*\*+\s*(\d{3,})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // International remittance: "Remittance ID : [IR26GBP36623]"
        val remittancePattern = Regex("""Remittance\s+ID\s*:\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE)
        remittancePattern.find(message)?.let { return it.groupValues[1].trim() }

        // Account: "for PICKME - 37419306" -> trailing numeric reference
        val dashRefPattern = Regex("""\bfor\s+.+?\s+-\s+(\d+)""", RegexOption.IGNORE_CASE)
        dashRefPattern.find(message)?.let { return it.groupValues[1].trim() }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Card: "Avl Bal LKR 250,000.00"
        val pattern = Regex("""Avl\s+Bal\s+[A-Z]{3}\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            val balStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("credited to") ||
            lower.contains("debited from") ||
            lower.contains("auth pmt")
        ) {
            return true
        }
        return super.isTransactionMessage(message)
    }
}
