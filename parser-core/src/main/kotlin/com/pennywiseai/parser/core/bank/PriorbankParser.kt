package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Priorbank (Belarus) SMS messages, in both the transliterated Russian
 * and the English format:
 * - "Karta 6***6666 29-10-25 18:34:25. Oplata 12.90 BYN. BLR RBO N77 "KFC Zavod". Dostupno: 947.09 BYN."
 * - "Card 4***1234 31-10-26 10:39:14. Payment 5.00 BYN. BLR SHOP. Balance: 100.00 BYN Tel. ..."
 * - "Card 4***1234 31-10-26 10:38:12. Money transfer 70.00 BYN. BLR P2P SDBO NO FEE. Balance: ..."
 * - "Card 4***1234 31-10-26 10:38:12. The transfer received to the card 70.00 BYN. BLR . Balance: ..."
 *
 * Every message is "Card/Karta <mask> <date> <time>. <operation> <amount> <CCY>. <place>. <balance>".
 */
class PriorbankParser : BankParser() {

    override fun getBankName() = "Priorbank"

    override fun getCurrency() = "BYN"

    override fun canHandle(sender: String): Boolean =
        sender.uppercase().contains("PRIORBANK")

    /** Groups: 1 = operation words, 2 = amount, 3 = currency. */
    private val operationPattern = Regex(
        """(?:Karta|Card)\s+\S+\s+\d{2}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\s+(.+?)\s+(\d+(?:\.\d{1,2})?)\s+([A-Z]{3})\.""",
        RegexOption.IGNORE_CASE
    )

    override fun extractAmount(message: String): BigDecimal? =
        operationPattern.find(message)?.groupValues?.get(2)?.toBigDecimalOrNull()

    override fun transactionCurrency(message: String): String =
        operationPattern.find(message)?.groupValues?.get(3)?.uppercase() ?: getCurrency()

    override fun extractTransactionType(message: String): TransactionType? {
        val operation = operationPattern.find(message)?.groupValues?.get(1)?.lowercase() ?: return null
        val incomeWords = listOf("received", "zachislenie", "popolnenie", "postuplenie")
        val expenseWords = listOf("payment", "oplata", "transfer", "perevod", "cash", "snyatie")
        return when {
            incomeWords.any { operation.contains(it) } -> TransactionType.INCOME
            expenseWords.any { operation.contains(it) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Merchant in quotes: "KFC Zavod"
        Regex(""""([^"]+)"""").find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Place between the amount and the balance: "BYN. BLR AZS N55. Dostupno"
        Regex("""\b[A-Z]{3}\.\s+(.+?)\.\s+(?:Dostupno|Balance)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim().removePrefix("BLR").trim())
                if (merchant.isNotEmpty() && isValidMerchantName(merchant)) return merchant
            }

        return null
    }

    override fun extractAccountLast4(message: String): String? =
        Regex("""(?:Karta|Card)\s+\d*\*+(\d{4})\b""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)

    override fun extractBalance(message: String): BigDecimal? =
        Regex("""(?:Dostupno|Balance):\s*(\d+(?:\.\d{1,2})?)\s+[A-Z]{3}""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.toBigDecimalOrNull()

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // OTP / codes ("kod" = code, "parol" = password)
        if (lowerMessage.contains("otp") || lowerMessage.contains("kod") || lowerMessage.contains("parol")) {
            return false
        }

        return operationPattern.containsMatchIn(message)
    }
}
