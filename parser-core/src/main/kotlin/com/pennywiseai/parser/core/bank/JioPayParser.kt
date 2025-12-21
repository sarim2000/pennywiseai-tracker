package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for JioPay wallet transactions.
 * Handles messages from JA-JioPay-S and similar senders.
 *
 * Note: Wallet transactions are marked as CREDIT to avoid double-counting
 * (money already counted when loading wallet from bank account)
 */
class JioPayParser : BankParser() {

    override fun getBankName() = "JioPay"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("JIOPAY") ||
                normalizedSender.endsWith("-JIOPAY-S") ||
                normalizedSender.endsWith("-JIOPAY-T") ||
                normalizedSender == "JM-JIOPAY"
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Pattern 1: "Plan Name : 249.00"
        val planPattern = Regex(
            """Plan\s+Name\s*:\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        planPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Pattern 2: "Rs. 249.00" or "Rs 249"
        val rsPattern = Regex(
            """Rs\.?\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        rsPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        // Fall back to base class patterns
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        return when {
            // Jio Recharge
            lowerMessage.contains("recharge successful") && lowerMessage.contains("jio number") -> {
                // Extract the phone number for reference
                val numberPattern =
                    Regex("""Jio\s+Number\s*:\s*(\d{10})""", RegexOption.IGNORE_CASE)
                val number = numberPattern.find(message)?.groupValues?.get(1) ?: ""
                if (number.isNotEmpty()) {
                    "Jio Recharge - ${number.take(4)}****"
                } else {
                    "Jio Recharge"
                }
            }

            // Bill payment patterns
            lowerMessage.contains("bill payment") -> {
                when {
                    lowerMessage.contains("electricity") -> "Electricity Bill"
                    lowerMessage.contains("water") -> "Water Bill"
                    lowerMessage.contains("gas") -> "Gas Bill"
                    lowerMessage.contains("broadband") -> "Broadband Bill"
                    lowerMessage.contains("dth") -> "DTH Recharge"
                    else -> "Bill Payment"
                }
            }

            // Other recharges
            lowerMessage.contains("recharge") -> {
                when {
                    lowerMessage.contains("mobile") -> "Mobile Recharge"
                    lowerMessage.contains("dth") -> "DTH Recharge"
                    lowerMessage.contains("data") -> "Data Recharge"
                    else -> "Recharge"
                }
            }

            // Payment to merchant
            lowerMessage.contains("payment successful to") -> {
                val toPattern =
                    Regex("""payment\s+successful\s+to\s+([^.\n]+)""", RegexOption.IGNORE_CASE)
                toPattern.find(message)?.let { match ->
                    return cleanMerchantName(match.groupValues[1].trim())
                }
                "JioPay Payment"
            }

            else -> super.extractMerchant(message, sender) ?: "JioPay Transaction"
        }
    }

    override fun extractReference(message: String): String? {
        // Pattern: "Transaction ID : BR000CAUBYON"
        val txnPattern = Regex(
            """Transaction\s+ID\s*:\s*([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        txnPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun extractTransactionType(message: String): TransactionType {
        // All JioPay wallet transactions are marked as CREDIT
        // to avoid double-counting (money was already debited when loading wallet)
        return TransactionType.CREDIT
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Reject bill notifications and reminders (not actual transactions)
        if (lowerMessage.contains("e-bill") ||
            lowerMessage.contains("bill has been sent") ||
            lowerMessage.contains("bill summary") ||
            lowerMessage.contains("payment due date") ||
            lowerMessage.contains("amount payable")
        ) {
            return false
        }

        // JioPay messages don't use standard transaction keywords
        // but "recharge successful" indicates a transaction
        return lowerMessage.contains("recharge successful") ||
                super.isTransactionMessage(message)
    }
}