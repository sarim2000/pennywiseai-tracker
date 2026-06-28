package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Slice payments bank transactions.
 * Handles messages from JK-SLICEIT and similar senders.
 */
class SliceParser : BankParser() {

    override fun getBankName() = "Slice"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("SLICE") ||
                normalizedSender.contains("SLICEIT") ||
                normalizedSender.contains("SLCEIT") ||  // Matches JD-SLCEIT-S and similar
                normalizedSender.contains("SLCBNK")  // Slice SFB sender, e.g. VA-SLCBNK-S
    }

    private fun isSuccessMessage(message: String): Boolean {
        val lower = message.lowercase()
        // Use word boundaries to avoid matching "unsuccessful"
        return Regex("""\bsuccessful\b""").containsMatchIn(lower) || 
               Regex("""\bsuccess\b""").containsMatchIn(lower) || 
               lower.contains("approved") ||
               lower.contains("confirmed")
    }

    private fun isFailureMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("declined") ||
               lower.contains("failed") ||
               lower.contains("rejected") ||
               lower.contains("error") ||
               lower.contains("denied") ||
               lower.contains("unsuccessful")
    }

    private fun isDatePhrase(text: String): Boolean {
        // Simple date pattern matching month names with day numbers
        val datePattern = Regex("""\b(?:\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2})\b""", RegexOption.IGNORE_CASE)
        return datePattern.containsMatchIn(text)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // OTP / authorization messages are not completed transactions.
        // (e.g. "is your OTP for txn of Rs. ... on slice card ending ...")
        if (lowerMessage.contains("otp")) {
            return false
        }

        // UPI AutoPay mandate lifecycle notices ("... is revoked", "is paused",
        // "is suspended") are not money movements.
        if (lowerMessage.contains("revoked") ||
            lowerMessage.contains("is paused") ||
            lowerMessage.contains("is suspended")) {
            return false
        }

        // Slice uses "sent" for UPI transfers, and the base class transaction
        // keyword list does not include "sent" — so accept it explicitly.
        // Collect/payment requests do not use "sent" wording and are filtered
        // by the base class below (it rejects "collect request" / "payment
        // request" / "has requested" / "have received payment" etc.).
        if (lowerMessage.contains("sent")) {
            return true
        }

        // For "transaction" keyword, ensure it's a successful transaction
        if (lowerMessage.contains("transaction")) {
            return isSuccessMessage(message) && !isFailureMessage(message)
        }

        // Everything else — including the SFB "received in slice A/c" credit and
        // the AutoPay "Successfully paid" debit — is handled by the base class,
        // whose keyword set ("received"/"paid"/...) accepts genuine transactions
        // while its guards reject collect/payment requests, OTPs and reminders.
        // A UPI collect-request from the slice sender therefore can no longer be
        // booked as income.
        return super.isTransactionMessage(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // --- Slice SFB (banking) formats ---

        // Card transaction: "transaction of Rs. 2.07 at FamAppbyTriO from a/c ... is successful"
        val atMerchantPattern = Regex(
            """\bat\s+(.+?)(?:\s+from\b|\s+on\b|\s+is\b|\.\s|$)""",
            RegexOption.IGNORE_CASE
        )
        atMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // UPI received: "received in slice A/c ... from NASIMUDDIN ... via UPI (Ref ID: ...)"
        // UPI sent:     "sent from a/c ... to Hussain Shaikh (UPI Ref: ...)"
        // AutoPay paid: "paid Rs.1 from slice a/c ... to OpenAI LLC on 25-May-26 via UPI AutoPay"
        // Only applies to the Slice SFB account-to-account flows (they always carry
        // an a/c reference), so we don't hijack legacy "credited to your slice
        // account" style messages.
        if (Regex("""\ba/c\b""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            val payeeKeyword = if (lowerMessage.contains("received")) "from" else "to"
            val payeePattern = Regex(
                """\b$payeeKeyword\s+(.+?)(?:\s+on\b|\s+via\b|\s+is\b|\s*\(|\.\s|$)""",
                RegexOption.IGNORE_CASE
            )
            payeePattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Look for "sent to NAME" pattern for UPI transfers
        val sentToPattern = Regex("""sent.*to\s+([A-Z][A-Z0-9\s./&-]+?)\s*\(""", RegexOption.IGNORE_CASE)
        sentToPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) {
                return cleanMerchantName(merchant)
            }
        }

        // Look for "from MERCHANT" pattern
        val fromPattern =
            Regex("""from\s+([A-Z][A-Z0-9\s]+?)(?:\s+on|\s+\(|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && !merchant.equals("NEFT", ignoreCase = true)) {
                return cleanMerchantName(merchant)
            }
        }

        // Look for "on MERCHANT" pattern for credit card transactions
        val onPattern = Regex("""\bon\s+([A-Za-z0-9\s./&-]+?)(?:\s+is|$)""", RegexOption.IGNORE_CASE)
        onPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && 
                !merchant.equals("slice", ignoreCase = true) &&
                !merchant.equals("RS", ignoreCase = true) &&
                !isDatePhrase(merchant)) {
                return cleanMerchantName(merchant)
            }
        }

        // Check for specific patterns
        return when {
            lowerMessage.contains("paypal") -> "PayPal"
            lowerMessage.contains("slice") && lowerMessage.contains("credited") -> "Slice Credit"
            else -> super.extractMerchant(message, sender) ?: "Slice"
        }
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        // Slice SFB card-spend format:
        // "Your transaction of Rs. X at MERCHANT from a/c ... is successful"
        // carries an a/c reference (which the base detector treats as non-card),
        // so flag it explicitly. UPI flows use sent/received/paid wording instead.
        if (lower.contains("transaction of") &&
            Regex("""\bat\s""", RegexOption.IGNORE_CASE).containsMatchIn(message) &&
            !lower.contains("sent") &&
            !lower.contains("received") &&
            !lower.contains("paid")) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Slice SFB prints "Avl. Bal. Rs. 2,203.56" (dots after Avl/Bal break the
        // base [:\s]+ patterns), so handle that shape explicitly first.
        val sliceBal = Regex(
            """Avl\.?\s*Bal\.?\s*(?:Rs\.?|INR|₹)?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        sliceBal.find(message)?.let { match ->
            return try {
                BigDecimal(match.groupValues[1].replace(",", ""))
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractBalance(message)
    }

    override fun extractReference(message: String): String? {
        // Slice SFB UPI formats: "(UPI Ref: 616851070000)" and "(Ref ID: 212756500000)".
        val upiRef = Regex("""UPI\s+Ref(?:\s+ID)?[:\s]+([0-9]+)""", RegexOption.IGNORE_CASE)
        upiRef.find(message)?.let { return it.groupValues[1] }

        val refId = Regex("""Ref\s+ID[:\s]+([0-9]+)""", RegexOption.IGNORE_CASE)
        refId.find(message)?.let { return it.groupValues[1] }

        return super.extractReference(message)
    }

    /**
     * Returns true when the message clearly references a Slice credit-card product
     * (legacy, pre-2022 RBI PPI pivot). Modern Slice is a UPI / savings-account
     * product, so without explicit card context we treat debits as EXPENSE, not
     * CREDIT (which downstream is interpreted as "credit card account").
     */
    private fun hasCardContext(lowerMessage: String): Boolean {
        return lowerMessage.contains("credit card") ||
                lowerMessage.contains("credit limit") ||
                lowerMessage.contains("available limit") ||
                lowerMessage.contains("card ending") ||
                lowerMessage.contains("card xx") ||
                lowerMessage.contains("card no") ||
                lowerMessage.contains("on your slice card") ||
                lowerMessage.contains("slice card")
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        val cardContext = hasCardContext(lowerMessage)

        return when {
            // Slice credits/cashbacks (income side unchanged)
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("cashback") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            // Slice payments/debits.
            // After RBI's 2022 PPI guidelines, Slice pivoted from a credit-card
            // product to a UPI / savings-account product (Slice Bank). Debits
            // from the bank account must be EXPENSE so that downstream code
            // does not classify the account as a credit card. Only fall back
            // to CREDIT when the message explicitly mentions card context.
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("sent") -> TransactionType.EXPENSE  // UPI transfer
            lowerMessage.contains("spent") ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE
            lowerMessage.contains("paid") ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE
            lowerMessage.contains("payment") && !lowerMessage.contains("received") ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE
            // Only map bare "transaction" word to a type if it's a successful
            // transaction; default to EXPENSE unless clearly card-context.
            lowerMessage.contains("transaction") && !lowerMessage.contains("credited") &&
                isSuccessMessage(message) && !isFailureMessage(message) ->
                if (cardContext) TransactionType.CREDIT else TransactionType.EXPENSE

            else -> super.extractTransactionType(message)
        }
    }
}
