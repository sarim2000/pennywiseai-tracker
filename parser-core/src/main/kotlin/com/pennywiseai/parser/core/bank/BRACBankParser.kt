package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * BRAC Bank (Bangladesh, BDT) — #943. Sender: "BRAC BANK". Amounts are written "TK", "Tk" or "BDT".
 *
 * - ATM:      "TK 500.00 withdrawn from A/C#11111**1111 on 18-10-26 @12:24 PM from TEST MARKET ATM DHAKA BD. Balance TK 22,639.95. BRAC Bank."
 * - Card:     "BDT 1,920 transacted at TEST SHOP on 18/10/26 [08:45:24 AM BST] using Card#***1111. Available balance: BDT 98,997.32. Helpline 16221."
 * - Transfer: "Tk 4,000.00 has been transferred from your BBL A/C: 11111**111 to A/C: 2**2222. Available balance is Tk17,150.95. …"
 * - NPSB:     "Tk 10,010.00 (incl. charges) has been debited from your BBL A/C: 11111**1111 to A/C: 2**2222 for NPSB transfer. Available balance: Tk. 66,330.95. …"
 * - Credit:   "TK 28,500.00 credited to A/C#11111**1111 on 12-10-26 @11:23 AM from OTHER BANK. Balance TK 37,130.95. BRAC Bank."
 */
class BRACBankParser : BankParser() {

    override fun getBankName() = "BRAC Bank"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("""[\s_-]"""), "")
        return normalized.contains("BRACBANK") || normalized.contains("BRACBNK")
    }

    override fun isTransactionMessage(message: String): Boolean =
        super.isTransactionMessage(message) ||
            (!isNonTransactionMessage(message) && message.contains("transacted at", ignoreCase = true))

    // The transaction amount leads the message; balances come later.
    override fun extractAmount(message: String): BigDecimal? =
        Regex("""^\s*(?:TK|BDT)\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credited to") -> TransactionType.INCOME
            lower.contains("withdrawn") || lower.contains("transacted at") ||
                lower.contains("debited from") || lower.contains("transferred from") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lower = message.lowercase()
        if (lower.contains("withdrawn") && lower.contains(" atm")) return "ATM Withdrawal"
        if (lower.contains("npsb transfer")) return "NPSB Transfer"
        Regex("""transacted\s+at\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE).find(message)?.let { m ->
            val merchant = cleanMerchantName(m.groupValues[1].replace(Regex("""\s+"""), " ").trim())
            if (isValidMerchantName(merchant)) return merchant
        }
        // "credited to A/C#… from OTHER BANK. Balance …"
        Regex("""credited\s+to\s+.+?\bfrom\s+(.+?)\.\s""", RegexOption.IGNORE_CASE).find(message)?.let { m ->
            val merchant = cleanMerchantName(m.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }
        if (lower.contains("transferred from")) return "Fund Transfer"
        return null
    }

    // The user's own account/card comes first ("from your BBL A/C: 11111**111 to A/C: …").
    override fun extractAccountLast4(message: String): String? =
        Regex("""Card#\**(\d{4})""", RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
            ?: Regex("""A/C[#:]\s*\d*\*+(\d{3,4})""", RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)

    // "Balance TK 22,639.95" / "Available balance: BDT 98,997.32" / "Available balance is Tk17,150.95" / "Tk. 66,330.95"
    override fun extractBalance(message: String): BigDecimal? =
        Regex("""balance(?:\s+is)?:?\s*(?:TK|BDT)\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()

    override fun extractReference(message: String): String? = null

    override fun detectIsCard(message: String): Boolean = message.contains("Card#", ignoreCase = true)
}
