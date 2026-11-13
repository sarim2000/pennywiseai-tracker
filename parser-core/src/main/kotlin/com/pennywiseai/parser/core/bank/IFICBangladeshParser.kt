package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * IFIC Bank Bangladesh SMS alerts (sender IFIC BANK).
 * Recognizes principal repayments, cash deposits, interest credits,
 * interest tax and system debits. Preserves balance when numeric.
 *
 * Both observed account forms expose only a four-digit suffix to the app;
 * accounts genuinely sharing the same suffix cannot be distinguished by
 * PennyWise's current BankName||Last4 identity scheme.
 */
class IFICBangladeshParser : BankParser() {
    companion object {
        private val DATE = Regex(
            """(?:[0-9]{2}/[0-9]{2}/[0-9]{4}\s+at\s+[0-9]{1,2}:[0-9]{2}|[0-9]{2}-[0-9]{2}-[0-9]{4}\s+[0-9]{1,2}:[0-9]{2}:[0-9]{2}\s*[AP]M)""",
            RegexOption.IGNORE_CASE
        )
        private val VALID_MONEY = Regex("""(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?:,[0-9]{2})+,[0-9]{3}|[0-9]+)(?:\.[0-9]{1,2})?""")
        private val ACTION = Regex(
            """BDT\s+([0-9][0-9,.]*)\s+(Automatic\s+Loan\s+Repayment-Principal|Cash\s+Deposit\s+for|Tax\s+on\s+Interest\s+for|Interest\s+for|Debit\s*\(SYSTEM\s+USE\))""",
            RegexOption.IGNORE_CASE
        )
        private val ACCOUNT = Regex("""(?:Joint\s+)?A/C\s+[0-9xX*]*\*([0-9]{4})""", RegexOption.IGNORE_CASE)
        private val BALANCE_AFTER = Regex("""BDT\s+([^\s]+)\s+Balance""", RegexOption.IGNORE_CASE)
        private val BALANCE_BEFORE = Regex("""Balance:\s*BDT\s+([^\s]+)""", RegexOption.IGNORE_CASE)
    }

    override fun getBankName(): String = "IFIC Bank (Bangladesh)"
    override fun getCurrency(): String = "BDT"
    override fun canHandle(sender: String): Boolean =
        sender.uppercase().replace(Regex("""[\s_-]"""), "") == "IFICBANK"

    private fun money(raw: String): BigDecimal? =
        if (VALID_MONEY.matches(raw)) raw.replace(",", "").toBigDecimalOrNull() else null

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (!canHandle(sender) || isNonTransactionMessage(smsBody)) return null
        val lines = smsBody.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 3 || !DATE.matches(lines[0])) return null
        val action = ACTION.matchEntire(lines[1]) ?: return null
        val amount = money(action.groupValues[1]) ?: return null
        if (amount <= BigDecimal.ZERO) return null
        val account = lines.drop(2).firstNotNullOfOrNull { ACCOUNT.matchEntire(it)?.groupValues?.get(1) }
            ?: return null
        val balance = lines.drop(2).firstNotNullOfOrNull { line ->
            (BALANCE_AFTER.matchEntire(line) ?: BALANCE_BEFORE.matchEntire(line))
                ?.groupValues?.get(1)?.let(::money)
        }
        val description = action.groupValues[2].lowercase().replace(Regex("\\s+"), " ")
        val (type, merchant) = when {
            description.startsWith("automatic loan repayment") -> TransactionType.EXPENSE to "Loan Principal Repayment"
            description.startsWith("cash deposit") -> TransactionType.INCOME to "Cash Deposit"
            description.startsWith("tax on interest") -> TransactionType.EXPENSE to "Tax on Interest"
            description.startsWith("interest for") -> TransactionType.INCOME to "Interest"
            description.startsWith("debit") -> TransactionType.EXPENSE to "Bank Debit (System Use)"
            else -> return null
        }
        return ParsedTransaction(
            amount = amount, type = type, merchant = merchant, reference = null,
            accountLast4 = account, balance = balance, smsBody = smsBody,
            sender = sender, timestamp = timestamp, bankName = getBankName(),
            isFromCard = false, currency = getCurrency()
        )
    }
}
