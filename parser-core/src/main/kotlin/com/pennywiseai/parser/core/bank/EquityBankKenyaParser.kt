package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Equity Bank (Kenya) SMS — currency KES (#894).
 *
 * Handles:
 * - "Your transaction of Kshs. 200.0  has been credited to  254700000000  Jane Doe. Ref.  EQA0000000000000.
 *    MPESA Ref.  TESTMPESA1 on  13/09/2026 at 16:13:44."
 *   The RECIPIENT is credited — money leaves the Equity account, so this is an EXPENSE.
 * - "You have received 2000.00 KES from TEST ENTERPRISES 0********1111 to your Equity account 0********1234.
 *    Ref. TESTREF0001 on 12 Sep 2026 at 17:56 EAT"
 *
 * The SMS use runs of spaces, so every pattern separates tokens with \s+.
 */
class EquityBankKenyaParser : BankParser() {

    override fun getBankName() = "Equity Bank"

    override fun getCurrency() = "KES"

    // Alphanumeric sender: "Equity Bank", "EQUITYBANK", "EQUITY_BANK". Equitas (India) uses EQUITA/EQUITS.
    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "").contains("EQUITY")

    override fun extractAmount(message: String): BigDecimal? {
        val match = Regex("""(?:Kshs?|KES)\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE).find(message)
            ?: Regex("""([0-9,]+(?:\.\d{1,2})?)\s*(?:Kshs?|KES)\b""", RegexOption.IGNORE_CASE).find(message)
            ?: return null
        return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            Regex("""you\s+have\s+received""").containsMatchIn(lower) -> TransactionType.INCOME
            Regex("""has\s+been\s+credited\s+to""").containsMatchIn(lower) -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val match = Regex("""credited\s+to\s+(?:\+?\d{9,13}\s+)?(.+?)\.\s+Ref""", RegexOption.IGNORE_CASE).find(message)
            ?: Regex("""received\s+.+?\s+from\s+(.+?)\s+(?:[0-9*X]{4,}|to\s+your)""", RegexOption.IGNORE_CASE).find(message)
            ?: return null
        val merchant = cleanMerchantName(match.groupValues[1].trim())
        return merchant.takeIf { isValidMerchantName(it) }
    }

    // First "Ref." is Equity's own reference; the later "MPESA Ref." is the wallet side's.
    override fun extractReference(message: String): String? =
        Regex("""\bRef\.\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)

    override fun extractAccountLast4(message: String): String? =
        Regex("""to\s+your\s+Equity\s+account\s+[0-9*X]*?(\d{4})\b""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)
}
