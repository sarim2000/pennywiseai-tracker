package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Parser for Bandhan Bank transaction SMS messages.
 *
 * Sample formats:
 * - "Dear Customer, your account XXXXXXXXXX1234 is credited with INR 3.00 on 01-OCT-2025 towards interest. Bandhan Bank"
 * - "INR 25,000.00 deposited to A/c XXXXXXXXXX1234 towards UPI/CR/C224513287910/JOHN DOE/u on 03-OCT-2025 . Clear Bal is INR 30,123.00 . Bandhan Bank."
 *
 * Senders generally follow DLT patterns like XY-BDNSMS-S.
 */
class BandhanBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Bandhan Bank"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()

        // Common short/long forms
        if (s.contains("BANDHAN")) return true

        // DLT/route patterns frequently used in India
        if (s.matches(Regex("^[A-Z]{2}-BDNSMS(?:-S)?$"))) return true
        if (s.matches(Regex("^[A-Z]{2}-BANDHN(?:-S)?$"))) return true

        return false
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern to extract merchant from "towards" section
        // Stops at: " Value", " on", " dt", " at", ".", or end of string
        val towardsPattern = Regex(
            """towards\s+([^\.\n]+?)(?:\s+Value|\s+on|\s+dt|\s+at|\.|$)""",
            RegexOption.IGNORE_CASE
        )

        towardsPattern.find(message)?.let { match ->
            var merchantRaw = match.groupValues[1].trim()

            // For UPI transactions with "/" delimiters, extract the last meaningful segment
            if (merchantRaw.contains("/")) {
                val segments = merchantRaw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
                val candidate = segments.lastOrNull { segment ->
                    segment.length >= 2 && segment.any { it.isLetter() } && !segment.equals(
                        "UPI",
                        ignoreCase = true
                    )
                } ?: segments.lastOrNull()

                if (candidate != null) {
                    merchantRaw = candidate
                }
            }

            // Clean up the merchant name
            val cleanedMerchant = cleanMerchantName(
                merchantRaw.replace(Regex("""\bu\b""", RegexOption.IGNORE_CASE), "").trim()
            )

            // Normalize specific merchants
            val normalizedMerchant = when {
                cleanedMerchant.equals("interest", ignoreCase = true) -> "Interest"
                else -> cleanedMerchant
            }

            if (isValidMerchantName(normalizedMerchant)) {
                return normalizedMerchant
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val upiReferencePattern = Regex(
            """UPI/[A-Z]{2}/([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )

        upiReferencePattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val clearBalancePattern = Regex(
            """Clear\s+Bal\s+(?:is\s+)?(?:INR\s*)?([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )

        clearBalancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (_: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }
}
