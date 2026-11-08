package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for the PhonePe wallet (India).
 *
 * Only wallet messages are read: PhonePe UPI payments are debited from a bank
 * account whose own SMS already records them, so parsing those here would count
 * each payment twice.
 *
 * Sample: "You've paid Rs.14.36 via PhonePe wallet for SHOP NAME. Not you? Call us on
 * 022-xxxxxxxx. Remaining balance: Rs.0. To top-up click https://phone.pe/PHONPE/ws"
 */
class PhonePeWalletParser : BaseIndianBankParser() {

    override fun getBankName() = "PhonePe Wallet"

    // A wallet: its balance goes to the single service-level wallet account.
    override fun isMobileWallet() = true

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("PHONPE") || upper.contains("PHONEPE")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (!lower.contains("phonepe wallet")) return false
        return super.isTransactionMessage(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // Read the action only from the words before "PhonePe wallet", so a merchant
        // named after it ("… wallet for LOADED FRIES") can't flip the direction.
        val action = message.lowercase().substringBefore("phonepe wallet")
        return when {
            action.contains("paid") || action.contains("debited") -> TransactionType.EXPENSE
            action.contains("refund") || action.contains("credited") ||
                action.contains("added") || action.contains("loaded") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        Regex("""via\s+PhonePe\s+wallet\s+(?:for|to|at)\s+(.+?)\.(?:\s|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) return merchant
            }
        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? = null

    override fun extractBalance(message: String): BigDecimal? {
        Regex("""Remaining\s+balance:?\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { return it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
        return super.extractBalance(message)
    }
}
