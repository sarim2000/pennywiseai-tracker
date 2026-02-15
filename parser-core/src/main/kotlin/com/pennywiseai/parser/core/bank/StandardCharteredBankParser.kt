package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Standard Chartered Bank SMS messages (India and Pakistan)
 *
 * Supported formats:
 * - UPI Debit: "Your a/c XX3421 is debited for Rs. 302.00 on 03-12-2025 15:49 and credited to a/c XX1465 (UPI Ref no 487597904232)"
 * - NEFT Credit: "Dear Customer, there is an NEFT credit of INR 48,796.00 in your account 123xxxx7655 on 1/11/2025.Available Balance:INR 97,885.05"
 * - PKR RAAST: "Dear Customer, PKR 55,000.00 sent to SCB PK A/C ****9901 for FUNDSTRANSFER 001 on 06-Feb-26 14:22 via RAAST"
 * - PKR IBFT: "Dear Client, an electronic funds transfer of PKR 5,000.00 has been made into your Account No. 0101xxx9901"
 *
 * Common senders: VM-SCBANK-S, VD-SCBANK-S, JK-SCBANK-S, SCBANK, StanChart, 9220 (Pakistan)
 */
class StandardCharteredBankParser : BankParser() {

    override fun getBankName() = "Standard Chartered Bank"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("SCBANK") ||
                upperSender.contains("STANCHART") ||
                upperSender.contains("STANDARDCHARTERED") ||
                upperSender.contains("STANDARD CHARTERED") ||
                upperSender == "9220" ||
                upperSender.matches(Regex("""^[A-Z]{2}-SCBANK-[A-Z]$"""))
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsed = super.parse(smsBody, sender, timestamp) ?: return null
        val currency = when {
            smsBody.contains("PKR", ignoreCase = true) -> "PKR"
            smsBody.contains("USD", ignoreCase = true) -> "USD"
            else -> parsed.currency
        }
        return parsed.copy(currency = currency)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pakistan: "PKR 55,000.00"
        val pkrPattern = Regex(
            """PKR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pkrPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return amount.toBigDecimalOrNull()
        }

        // International: "USD 79.00 have been paid at ..."
        val foreignCurrencyPattern = Regex(
            """\b(?:USD)\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        foreignCurrencyPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // India Pattern 1: "is debited for Rs. 302.00"
        val debitPattern = Regex(
            """is debited for Rs\.\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // India Pattern 2: "NEFT credit of INR 48,796.00"
        val neftCreditPattern = Regex(
            """(?:NEFT|RTGS|IMPS)\s+credit\s+of\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        neftCreditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // India Pattern 3: "is credited for Rs. xxx"
        val creditPattern = Regex(
            """is credited for Rs\.\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Pakistan-specific
            lowerMessage.contains("payment of") && lowerMessage.contains("financing") -> TransactionType.EXPENSE
            lowerMessage.contains("transaction of pkr") && lowerMessage.contains("using online banking") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn from account") -> TransactionType.EXPENSE
            lowerMessage.contains("cash withdrawal transaction") -> TransactionType.EXPENSE
            lowerMessage.contains("paid at") -> TransactionType.EXPENSE
            lowerMessage.contains("transaction of pkr") && lowerMessage.contains("to") -> TransactionType.TRANSFER
            lowerMessage.contains("sent to scb pk") -> TransactionType.INCOME
            lowerMessage.contains("electronic funds transfer") && lowerMessage.contains("into your account") -> TransactionType.INCOME
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            // India-specific
            lowerMessage.contains("is debited for") -> TransactionType.EXPENSE
            lowerMessage.contains("neft credit") -> TransactionType.INCOME
            lowerMessage.contains("rtgs credit") -> TransactionType.INCOME
            lowerMessage.contains("imps credit") -> TransactionType.INCOME
            lowerMessage.contains("is credited for") -> TransactionType.INCOME
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pakistan-specific merchant patterns
        if (message.contains("sent to scb pk", ignoreCase = true)) {
            return "RAAST Transfer"
        }

        if (message.contains("financing facility", ignoreCase = true)) {
            return "Financing Payment"
        }

        if (message.contains("withdrawn", ignoreCase = true) ||
            message.contains("cash withdrawal", ignoreCase = true)
        ) {
            return "ATM Cash Withdrawal"
        }

        // India Pattern 1: "credited to a/c XX1465" (for debit/UPI transfers)
        val upiTransferPattern = Regex(
            """and credited to a/c ([X\*]+\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiTransferPattern.find(message)?.let { match ->
            val accountNum = match.groupValues[1]
            return "UPI Transfer to $accountNum"
        }

        // India Pattern 2: NEFT/RTGS/IMPS credits
        if (message.lowercase().contains("neft credit")) {
            return "NEFT Credit"
        }
        if (message.lowercase().contains("rtgs credit")) {
            return "RTGS Credit"
        }
        if (message.lowercase().contains("imps credit")) {
            return "IMPS Credit"
        }

        // Pakistan: "paid at ELITE CLUB on"
        val paidAtPattern = Regex(
            """paid at\s+([A-Za-z0-9\s.\-]+?)\s+on""",
            RegexOption.IGNORE_CASE
        )
        paidAtPattern.find(message)?.let { match ->
            return cleanMerchantName(match.groupValues[1])
        }

        // Pakistan: "to TANBITS on" (online banking transfer)
        val transferToPattern = Regex(
            """to\s+([A-Za-z0-9*]+)(?:\s|$)""",
            RegexOption.IGNORE_CASE
        )
        transferToPattern.find(message)?.let { match ->
            val dest = match.groupValues[1]
            if (dest.isNotBlank()) {
                val normalized = dest.lowercase()
                if (normalized == "your" || normalized == "account" || normalized == "iban" || normalized == "acct") {
                    return@let
                }
                return when {
                    dest.all { it == '*' } -> "Transfer"
                    dest.startsWith("****") -> "Transfer to ${dest.takeLast(4)}"
                    dest.length in 3..8 && dest.any { it.isLetter() } -> cleanMerchantName(dest)
                    dest.length in 3..8 -> "Transfer to $dest"
                    else -> "Transfer"
                }
            }
        }

        // Pakistan: "from account 18-87xxxxx-9039959 PAYONEER from IBFT"
        val fromAccountPattern = Regex(
            """from account\s+[A-Za-z0-9\-*xX]+(?:\s+([A-Z][A-Za-z0-9\s]+?))(?:\s+from\s+IBFT|\s+via|\s+on|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        fromAccountPattern.find(message)?.let { match ->
            val name = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (name.isNotEmpty()) {
                return cleanMerchantName(name)
            }
        }

        val genericFromAccountPattern = Regex(
            """from account\s+[A-Za-z0-9\-*xX]+""",
            RegexOption.IGNORE_CASE
        )
        if (genericFromAccountPattern.containsMatchIn(message)) {
            return "IBFT Transfer"
        }

        if (message.contains("RAAST", ignoreCase = true)) {
            return "RAAST Transfer"
        }

        if (message.contains("IBFT", ignoreCase = true) ||
            message.contains("electronic funds transfer", ignoreCase = true)
        ) {
            return "IBFT Transfer"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // India Pattern 1: "Your a/c XX3421"
        val acPattern = Regex(
            """Your a/c ([X*]+)(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        acPattern.find(message)?.let { match ->
            return match.groupValues[2]
        }

        // India Pattern 2: "in your account 123xxxx7655"
        val accountPattern = Regex(
            """in your account (?:\d+[xX*]+)?(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pakistan Pattern 3: "A/C ****9901" or "Account No. 0101xxx9901"
        val maskedAccountPattern = Regex(
            """(?:A/C\s*[*Xx]+|Account No\.\s*[0-9Xx*]+|Acc\. Number\s*[0-9Xx*]+|Iban\.\s*[*Xx]+)(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        maskedAccountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pakistan Pattern 4: "credit/debit card no 53119xxxxxxxx1640"
        val cardPattern = Regex(
            """card no\.?\s*[0-9Xx*\s-]*?(\d{4})(?![0-9Xx])""",
            RegexOption.IGNORE_CASE
        )
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pakistan Pattern 5: "your account 01-01***9901"
        val yourAccountPattern = Regex(
            """your account\s+[0-9\-*xX]+""",
            RegexOption.IGNORE_CASE
        )
        yourAccountPattern.find(message)?.let { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length >= 4) return digits.takeLast(4)
        }

        // Pakistan Pattern 6: "account 01-70***32-01"
        val flexibleAccountPattern = Regex(
            """account\s+[0-9\-*xX]+""",
            RegexOption.IGNORE_CASE
        )
        flexibleAccountPattern.find(message)?.let { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length >= 4) return digits.takeLast(4)
            if (digits.isNotEmpty()) return digits.takeLast(2)
        }

        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // India: "UPI Ref no 487597904232"
        val upiRefPattern = Regex(
            """UPI Ref no (\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pakistan: "TX ID FAYS2602061422..."
        val txIdPattern = Regex("""TX ID ([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        txIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pakistan: "Transaction ID:PK-019-..."
        val transactionIdPattern = Regex("""Transaction ID:([A-Z0-9\-]+)""", RegexOption.IGNORE_CASE)
        transactionIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // India: "Available Balance:INR 97,885.05"
        val balancePattern = Regex(
            """Available Balance:\s*INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pakistan: "Avail Limit PKR 18062.81"
        val availLimitPattern = Regex(
            """Avail Limit\s*PKR\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        availLimitPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return balanceStr.toBigDecimalOrNull()
        }

        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("is debited for") ||
            lowerMessage.contains("is credited for") ||
            lowerMessage.contains("neft credit") ||
            lowerMessage.contains("rtgs credit") ||
            lowerMessage.contains("imps credit") ||
            lowerMessage.contains("withdrawn from account") ||
            lowerMessage.contains("cash withdrawal transaction") ||
            lowerMessage.contains("paid at") ||
            lowerMessage.contains("payment of") ||
            lowerMessage.contains("transaction of pkr") ||
            lowerMessage.contains("sent to scb pk") ||
            lowerMessage.contains("electronic funds transfer") ||
            lowerMessage.contains("has been credited")
        ) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}
