package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Parser for IndusInd Bank SMS messages (India)
 *
 * Notes:
 * - Defaults to INR via base class
 * - Relies on base patterns for amount, balance, merchant, account, reference
 * - canHandle() includes common DLT sender variants seen in India
 */
class IndusIndBankParser : BaseIndianBankParser() {

    override fun getBankName() = "IndusInd Bank"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()

        // Common short/long forms
        if (s == "INDUSB" || s == "INDUSIND" || s.contains("INDUSIND BANK")) return true

        // DLT/route patterns frequently used in India
        // Allow -S, -T, or no suffix (e.g., VM-INDUSB, VM-INDUSB-S, VM-INDUSB-T)
        if (s.matches(Regex("^[A-Z]{2}-INDUSB(?:-[A-Z])?$"))) return true
        if (s.matches(Regex("^[A-Z]{2}-INDUSIND(?:-[A-Z])?$"))) return true

        // Some routes omit the trailing suffix or vary the middle part
        if (s.matches(Regex("^[A-Z]{2}-INDUS(?:[A-Z]{2,})?-[A-Z]$"))) return true

        return false
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        // IndusInd typically uses standard verbs; fall back to base for most, but
        // explicitly treat "spent" and "purchase" as expenses to avoid ambiguity.
        return when {
            lower.contains("spent") -> TransactionType.EXPENSE
            lower.contains("debited") -> TransactionType.EXPENSE
            lower.contains("purchase") -> TransactionType.EXPENSE
            lower.contains("deposit") -> TransactionType.INVESTMENT
            lower.contains("fd") -> TransactionType.INVESTMENT
            lower.contains("ach") -> TransactionType.INVESTMENT
            else -> super.extractTransactionType(message)
        }
    }

    /**
     * Force non-card detection for ACH/NACH messages since these are account debits/credits.
     */
    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        val isAchOrNach =
            lower.contains("ach db") || lower.contains("ach cr") || lower.contains("nach")
        if (isAchOrNach) return false
        return super.detectIsCard(message)
    }



    /**
     * Detect balance-only notifications (not transactions).
     * Examples:
     *  - "Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/25 04:10 AM ..."
     */
    override fun isBalanceUpdateNotification(message: String): Boolean {
        val lower = message.lowercase()
        val hasBalanceCue = lower.contains("avl bal") ||
                lower.contains("available bal") ||
                lower.contains("account balance") ||
                lower.contains("a/c balance")
        val hasTxnVerb = listOf("debited", "credited", "withdrawn", "spent", "transferred")
            .any { lower.contains(it) }
        return hasBalanceCue && lower.contains("as on") && !hasTxnVerb
    }

    /**
     * Parse balance-only notifications.
     */
    override fun parseBalanceUpdate(message: String): BaseBalanceUpdateInfo? {
        if (!isBalanceUpdateNotification(message)) return null

        // Extract account last4 using existing helper
        val accountLast4 = extractAccountLast4(message) ?: return null

        // Extract balance amount
        // Pattern 1: "Avl BAL of INR 1,234.56"
        val p1 = Regex("""Avl\s*BAL\s+of\s+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val balance = p1.find(message)?.let { m ->
            runCatching { BigDecimal(m.groupValues[1].replace(",", "")) }.getOrNull()
        } ?: run {
            // Pattern 2: "Avl BAL INR 1,234.56" | "Available Balance is INR ..." | "Bal INR ..."
            val p2 = Regex(
                """(?:Avl\s*BAL|Available\s+Balance(?:\s+is)?|Bal)[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""",
                RegexOption.IGNORE_CASE
            )
            p2.find(message)?.let { m ->
                runCatching { BigDecimal(m.groupValues[1].replace(",", "")) }.getOrNull()
            }
        } ?: return null

        // Extract optional "as on" date. IndusInd often uses: dd/MM/yy hh:mm AM/PM
        val datePattern = Regex(
            """as\s+on\s+(\d{1,2}/\d{1,2}/\d{2})\s+(\d{1,2}:\d{2})\s*(AM|PM)""",
            RegexOption.IGNORE_CASE
        )
        val asOfDate = datePattern.find(message)?.let { match ->
            val dateParts = match.groupValues[1].split("/")
            val timeParts = match.groupValues[2].split(":")
            val ampm = match.groupValues[3].uppercase()
            runCatching {
                val day = dateParts[0].toInt()
                val month = dateParts[1].toInt()
                val year = 2000 + dateParts[2].toInt()
                var hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                // Convert 12-hour to 24-hour format
                hour = when {
                    ampm == "PM" && hour < 12 -> hour + 12
                    ampm == "AM" && hour == 12 -> 0
                    else -> hour
                }
                LocalDateTime.of(year, month, day, hour, minute)
            }.getOrNull()
        }

        return BaseBalanceUpdateInfo(
            bankName = getBankName(),
            accountLast4 = accountLast4,
            balance = balance,
            asOfDate = asOfDate
        )
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        // Skip interest payout on deposits as per requirement
        if (lower.contains("net interest") && lower.contains("deposit no")) {
            return false
        }
        return super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): java.math.BigDecimal? {
        // Prefer transaction amount tied to action verbs to avoid picking Available Balance
        val verbAmountPattern = Regex(
            """(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.\d{2})?)\s+(?:debited|credited|spent|withdrawn|paid|purchase)""",
            RegexOption.IGNORE_CASE
        )
        verbAmountPattern.find(message)?.let { match ->
            val amt = match.groupValues[1].replace(",", "")
            return try {
                java.math.BigDecimal(amt)
            } catch (_: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // UPI-style: towards <vpa or merchant>
        // Capture the next token (can include dots) and strip trailing punctuation
        val towardsPattern = Regex("""towards\s+(\S+)""", RegexOption.IGNORE_CASE)
        towardsPattern.find(message)?.let { match ->
            var m = match.groupValues[1].trim().trimEnd('.', ',', ';')
            if (m.contains("/")) m = m.substringBefore("/")
            if (m.contains("@")) m = m.substringBefore("@").trim()
            if (m.isNotEmpty()) return cleanMerchantName(m)
        }

        // Credit: from account XXXX/MERCHANT pattern
        // Example: "received from account XXXXXXX4321/MADMONEY"
        val fromAccountPattern = Regex("""from\s+account\s+[^\s/]+/([^\s(]+)""", RegexOption.IGNORE_CASE)
        fromAccountPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim().trimEnd('.', ',', ';', ')')
            if (merchant.isNotEmpty()) return cleanMerchantName(merchant)
        }

        // Credit: from <vpa or merchant>
        val fromPattern = Regex("""from\s+(\S+)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val token = match.groupValues[1].trim().trimEnd('.', ',', ';')
            var m = token
            if (m.contains("/")) m = m.substringBefore("/")
            if (m.contains("@")) {
                m = m.substringBefore("@").trim()
                if (m.isNotEmpty()) return cleanMerchantName(m)
            }
        }

        // Card/POS: at <merchant>
        val atPattern = Regex("""at\s+([^\n]+?)(?:\s+Ref|\s+on|$)""", RegexOption.IGNORE_CASE)
        atPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return cleanMerchantName(merchant)
        }

        // Pattern: Ref-.../REFID/<Merchant>.Bal ... -> capture merchant between last '/' and '.Bal'
        val merchantBeforeBal = Regex("""/(?!\s)([^/\.\s]+)\.\s*Bal""", RegexOption.IGNORE_CASE)
        merchantBeforeBal.find(message)?.let { match ->
            val m = match.groupValues[1].trim()
            if (m.isNotEmpty()) return cleanMerchantName(m)
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Pattern 1: "IndusInd Account 20XXXXX1234" - extract last 4 digits
        val indusIndAccountPattern = Regex(
            """IndusInd\s+Account\s+\d+X+(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        indusIndAccountPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: "account XXXXXXX1234" - X's followed by 4 digits
        val accountXPattern = Regex(
            """account\s+X{5,}(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        accountXPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 3: IndusInd balance/alerts often mask accounts like: "A/C 2134***12345"
        val maskedPattern = Regex(
            """A/?C\s+([0-9]{2,})[\*xX#]+(\d{4,})""",
            RegexOption.IGNORE_CASE
        )
        maskedPattern.find(message)?.let { match ->
            val trailing = match.groupValues[2]
            // Always normalize to last 4 digits for account matching consistency
            return if (trailing.length >= 4) trailing.takeLast(4) else trailing
        }

        // Pattern 4: "A/c *XX1234" -> capture trailing digits after masked Xs or *
        val starMaskPattern = Regex(
            """A/?c\s+\*?X+\s*(\d{4,6})""",
            RegexOption.IGNORE_CASE
        )
        starMaskPattern.find(message)?.let { match ->
            val digits = match.groupValues[1]
            return if (digits.length >= 4) digits.takeLast(4) else digits
        }

        // For ACH/NACH messages, treat as account transaction and defer to default account
        val lower = message.lowercase()
        if (lower.contains("ach db") || lower.contains("ach cr") || lower.contains("nach")) {
            return null
        }

        // DO NOT fallback to base implementation - base patterns are too generic
        // and can cause false positives. Better to return null than wrong account.
        return null
    }

    override fun extractBalance(message: String): java.math.BigDecimal? {
        // Pattern: "Avl BAL of INR 1,234.56"
        val pattern1 = Regex(
            """Avl\s*BAL\s+of\s+INR\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pattern1.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                java.math.BigDecimal(balanceStr)
            } catch (_: NumberFormatException) {
                null
            }
        }

        // Variant: "Avl BAL INR 1,234.56", "Available Balance is INR ...", or "Bal INR ..."
        val pattern2 = Regex(
            """(?:Avl\s*BAL|Available\s+Balance(?:\s+is)?|Bal)[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        pattern2.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                java.math.BigDecimal(balanceStr)
            } catch (_: NumberFormatException) {
                null
            }
        }

        // Fallback to base patterns
        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Capture RRN numbers
        val rrnPattern = Regex("""RRN[:\s]+([0-9]+)""", RegexOption.IGNORE_CASE)
        rrnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Capture IMPS/UPI Ref no. pattern
        // Example: "IMPS Ref no. 123456789" or "Ref no. 123456789"
        val refNoPattern = Regex("""(?:IMPS\s+)?Ref\s+no\.?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
        refNoPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

}
