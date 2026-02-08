package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Telebirr - handles ETB currency transactions
 */
class TelebirrParser: BankParser() {

    override fun getBankName() = "Telebirr"

    override fun getCurrency() = "ETB"  // Ethiopian Birr

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase().trim()
        return upperSender == "127" ||
                upperSender.contains("127") ||
                // DLT patterns for Ethiopia: "XX-127-X" format
                upperSender.matches(Regex("""^[A-Z]{2}-127-[A-Z]$""")) ||
                // Alternative patterns: "127-XXX" or "XXX-127"
                upperSender.matches(Regex("""^127-[A-Z0-9]+$""")) ||
                upperSender.matches(Regex("""^[A-Z0-9]+-127$"""))
    }

    override fun extractAmount(message: String): BigDecimal? {
        // CBE patterns: "ETB 3,000.00", "ETB 25.00", "ETB250"
        val patterns = listOf(
            Regex("""ETB\s+([0-9,]+(?:\.[0-9]{2})?)\s""", RegexOption.IGNORE_CASE),
            Regex("""ETB\s*([0-9,]+(?:\.[0-9]{2})?)(?:\s|$|\.)""", RegexOption.IGNORE_CASE),
            Regex(
                """(?:Credited|debited|transfered)\s+(?:with\s+)?ETB\s+([0-9,]+(?:\.[0-9]{2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Savings flows have reversed direction relative to the saving account:
            // - Deposit TO saving account => money leaves telebirr wallet => EXPENSE
            // - Withdraw FROM saving account => money enters telebirr wallet => INCOME
            lowerMessage.contains("deposited etb") &&
                    lowerMessage.contains("to your saving account") -> TransactionType.EXPENSE
            (lowerMessage.contains(" withdraw etb") || lowerMessage.contains("withdraw etb")) &&
                    lowerMessage.contains("from your saving account") -> TransactionType.INCOME

            // Credit transactions are income
            lowerMessage.contains("you have received") -> TransactionType.INCOME

            // Debit transactions are expenses
            lowerMessage.contains("you have paid") -> TransactionType.EXPENSE

            // Transfer transactions are expenses (money going out)
            lowerMessage.contains("you have transferred") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 0: Savings deposit - "deposited ETB ... to your Saving Account on ..."
        val savingsDepositPattern = Regex(
            """deposited\s+ETB\s+[0-9,]+(?:\.[0-9]{2})?\s+to\s+your\s+(.+?)\s+on\s+\d{2}/\d{2}/\d{4}""",
            RegexOption.IGNORE_CASE
        )
        savingsDepositPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Pattern 0b: Savings withdraw - "Withdraw ETB ... from your saving account on ..."
        val savingsWithdrawPattern = Regex(
            """withdraw(?:n)?\s+ETB\s+[0-9,]+(?:\.[0-9]{2})?\s+from\s+your\s+(.+?)\s+on\s+\d{2}/\d{2}/\d{4}""",
            RegexOption.IGNORE_CASE
        )
        savingsWithdrawPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Pattern 1: "from Zemen Bank to your telebirr Account" (bank transfer income) - check this first
        val bankFromPattern = Regex("""from\s+([A-Za-z\s]+Bank)\s+to\s+your""", RegexOption.IGNORE_CASE)
        bankFromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 2: "paid ETB X to 519680 - City Government..." (government payment)
        // Capture everything until "on DATE" or "Your transaction number"
        // Use a lookahead to capture the space before "on" if it exists
        val paidToPattern = Regex("""paid\s+ETB\s+[0-9,]+(?:\.[0-9]{2})?\s+to\s+([^,\n]+?)(?=\s+on\s+\d{2}/\d{2}/\d{4}|\.\s+Your\s+transaction|$)""", RegexOption.IGNORE_CASE)
        paidToPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1]
            // Check if there's a space before "on" - look at the text right after the match
            val matchEnd = match.range.last + 1
            if (matchEnd < message.length) {
                val textAfterMatch = message.substring(matchEnd)
                if (textAfterMatch.trimStart().startsWith("on ", ignoreCase = true) && textAfterMatch[0] == ' ') {
                    merchant += " "
                }
            }
            if (merchant.isNotEmpty()) {
                return merchant
            }
        }

        // Pattern 3: "paid ETB X for goods purchased from 521902 - SAMUEL..." (merchant payment)
        val purchasedFromPattern = Regex("""for\s+goods\s+purchased\s+from\s+([^,\n]+?)(?:\s+on\s+\d{2}/\d{2}/\d{4}|\.\s+Your\s+transaction|$)""", RegexOption.IGNORE_CASE)
        purchasedFromPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            merchant = merchant.trim()
            if (merchant.isNotEmpty()) {
                return merchant
            }
        }

        // Pattern 4: "paid ETB X for package Monthly 240Min..." (airtime payment)
        // Need to capture "Monthly 240Min + 24GB Data purchase made for 911111119"
        val packagePattern = Regex("""for\s+package\s+([^,\n]+?)(?:\s+purchase\s+made|\s+on\s+\d{2}/\d{2}/\d{4}|\.\s+Your\s+transaction|$)""", RegexOption.IGNORE_CASE)
        packagePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Also capture "purchase made for X" part if it exists
            val purchaseMadePattern = Regex("""purchase\s+made\s+for\s+(\d+)""", RegexOption.IGNORE_CASE)
            val purchaseMatch = purchaseMadePattern.find(message)
            if (purchaseMatch != null) {
                merchant += " purchase made for ${purchaseMatch.groupValues[1]}"
                // Check if there's a space after the number before "on"
                val afterNumber = purchaseMatch.range.last + 1
                if (afterNumber < message.length && message[afterNumber] == ' ') {
                    val nextPart = message.substring(afterNumber + 1).trimStart()
                    if (nextPart.startsWith("on ", ignoreCase = true)) {
                        merchant += " "
                    }
                }
            }
            if (merchant.isNotEmpty()) {
                return merchant
            }
        }

        // Pattern 5: "transferred... to Commercial Bank of Ethiopia..." (bank transfer expense)
        // But also "to Person Name (2519****4211)" - preserve parentheses
        val transferredToPattern = Regex("""transferred\s+[^,\n]+?\s+to\s+([^,\n]+?)(?:\s+on\s+\d{2}/\d{2}/\d{4}|\.|$)""", RegexOption.IGNORE_CASE)
        transferredToPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Don't clean if it contains parentheses - preserve the phone number
            if (merchant.contains("(") && merchant.contains(")")) {
                return merchant
            }
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 6: "from PERSON NME(2519****2078)" (received transaction) - but not "from your account"
        val fromPattern = Regex("""from\s+(?!your\s+account)([^,\n]+?)(?:\s+on\s+\d{2}/\d{2}/\d{4}|\s+to\s+your|\.|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Handle phone number in parentheses: "PERSON NME(2519****2078)" -> "PERSON NME (2519****2078)"
            merchant = merchant.replace(Regex("""([A-Za-z\s]+)\((\d+\*+\d+)\)"""), "$1 ($2)")
            // Don't clean if it contains parentheses - preserve the phone number exactly as formatted
            if (merchant.contains("(") && merchant.contains(")")) {
                return merchant
            }
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 6: "to Person Name (2519****4211)" (transfer transaction)
        val toPattern = Regex("""to\s+([^,\n]+?)(?:\s+on\s+\d{2}/\d{2}/\d{4}|\.|$)""", RegexOption.IGNORE_CASE)
        toPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Don't clean if it contains parentheses - preserve the phone number
            if (merchant.contains("(")) {
                return merchant
            }
            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern: "Dear [Name]" - extract the name in brackets
        val dearPattern = Regex("""Dear\s+\[([^\]]+)\]""", RegexOption.IGNORE_CASE)
        dearPattern.find(message)?.let { match ->
            return "[${match.groupValues[1]}]"
        }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "Your current E-Money Account balance is ETB 9,719.23"
        val eMoneyBalancePattern =
            Regex("""E-Money Account\s+balance is ETB\s+([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
        eMoneyBalancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern: "Your current balance is ETB 334.23"
        val currentBalancePattern =
            Regex("""current balance is ETB\s+([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
        currentBalancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern: "Your telebirr account balance is ETB 496.04"
        val telebirrBalancePattern =
            Regex("""telebirr account balance is\s+ETB\s+([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
        telebirrBalancePattern.find(message)?.let { match ->
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
        // Look for "bank transaction number is FT2603327H99" (preferred for bank transfers)
        val bankTransactionPattern = Regex("""bank transaction number is\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        bankTransactionPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Look for "by transaction number DAV5COORPD" (bank transfer income)
        val byTransactionPattern = Regex("""by transaction number\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        byTransactionPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Look for "transaction number is DAV4D0PVWS" or "Your transaction number is DAV4D0PVWS"
        val transactionPattern = Regex("""(?:your\s+)?transaction number is\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        transactionPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Telebirr specific transaction keywords
        val telebirrTransactionKeywords = listOf(
            "dear",
            "you have received",
            "you have paid",
            "you have transferred",
            "current balance",
            "e-money account balance",
            "telebirr account balance",
            "thank you for using telebirr",
            "etb",
            "transaction number"
        )

        if (telebirrTransactionKeywords.any { lowerMessage.contains(it) }) {
            return true
        }

        return super.isTransactionMessage(message)
    }
}