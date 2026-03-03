package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PhonePePdfParser : PdfStatementParser {

    companion object {
        private val PHONEPE_KEYWORDS = listOf("phonepe", "phone pe")

        private val DATE_FORMATS = listOf(
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        ).onEach { it.timeZone = TimeZone.getTimeZone("Asia/Kolkata") }

        private val AMOUNT_PATTERN = Regex("""[₹Rs.]+\s*([\d,]+(?:\.\d{1,2})?)""")
        private val TXN_ID_PATTERN = Regex("""(?:Transaction\s+ID|UTR(?:\s+No)?)[:\s]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        private val DATE_PATTERN = Regex("""(\d{1,2}\s+\w{3}[,]?\s+\d{4}|\w{3}\s+\d{1,2}[,]?\s+\d{4})""")
        private val MERCHANT_PATTERN = Regex("""(?:Paid\s+to|Sent\s+to|Transferred\s+to|Received\s+from)\s+(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
    }

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        return PHONEPE_KEYWORDS.any { it in lower }
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
            val trimmed = line.trim()
            val isDebit = trimmed.equals("DEBIT", ignoreCase = true) ||
                    trimmed.startsWith("DEBIT ", ignoreCase = true)
            val isCredit = trimmed.equals("CREDIT", ignoreCase = true) ||
                    trimmed.startsWith("CREDIT ", ignoreCase = true)
            val isPaidTo = trimmed.startsWith("Paid to", ignoreCase = true) ||
                    trimmed.startsWith("Sent to", ignoreCase = true) ||
                    trimmed.startsWith("Transferred to", ignoreCase = true)
            val isReceivedFrom = trimmed.startsWith("Received from", ignoreCase = true)

            if (isDebit || isCredit || isPaidTo || isReceivedFrom) {
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
        val merchant = extractMerchant(block)
        val reference = extractTransactionId(block)
        val timestamp = extractTimestamp(block) ?: System.currentTimeMillis()

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant?.trim(),
            reference = reference,
            accountLast4 = null,
            balance = null,
            smsBody = block.trim(),
            sender = "PhonePe PDF",
            timestamp = timestamp,
            bankName = "PhonePe"
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
        val firstLine = block.lines().firstOrNull()?.trim()?.uppercase() ?: return null
        return when {
            firstLine.startsWith("DEBIT") -> TransactionType.EXPENSE
            firstLine.startsWith("CREDIT") -> TransactionType.INCOME
            firstLine.startsWith("PAID TO", ignoreCase = true) ||
                    firstLine.startsWith("SENT TO", ignoreCase = true) ||
                    firstLine.startsWith("TRANSFERRED TO", ignoreCase = true) -> TransactionType.EXPENSE
            firstLine.startsWith("RECEIVED FROM", ignoreCase = true) -> TransactionType.INCOME
            block.contains("DEBIT", ignoreCase = true) -> TransactionType.EXPENSE
            block.contains("CREDIT", ignoreCase = true) -> TransactionType.INCOME
            else -> null
        }
    }

    private fun extractMerchant(block: String): String? {
        val match = MERCHANT_PATTERN.find(block)
        if (match != null) return match.groupValues[1].trim()

        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2) {
            val secondLine = lines[1]
            if (!secondLine.matches(Regex("""^[₹Rs.\d,]+.*""")) &&
                !secondLine.startsWith("Transaction", ignoreCase = true) &&
                !secondLine.startsWith("UTR", ignoreCase = true)
            ) {
                return secondLine
            }
        }

        return null
    }

    private fun extractTransactionId(block: String): String? {
        return TXN_ID_PATTERN.find(block)?.groupValues?.get(1)
    }

    private fun extractTimestamp(block: String): Long? {
        val match = DATE_PATTERN.find(block) ?: return null
        val dateStr = match.groupValues[1].trim()

        for (format in DATE_FORMATS) {
            try {
                synchronized(format) {
                    return format.parse(dateStr)?.time
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
