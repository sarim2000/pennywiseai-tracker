package com.pennywiseai.shared.data.statement

import com.pennywiseai.shared.data.model.SharedTransactionType

class PhonePeSharedStatementParser : SharedStatementParser {
    companion object {
        private val phonepeKeywords = listOf("phonepe", "phone pe")
        private val amountPattern = Regex("""[₹Rs.]+\s*([\d,]+(?:\.\d{1,2})?)""")
        private val txnPattern = Regex("""(?:Transaction\s+ID|UTR(?:\s+No)?)[:\s]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        private val merchantPattern = Regex("""(?:Paid\s+to|Sent\s+to|Transferred\s+to|Received\s+from)\s+(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
    }

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        return phonepeKeywords.any { it in lower }
    }

    override fun parse(text: String): List<SharedParsedStatementTransaction> {
        return splitBlocks(text).mapNotNull { parseBlock(it) }
    }

    private fun splitBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        var current = StringBuilder()
        var inBlock = false
        text.lines().forEach { line ->
            val trimmed = line.trim()
            val starts = trimmed.equals("DEBIT", true) || trimmed.equals("CREDIT", true) ||
                trimmed.startsWith("Paid to", true) || trimmed.startsWith("Received from", true) ||
                trimmed.startsWith("Sent to", true) || trimmed.startsWith("Transferred to", true)
            if (starts) {
                if (inBlock && current.isNotEmpty()) {
                    blocks.add(current.toString())
                    current = StringBuilder()
                }
                inBlock = true
            }
            if (inBlock) current.appendLine(line)
        }
        if (current.isNotEmpty()) blocks.add(current.toString())
        return blocks
    }

    private fun parseBlock(block: String): SharedParsedStatementTransaction? {
        val amountMinor = amountPattern.find(block)?.groupValues?.getOrNull(1)?.let(::amountToMinor) ?: return null
        val upper = block.uppercase()
        val type = when {
            upper.contains("DEBIT") || upper.contains("PAID TO") || upper.contains("SENT TO") || upper.contains("TRANSFERRED TO") -> SharedTransactionType.EXPENSE
            upper.contains("CREDIT") || upper.contains("RECEIVED FROM") -> SharedTransactionType.INCOME
            else -> return null
        }
        return SharedParsedStatementTransaction(
            amountMinor = amountMinor,
            transactionType = type,
            merchant = merchantPattern.find(block)?.groupValues?.getOrNull(1)?.trim(),
            reference = txnPattern.find(block)?.groupValues?.getOrNull(1),
            accountLast4 = null,
            bankName = "PhonePe",
            timestampEpochMillis = fallbackTimestamp(),
            rawText = block.trim()
        )
    }
}
