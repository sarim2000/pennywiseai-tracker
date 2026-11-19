package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

class BelarusbankParser : BankParser() {

    override fun getBankName() = "Belarusbank"

    override fun getCurrency() = "BYN"

    override fun canHandle(sender: String): Boolean {
        return sender == "ASB.BY"
    }

    /** Groups: 1 = operation, 2 = amount, 3 = card number. */
    private val transactionPattern = Regex(
        """^\s*(.+?)\s+(?:>>\s*)?(\d+(?:[.,]\d{1,2})?)\s+([A-Z]{3})\s+KARTA\s+#?(\d{4})\s+DATA\s+\d{2}\.\d{2}\.\d{4}\s+\d{2}:\d{2}:\d{2}\b[\s\S]*\b(BYN)\b\s*$""",
        RegexOption.IGNORE_CASE
    )

    override fun extractAmount(message: String): BigDecimal? =
        transactionPattern.find(message)
            ?.groupValues?.get(2)
            ?.replace(',', '.')
            ?.toBigDecimalOrNull()

    override fun extractAccountLast4(message: String): String? =
        transactionPattern.find(message)?.groupValues?.get(4)

    override fun extractBalance(message: String): BigDecimal? =
        Regex(
            """\bOSTATOK\s+(\d+(?:[.,]\d{1,2})?)\s+BYN\b""",
            RegexOption.IGNORE_CASE
        ).find(message)
            ?.groupValues?.get(1)
            ?.replace(',', '.')
            ?.toBigDecimalOrNull()

    override fun accountCurrency(message: String): String =
        transactionPattern.find(message)?.groupValues?.get(5)?.uppercase() ?: getCurrency()

    override fun transactionCurrency(message: String): String =
        transactionPattern.find(message)?.groupValues?.get(3)?.uppercase() ?: getCurrency()

    override fun extractTransactionType(message: String): TransactionType? {
        val operation = transactionPattern.find(message)
            ?.groupValues?.get(1)
            ?.uppercase()
            ?: return null

        return when {
            operation.contains("OTMENA") -> TransactionType.INCOME

            operation.contains("ZACHISLENIE") ||
                operation.contains("POPOLNENIE") -> TransactionType.INCOME

            operation.contains("OPLATA") ||
                operation.contains("NALICHNYE") ||
                operation.contains("SPISANIE") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val description = Regex(
            """\b\d{2}\.\d{2}\.\d{4}\s+\d{2}:\d{2}:\d{2}\s+(.+?)(?=\s+MCC:|\s+OSTATOK\b|$)""",
            RegexOption.IGNORE_CASE
        ).find(message)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: return null

        if (Regex("""^(?:OSTATOK\b|MCC\s*:)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(description)
        ) {
            return null
        }

        val merchant = description
            .substringBefore('>')
            .trim()
            .trimEnd('.', ',', ';')
    
        if (merchant.isEmpty()) return null
    
        val cleaned = cleanMerchantName(merchant)
        return cleaned.takeIf { it.isNotEmpty() && isValidMerchantName(it) }
    }

    override fun isTransactionMessage(message: String): Boolean {
        return transactionPattern.containsMatchIn(message) &&
            Regex("""\bKARTA\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(message)
    }
}
