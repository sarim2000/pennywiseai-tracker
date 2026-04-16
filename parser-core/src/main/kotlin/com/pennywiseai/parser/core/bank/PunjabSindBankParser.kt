package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Parser for Punjab & Sind Bank (PSB) SMS messages.
 *
 * Expected format:
 *   A/c No **<last4> Credited|Debited with Rs <amount>--<description> (CLR BAL <bal>CR|DR)(dd-MM-yyyy HH:mm:ss)-Punjab&Sind Bank
 *
 * <description> variants:
 *   - NEFT/<ref>/<sender name>
 *   - UPI/CR|DR/<utr>/<counterparty>/<bank>/<account>/<suffix>
 *   - Credit|Debit of <MICR> (cheque clearing)
 *   - Free text (e.g. generic vendor note)
 */
class PunjabSindBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Punjab & Sind Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("PSBANK") ||
                normalizedSender.contains("PUNJAB&SIND") ||
                normalizedSender.contains("PUNJAB & SIND")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val pattern = Regex(
            """(?:Credited|Debited)\s+with\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractAmount(message)
    }

    override fun extractAccountLast4(message: String): String? {
        val pattern = Regex(
            """A/[Cc]\s+No\s+\*+(\d{2,})""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val pattern = Regex(
            """CLR\s+BAL\s+([0-9,]+(?:\.\d{2})?)\s*(?:CR|DR)?""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        val neftRef = Regex("""NEFT/([A-Z0-9]+)/""", RegexOption.IGNORE_CASE)
        neftRef.find(message)?.let { return it.groupValues[1] }

        val upiRef = Regex("""UPI/(?:CR|DR)/(\d+)/""", RegexOption.IGNORE_CASE)
        upiRef.find(message)?.let { return it.groupValues[1] }

        val chequeRef = Regex("""(?:Credit|Debit)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)
        chequeRef.find(message)?.let { return it.groupValues[1] }

        val psbRef = Regex("""\b(PSB\d{10,})\b""")
        psbRef.find(message)?.let { return it.groupValues[1] }

        return super.extractReference(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val upiMerchant = Regex(
            """UPI/(?:CR|DR)/\d+/([^/]+)/""",
            RegexOption.IGNORE_CASE
        )
        upiMerchant.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        val neftMerchant = Regex(
            """NEFT/[A-Z0-9]+/([^(\r\n]+?)(?=\s*\(|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        neftMerchant.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        val chequePattern = Regex("""(Credit|Debit)\s+of\s+\d+""", RegexOption.IGNORE_CASE)
        chequePattern.find(message)?.let { match ->
            return if (match.groupValues[1].equals("Credit", ignoreCase = true)) {
                "Cheque Credit"
            } else {
                "Cheque Debit"
            }
        }

        val descPattern = Regex(
            """(?:Credited|Debited)\s+with\s+Rs\.?\s*[0-9,]+(?:\.\d{2})?\s*--\s*([^(\r\n]+?)\s*\(CLR\s+BAL""",
            RegexOption.IGNORE_CASE
        )
        descPattern.find(message)?.let { match ->
            val desc = match.groupValues[1].trim().trimEnd('-').trim()
            val merchant = cleanMerchantName(desc)
            if (isValidMerchantName(merchant)) return merchant
        }

        return super.extractMerchant(message, sender)
    }
}
