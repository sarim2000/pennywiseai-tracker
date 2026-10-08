package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Parser for Punjab National Bank (PNB) SMS messages
 */
class PNBBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Punjab National Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("PUNJAB NATIONAL BANK") || // RCS sender (any case)
                normalizedSender.contains("PNBBNK") ||
                normalizedSender.contains("PUNBN") ||
                normalizedSender.contains("PNBSMS") || // Matches V?-PNBSMS-S
                normalizedSender.matches(Regex("^[A-Z]{2}-PNBBNK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-PNB-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-PNBBNK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-PNB$")) ||
                normalizedSender.matches(Regex("^(?:[A-Z]{2}-)?PNBCCD(?:-[ST])?$")) || // Credit card alerts
                normalizedSender == "PNBBNK" ||
                normalizedSender == "PNB"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Normalize Unicode text for RCS messages
        val normalizedBody = normalizeUnicodeText(smsBody)

        // Use normalized body for parsing
        return super.parse(normalizedBody, sender, timestamp)
    }

    private fun normalizeUnicodeText(text: String): String {
        // Use Java's built-in normalizer to decompose Unicode
        // NFKD = Compatibility Decomposition
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("[^\\p{ASCII}₹]"), "") // Keep ASCII and the rupee symbol
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Handle "a/c no XX340 is debited for Rs 7519" pattern
        val debitedForPattern = Regex(
            """debited\s+for\s+(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        debitedForPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Handle explicit debit of initial amount in auto-pay messages
        val initialDebitPattern = Regex(
            """initial\s+amount\s+of\s+(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{2})?)\s+has\s+been\s+debited""",
            RegexOption.IGNORE_CASE
        )
        initialDebitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Handle debit patterns with currency text or the rupee symbol
        // "with" is optional for backward compatibility ("debited Rs. X" and "debited with Rs. X")
        val debitPattern = Regex(
            """debited\s+(?:(?:with|by)\s+)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{2})?)""",
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

        // Handle credit patterns with currency text or the rupee symbol
        val creditPattern = Regex(
            """(?:(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{2})?)\s+(?:has\s+been\s+)?credited|credited\s+(?:(?:with|by|for)\s+)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{2})?))""",
            RegexOption.IGNORE_CASE
        )
        creditPattern.find(message)?.let { match ->
            // Try to get the amount from either capture group (pattern 1 or pattern 2)
            val amount =
                (if (match.groupValues[1].isNotEmpty()) match.groupValues[1] else match.groupValues[2])
                    .replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Note: Removed balance pattern - balance should never be used as transaction amount
        // Balance is extracted separately by extractBalance() method

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        if (isUPIMandateNotification(message)) {
            return null
        }

        // "PNB Credit Card 1234 debited with Rs.270 ..." is a card spend. Match the
        // card as the thing debited — a bank-account debit "towards PNB credit card
        // payment" also mentions the card and must stay an account expense.
        if (isCardSpend(message)) {
            return TransactionType.CREDIT
        }

        // Auto-Pay activation can carry a real initial debit that should remain an expense.
        if (lowerMessage.contains("auto pay facility") && lowerMessage.contains("debited")) {
            return TransactionType.EXPENSE
        }

        return super.extractTransactionType(message)
    }

    fun isUPIMandateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return (lowerMessage.contains("upi-mandate") || lowerMessage.contains("upi mandate")) &&
            lowerMessage.contains("successfully created")
    }

    fun parseUPIMandateSubscription(message: String): UPIMandateInfo? {
        if (!isUPIMandateNotification(message)) {
            return null
        }

        val amountPattern = Regex(
            """for\s+(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        val amount = amountPattern.find(message)?.groupValues?.get(1)?.replace(",", "")?.let {
            try {
                BigDecimal(it)
            } catch (_: NumberFormatException) {
                null
            }
        } ?: super.parseMandateSubscription(message)?.amount ?: return null

        val merchant = Regex(
            """towards\s+(.+?)\s+for\s+(?:Rs\.?|INR)""",
            RegexOption.IGNORE_CASE
        ).find(message)?.groupValues?.get(1)?.trim()?.let(::cleanMerchantName)
            ?.takeIf(::isValidMerchantName)
            ?: super.parseMandateSubscription(message)?.merchant
            ?: return null

        val umn = Regex("""UMN:?\s*([^.\s]+)""", RegexOption.IGNORE_CASE)
            .find(message)
            ?.groupValues
            ?.get(1)

        return UPIMandateInfo(
            amount = amount,
            nextDeductionDate = null,
            merchant = merchant,
            umn = umn,
            accountLast4 = extractAccountLast4(message)
        )
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Credit card spend: "... debited with Rs.270 [CODE:..] at <payee or VPA> on 04-10-2026"
        // The payee can be several words ("at AMAZON INDIA on …"), so take
        // everything up to "on <date>".
        if (isCardSpend(message)) {
            Regex("""\bat\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE).find(message)?.let { match ->
                // Same as HDFC credit cards: show the VPA's handle, not the bank suffix.
                val payee = cleanMerchantName(match.groupValues[1].substringBefore("@"))
                if (payee.isNotEmpty()) return payee
            }
        }

        // Handle IMPS transactions early to avoid base class patterns matching phone numbers
        if (message.contains("IMPS", ignoreCase = true)) {
            return "IMPS Transfer"
        }

        // Extract merchant from Auto-Pay activation: from Google Clouds
        val fromMerchantPattern = Regex(
            """auto\s+pay.*?activated.*?from\s+([^.]+?)(?:\s+An\s+initial|\.|$)""",
            RegexOption.IGNORE_CASE
        )
        fromMerchantPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Extract merchant from UPI-Mandate: towards Google Pay
        val towardsPattern = Regex(
            """UPI-Mandate.*towards\s+(.+?)\s+for""",
            RegexOption.IGNORE_CASE
        )
        towardsPattern.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }

        // Extract card info if available: thru card XX9239
        val cardPattern = Regex("""thru\s+card\s+([X\*]+\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return "Card ${match.groupValues[1]}"
        }

        if (message.contains("PNB ATM", ignoreCase = true)) {
            return "PNB ATM Withdrawal"
        }
        if (Regex("""\bATM\b""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return "ATM Transaction"
        }

        if (Regex("""thru\s+debitcard\b""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            return super.extractMerchant(message, sender) ?: "Debit Card Transaction"
        }

        if (message.contains("NEFT", ignoreCase = true)) {
            return "NEFT Transfer"
        }

        val upiPayeePattern = Regex(
            """\bto\s+(.+?)\s+thru\s+UPI\s*:""",
            RegexOption.IGNORE_CASE
        )
        upiPayeePattern.find(message)?.let { match ->
            val payee = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(payee)) {
                return payee
            }
        }

        if (message.contains("UPI", ignoreCase = true)) {
            val byPayeePattern = Regex("""\bby\s+((?:(?!\bby\b).)+?)\s+thru\s+UPI\b""", RegexOption.IGNORE_CASE)
            byPayeePattern.find(message)?.let { match ->
                val payee = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(payee)) {
                    return payee
                }
            }
            val fromPayeePattern = Regex("""\bfrom\s+([^/\r\n]+)/""", RegexOption.IGNORE_CASE)
            fromPayeePattern.find(message)?.let { match ->
                val payee = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(payee)) {
                    return payee
                }
            }
            return "UPI Transaction"
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        // Preserve short masks; take the trailing digits when more than four are visible.
        val acNoPattern = Regex(
            """(?:a/c\s+(?:no\.?\s*)?|ac\s+)[X*]+(\d{2,16})\b""",
            RegexOption.IGNORE_CASE
        )
        acNoPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        // Handle variations: Ac, Card followed by X/dots/spaces and then digits (4 to 16)
        val acPattern = Regex(
            """(?:A/c(?:\s*No\.)?|Ac|Card)\s*(?:[X\*]+)?(\d{4,16})""",
            RegexOption.IGNORE_CASE
        )
        acPattern.find(message)?.let { match ->
            return match.groupValues[1].takeLast(4)
        }

        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        val rrnPattern = Regex("""\bRRN\s*[-:]\s*(\d{6,})""", RegexOption.IGNORE_CASE)
        rrnPattern.find(message)?.let { return it.groupValues[1] }

        // Handle IMPS reference: "IMPS Ref no 606701245043"
        if (message.contains("IMPS", ignoreCase = true)) {
            // More flexible pattern: IMPS followed by any word then reference number
            val impsRefPattern = Regex(
                """IMPS\s+\w*\s*Ref\s*(?:no\.?\s*)?(\d{6,})""",
                RegexOption.IGNORE_CASE
            )
            impsRefPattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
            // Fallback: find a 12-digit number after IMPS (IMPS refs are 12 digits)
            val impsFallback = Regex(
                """IMPS[^0-9]*(\d{12,})""",
                RegexOption.IGNORE_CASE
            )
            impsFallback.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        val neftRefPattern = Regex(
            """ref\s+no\.\s+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        neftRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Handle UPI Ref ID: "(UPI Ref ID:606379499474)"
        val upiRefIdPattern = Regex(
            """UPI\s+Ref\s+ID:?\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefIdPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Handle "UPI: <number>" format
        val upiRefPattern = Regex(
            """UPI:\s*([0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Fall back to base class, but filter out "-PNB" suffix matches
        val baseRef = super.extractReference(message)
        return if (baseRef != null && baseRef.equals("PNB", ignoreCase = true)) null else baseRef
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Handle "Aval Bal", "Avl Bal", "Bal" followed by currency and amount, usually ending with CR/DR
        val balPattern = Regex(
            """(?:Aval\s+Bal|Avl\s+Bal|Avl|Bal)\s*(?:INR\s*|Rs\.?\s*|₹\s*)?([0-9,]+(?:\.\d{2})?)(?:\s+(?:CR|DR))?""",
            RegexOption.IGNORE_CASE
        )
        balPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fallback for just "Bal XXXX.XX CR"
        val simpleBalPattern = Regex(
            """Bal\s*([0-9,]+(?:\.\d{2})?)\s+(?:CR|DR)""",
            RegexOption.IGNORE_CASE
        )
        simpleBalPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // "Avl limit Rs. 48882.5." — one decimal digit, which the base patterns truncate.
        Regex("""Avl\s+limit\s*(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
            }
        return super.extractAvailableLimit(message)
    }

    private fun isCardSpend(message: String) =
        Regex("""PNB\s+Credit\s+Card\s+(?:XX)?\d{4}\s+debited""", RegexOption.IGNORE_CASE)
            .containsMatchIn(message)

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (isUPIMandateNotification(message)) {
            return false
        }

        // Credit card bill payment received: skipped as HDFC/ICICI do — the debit
        // from the paying bank account already records the money moving.
        if (lowerMessage.contains("received as payment towards your pnb credit card")) {
            return false
        }

        if (lowerMessage.contains("auto pay facility") && lowerMessage.contains("debited")) {
            return true
        }

        if (lowerMessage.contains("register for e-statement")) {
            return true
        }

        if (lowerMessage.contains("imps") && lowerMessage.contains("debited")) {
            return true
        }

        return super.isTransactionMessage(message)
    }

    data class UPIMandateInfo(
        override val amount: BigDecimal,
        override val nextDeductionDate: String?,
        override val merchant: String,
        override val umn: String?,
        override val accountLast4: String? = null
    ) : MandateInfo {
        override val dateFormat = "dd-MMM-yy"
    }
}
