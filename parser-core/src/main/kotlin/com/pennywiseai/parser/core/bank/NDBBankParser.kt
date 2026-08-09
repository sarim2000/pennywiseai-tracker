package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for National Development Bank (NDB, Sri Lanka) SMS messages.
 *
 * Currency is LKR. Sender: "NDB ALERT".
 *
 * Supported formats:
 * - POS debit (EXPENSE):
 *   "LKR 12,724.00 debited from AC XXXXXXXX1234 as POS TXN on 30 Jul 2026 22:28
 *    at MERCHANT NAME CITY. Avl Bal 2,582.08 Call 94112448888 for info"
 * - CEFTS outward transfer (EXPENSE):
 *   "LKR 50,000.00 debited from AC XXXXXXXX1234 on 02 Jul 2026 15:52
 *    as CEFTS Outward Transfer. Avl Bal 1,527,72.83 Call 94112448888 for info"
 * - CEFTS inward transfer (INCOME):
 *   "LKR 60,076.25 credited to AC XXXXXXXX1234 on 02 Jul 2026 21:53
 *    as CEFTS Inward Transfer. Avl Bal 1,789,349.08 Call 94112448888 for info"
 *
 * CEFTS transfers are classified as EXPENSE/INCOME (counterparty unknown),
 * consistent with the other Sri Lankan parsers (Sampath, NSB).
 *
 * Amount/balance regexes tolerate arbitrary comma grouping (commas are stripped),
 * since real messages have been observed with non-standard grouping in the balance.
 *
 * The account number is masked with X's but exposes the last 4 digits
 * ("XXXXXXXX1234"); the last 4 are extracted. (Digits shown here are synthetic —
 * real account numbers are PII and must never be committed.)
 */
class NDBBankParser : BankParser() {

    override fun getBankName() = "National Development Bank"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean {
        // Sender is "NDB ALERT"; tolerate separator variants ("NDBALERT",
        // "NDB-ALERT") plus a bare "NDB". Exact matches only — a contains("NDB")
        // check would collide with IndusInd's "INDBNK" sender.
        val normalized = sender.uppercase().replace(Regex("[\\s-]"), "")
        return normalized == "NDBALERT" || normalized == "NDB"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // "LKR 12,724.00 debited" / "LKR 60,076.25 credited"
        val pattern = Regex(
            """LKR\s+([0-9,]+\.\d{2})\s+(?:credited|debited)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credited to") -> TransactionType.INCOME
            lower.contains("debited from") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // POS: "at COLOMBAY COLOMBO 2. Avl Bal ..."
        val posPattern = Regex("""\bat\s+(.+?)\.\s*Avl\s+Bal""", RegexOption.IGNORE_CASE)
        posPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Transfers: "as CEFTS Outward Transfer. Avl Bal ..."
        val descPattern = Regex("""\bas\s+(.+?)\.\s*Avl\s+Bal""", RegexOption.IGNORE_CASE)
        descPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "Avl Bal 2,582.08" — no currency code before the balance figure.
        // Commas are stripped, so non-standard grouping still parses.
        val pattern = Regex("""Avl\s+Bal\s+(?:LKR\s+)?([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractReference(message: String): String? {
        // NDB messages carry no transaction reference. The base class's generic
        // pattern misreads "POS TXN on ..." and returns "on", so suppress it.
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "AC XXXXXXXX7497" — last 4 digits after the X-masked prefix.
        val pattern = Regex("""AC\s+X+(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)
    }
}
