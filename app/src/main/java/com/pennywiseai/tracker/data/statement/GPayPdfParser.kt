package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GPayPdfParser : PdfStatementParser {

    companion object {
        private val GPAY_KEYWORDS = listOf("gpay", "google pay")

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy h:mm a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }

        private val AMOUNT_PATTERN = Regex("""[₹Rs.]+\s*([\d,]+(?:\.\d{1,2})?)""")
        private val UPI_TXN_ID_PATTERN = Regex("""UPI\s+[Tt]ransaction\s+ID\s*[:\-]?\s*(\d+)""")
        private val PAID_TO_MERCHANT_PATTERN = Regex("""Paid\s+to\s+(.+?)(?:\n|$)""")
        private val RECEIVED_FROM_PATTERN = Regex("""Received\s+from\s+(.+?)(?:\n|$)""")
        private val PAID_BY_ACCOUNT_PATTERN = Regex("""Paid\s+by\s+(.+?)(?:\n|$)""")
        private val ACCOUNT_LAST4_PATTERN = Regex("""[Xx*]+(\d{4})""")
        private val DATE_PATTERN = Regex("""(\d{1,2}\s+\w{3}\s+\d{4}\s+\d{1,2}:\d{2}\s*[AaPp][Mm])""")
    }

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        return GPAY_KEYWORDS.any { it in lower } && lower.contains("upi transaction id")
    }

    override fun parse(text: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val blocks = splitIntoTransactionBlocks(text)

        for (block in blocks) {
            parseBlock(block)?.let { transactions.add(it) }
        }

        return transactions
    }

    private fun splitIntoTransactionBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        val lines = text.lines()
        var currentBlock = StringBuilder()
        var inTransaction = false

        for (line in lines) {
            val isPaidTo = line.trimStart().startsWith("Paid to", ignoreCase = true)
            val isReceivedFrom = line.trimStart().startsWith("Received from", ignoreCase = true)

            if (isPaidTo || isReceivedFrom) {
                if (inTransaction && currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock.toString())
                    currentBlock = StringBuilder()
                }
                inTransaction = true
            }

            if (inTransaction) {
                currentBlock.appendLine(line)
            }
        }

        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock.toString())
        }

        return blocks
    }

    private fun parseBlock(block: String): ParsedTransaction? {
        val amount = extractAmount(block) ?: return null
        val type = extractTransactionType(block) ?: return null
        val merchant = extractMerchant(block, type) ?: return null
        val reference = extractUpiTransactionId(block)
        val timestamp = extractTimestamp(block) ?: System.currentTimeMillis()
        val accountInfo = extractAccountInfo(block)

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant.trim(),
            reference = reference,
            accountLast4 = accountInfo.last4,
            balance = null,
            smsBody = block.trim(),
            sender = "GPay PDF",
            timestamp = timestamp,
            bankName = accountInfo.bankName ?: "Google Pay"
        )
    }

    private fun extractAmount(block: String): BigDecimal? {
        val match = AMOUNT_PATTERN.find(block) ?: return null
        val amountStr = match.groupValues[1].replace(",", "")
        return try {
            BigDecimal(amountStr)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun extractTransactionType(block: String): TransactionType? {
        return when {
            block.trimStart().startsWith("Paid to", ignoreCase = true) -> TransactionType.EXPENSE
            block.trimStart().startsWith("Received from", ignoreCase = true) -> TransactionType.INCOME
            else -> null
        }
    }

    private fun extractMerchant(block: String, type: TransactionType): String? {
        val pattern = if (type == TransactionType.EXPENSE) PAID_TO_MERCHANT_PATTERN else RECEIVED_FROM_PATTERN
        return pattern.find(block)?.groupValues?.get(1)?.trim()
    }

    private fun extractUpiTransactionId(block: String): String? {
        return UPI_TXN_ID_PATTERN.find(block)?.groupValues?.get(1)
    }

    private fun extractTimestamp(block: String): Long? {
        val match = DATE_PATTERN.find(block) ?: return null
        return try {
            synchronized(DATE_FORMAT) {
                DATE_FORMAT.parse(match.groupValues[1].trim())?.time
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAccountInfo(block: String): AccountInfo {
        val paidByMatch = PAID_BY_ACCOUNT_PATTERN.find(block)
        val paidByText = paidByMatch?.groupValues?.get(1) ?: ""

        val last4 = ACCOUNT_LAST4_PATTERN.find(paidByText)?.groupValues?.get(1)
        val bankName = paidByText
            .replace(ACCOUNT_LAST4_PATTERN, "")
            .replace(Regex("""[•\-–]"""), "")
            .trim()
            .takeIf { it.isNotEmpty() }

        return AccountInfo(bankName = bankName, last4 = last4)
    }

    private data class AccountInfo(val bankName: String?, val last4: String?)
}
