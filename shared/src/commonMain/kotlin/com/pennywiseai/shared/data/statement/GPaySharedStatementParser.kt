package com.pennywiseai.shared.data.statement

import com.pennywiseai.shared.data.model.SharedTransactionType

class GPaySharedStatementParser : SharedStatementParser {
    companion object {
        private val keywords = listOf("gpay", "google pay")
        // Anchored amount pattern: requires ₹/Rs prefix, captures exactly one number
        // with (?!\d) lookahead to prevent matching across concatenated PDF columns
        private val amountPattern = Regex("""(?:₹|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)(?!\d)""")
        private val upiPattern = Regex("""UPI\s+[Tt]ransaction\s+ID\s*[:\-]?\s*(\d+)""")
        private val paidToPattern = Regex("""Paid\s+to\s+(.+?)(?:\n|$)""")
        private val receivedFromPattern = Regex("""Received\s+from\s+(.+?)(?:\n|$)""")
        private val paidByPattern = Regex("""Paid\s+by\s+(.+?)(?:\n|$)""")
        private val accountLast4Pattern = Regex("""[Xx*]+(\d{4})""")
        // Max reasonable transaction amount: ₹1 crore = 10,000,000.00 = 1_000_000_000 paise
        private const val MAX_AMOUNT_MINOR = 1_000_000_000L
    }

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        return keywords.any { it in lower } && lower.contains("upi transaction id")
    }

    override fun parse(text: String): List<SharedParsedStatementTransaction> {
        return splitBlocks(text).mapNotNull { parseBlock(it) }
    }

    private fun splitBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        var current = StringBuilder()
        var inBlock = false
        text.lines().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("Paid to", true) || trimmed.startsWith("Received from", true)) {
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
        if (amountMinor <= 0 || amountMinor > MAX_AMOUNT_MINOR) return null
        val type = when {
            block.trimStart().startsWith("Paid to", true) -> SharedTransactionType.EXPENSE
            block.trimStart().startsWith("Received from", true) -> SharedTransactionType.INCOME
            else -> return null
        }
        val merchant = if (type == SharedTransactionType.EXPENSE) {
            paidToPattern.find(block)?.groupValues?.getOrNull(1)?.trim()
        } else {
            receivedFromPattern.find(block)?.groupValues?.getOrNull(1)?.trim()
        }
        val reference = upiPattern.find(block)?.groupValues?.getOrNull(1)
        val paidByText = paidByPattern.find(block)?.groupValues?.getOrNull(1).orEmpty()
        val accountLast4 = accountLast4Pattern.find(paidByText)?.groupValues?.getOrNull(1)
        val bankName = paidByText.replace(accountLast4Pattern, "").replace(Regex("""[•\-–]"""), "").trim().ifEmpty { "UPI (GPay)" }
        return SharedParsedStatementTransaction(
            amountMinor = amountMinor,
            transactionType = type,
            merchant = merchant,
            reference = reference,
            accountLast4 = accountLast4,
            bankName = bankName,
            timestampEpochMillis = fallbackTimestamp(),
            rawText = block.trim()
        )
    }
}
