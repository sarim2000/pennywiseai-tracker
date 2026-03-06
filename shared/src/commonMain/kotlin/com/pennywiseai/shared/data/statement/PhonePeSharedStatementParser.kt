package com.pennywiseai.shared.data.statement

import com.pennywiseai.shared.data.model.SharedTransactionType

class PhonePeSharedStatementParser : SharedStatementParser {
    companion object {
        private val phonepeKeywords = listOf("phonepe", "phone pe")
        // Anchored amount pattern: must start with ₹/Rs/Rs. and capture only one number.
        // Uses (?!\d) negative lookahead to stop at number boundaries when PDF text
        // concatenates columns (e.g. "395.0012345.00" from debit+balance columns).
        private val amountPattern = Regex("""(?:₹|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)(?!\d)""")
        private val txnPattern = Regex("""(?:Transaction\s+ID|UTR(?:\s+No)?)[:\s]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        // Merchant pattern: stops at Transaction ID, UTR, Paid by, or newline/end
        private val merchantPattern = Regex("""(?:Paid\s+to|Sent\s+to|Transferred\s+to|Received\s+from)\s+(.+?)(?:\s+Transaction|\s+UTR|\s+Paid\s+by|\n|$)""", RegexOption.IGNORE_CASE)
        // Date pattern: matches "Mar 02, 2026 08:49 pm" or "02 Mar, 2026 08:49 pm"
        // Uses [\s,]+ to handle PDFKit whitespace variations (newlines, tabs, extra spaces)
        private val datePattern = Regex("""((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\s,]+\d{1,2}[\s,]+\d{4}|\d{1,2}[\s,]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\s,]+\d{4})[\s]*(\d{1,2}:\d{2}[\s]*(?:am|pm|AM|PM))?""")
        // Date-only pattern for block splitting (lookahead to not consume text)
        // Uses [\s,]+ to handle PDFKit whitespace variations
        private val dateStartPattern = Regex("""(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\s,]+\d{1,2}[\s,]+\d{4}""")
        // Account last4 pattern: "Paid by XXXXXX0093" -> "0093"
        private val accountPattern = Regex("""(?:Paid\s+by|Received\s+by)\s+X+(\d{4})""", RegexOption.IGNORE_CASE)
        // Max reasonable transaction amount: ₹1 crore = 10,000,000.00 = 1_000_000_000 paise
        private const val MAX_AMOUNT_MINOR = 1_000_000_000L

        private val MONTH_MAP = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )
    }

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        return phonepeKeywords.any { it in lower }
    }

    override fun parse(text: String): List<SharedParsedStatementTransaction> {
        val blocks = splitBlocks(text)
        println("[PhonePe Parser] Found ${blocks.size} blocks")
        blocks.take(3).forEachIndexed { i, block ->
            println("[PhonePe Parser] Block #${i + 1} (first 200 chars): ${block.take(200)}")
        }
        return blocks.mapNotNull { parseBlock(it) }
    }

    private fun splitBlocks(text: String): List<String> {
        // Split on date patterns that start each transaction in PhonePe PDFs.
        // Each transaction begins with a date like "Mar 02, 2026".
        val matches = dateStartPattern.findAll(text).toList()
        if (matches.isEmpty()) return fallbackSplitBlocks(text)

        val blocks = mutableListOf<String>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val block = text.substring(start, end).trim()
            if (block.isNotEmpty()) blocks.add(block)
        }
        return blocks
    }

    // Fallback to the old splitting logic when no dates are found
    private fun fallbackSplitBlocks(text: String): List<String> {
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
        if (amountMinor <= 0 || amountMinor > MAX_AMOUNT_MINOR) return null
        val upper = block.uppercase()
        val type = when {
            upper.contains("DEBIT") || upper.contains("PAID TO") || upper.contains("SENT TO") || upper.contains("TRANSFERRED TO") -> SharedTransactionType.EXPENSE
            upper.contains("CREDIT") || upper.contains("RECEIVED FROM") -> SharedTransactionType.INCOME
            else -> return null
        }
        val timestamp = extractTimestamp(block) ?: fallbackTimestamp()
        val accountLast4 = accountPattern.find(block)?.groupValues?.getOrNull(1)
        return SharedParsedStatementTransaction(
            amountMinor = amountMinor,
            transactionType = type,
            merchant = merchantPattern.find(block)?.groupValues?.getOrNull(1)?.trim(),
            reference = txnPattern.find(block)?.groupValues?.getOrNull(1),
            accountLast4 = accountLast4,
            bankName = "UPI (PhonePe)",
            timestampEpochMillis = timestamp,
            rawText = block.trim()
        )
    }

    private fun extractTimestamp(block: String): Long? {
        val match = datePattern.find(block)
        if (match == null) {
            println("[PhonePe Parser] WARNING: No date found in block: ${block.take(100)}")
            return null
        }
        val dateStr = match.groupValues[1].trim()
        val timeStr = match.groupValues.getOrNull(2)?.trim()
        println("[PhonePe Parser] Parsed date='$dateStr' time='$timeStr'")
        val result = parseDateTime(dateStr, timeStr)
        println("[PhonePe Parser] Epoch millis = $result")
        return result
    }

    private fun parseDateTime(dateStr: String, timeStr: String?): Long? {
        // Parse date part: "Mar 02, 2026" or "02 Mar, 2026"
        // Handle PDFKit whitespace variations (newlines, tabs, multiple commas/spaces)
        val cleaned = dateStr.replace(",", " ").trim()
        val parts = cleaned.split(Regex("[\\s]+")).filter { it.isNotEmpty() }
        if (parts.size < 3) return null

        val month: Int
        val day: Int
        val year: Int

        val firstAsMonth = MONTH_MAP[parts[0].lowercase()]
        if (firstAsMonth != null) {
            // "Mar 02 2026"
            month = firstAsMonth
            day = parts[1].toIntOrNull() ?: return null
            year = parts[2].toIntOrNull() ?: return null
        } else {
            // "02 Mar 2026"
            day = parts[0].toIntOrNull() ?: return null
            month = MONTH_MAP[parts[1].lowercase()] ?: return null
            year = parts[2].toIntOrNull() ?: return null
        }

        // Parse time part: "08:49 pm" or "8:49 AM"
        var hour = 0
        var minute = 0
        if (!timeStr.isNullOrBlank()) {
            val timeCleaned = timeStr.trim().lowercase()
            val isPm = timeCleaned.contains("pm")
            val timeDigits = timeCleaned.replace(Regex("[^0-9:]"), "")
            val timeParts = timeDigits.split(":")
            if (timeParts.size >= 2) {
                hour = timeParts[0].toIntOrNull() ?: 0
                minute = timeParts[1].toIntOrNull() ?: 0
                if (isPm && hour < 12) hour += 12
                if (!isPm && hour == 12) hour = 0
            }
        }

        // Convert to epoch millis (UTC-based, IST = UTC+5:30)
        return dateToEpochMillis(year, month, day, hour, minute)
    }

    private fun dateToEpochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        // Days from Unix epoch (1970-01-01) to the given date
        // Using a simple algorithm to compute days since epoch
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 9 else month - 3
        val daysSinceEpoch = 365L * y + y / 4 - y / 100 + y / 400 + (m * 306 + 5) / 10 + (day - 1) - 719468L

        val timeMillis = (hour * 3600L + minute * 60L) * 1000L
        val istOffsetMillis = 5 * 3600_000L + 30 * 60_000L // IST = UTC+5:30

        return daysSinceEpoch * 86_400_000L + timeMillis - istOffsetMillis
    }
}
