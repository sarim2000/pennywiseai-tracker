package com.pennywiseai.tracker.data.statement

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses Google Pay PDF statement exports into [ParsedTransaction] objects.
 * For income transactions the anchor is "Received from ..." and the account
 * line reads "Paid to South Indian Bank 1234" — which is NOT a new transaction.
 * We distinguish transaction anchors from account lines by checking that a
 * "Paid to" line does NOT end with 4 digits (which would make it a bank line).
 */
class GPayPdfParser : PdfStatementParser {

    // ─── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG                 = "GPayPdfParser"
        private const val DATE_FORMAT_PATTERN = "dd MMM, yyyy hh:mm a"
        private const val DATE_BUFFER_SIZE    = 4   // lines buffered before anchor
    }

    private val IST = TimeZone.getTimeZone("Asia/Kolkata")

    // ─── Patterns ─────────────────────────────────────────────────────────────

    // Matches "Paid to <merchant>" — but NOT "Paid to South Indian Bank 1234"
    // (account lines always end with 4 digits, merchant names never do)
    private val merchantAnchorRegex = Regex(
        """^Paid\s+to\s+(.+)$""", RegexOption.IGNORE_CASE
    )
    private val receivedAnchorRegex = Regex(
        """^Received\s+from\s+(.+)$""", RegexOption.IGNORE_CASE
    )
    // Account line: "Paid by Bank 1234" (expense) or "Paid to Bank 1234" (income)
    // Identified by ending with exactly 4 digits
    private val accountLineRegex = Regex(
        """^Paid\s+(?:by|to)\s+(.+\D)(\d{4})$""", RegexOption.IGNORE_CASE
    )
    // Stricter version for anchor detection: only matches if the name before
    // the 4 digits ends with a known account keyword (Bank, Card, A/c).
    // This prevents "Paid to Store 2024" from being misclassified as an account line.
    private val bankAccountLineRegex = Regex(
        """^Paid\s+(?:by|to)\s+(.+)\s+(Bank|Card|A/c)\s+(\d{4})$""", RegexOption.IGNORE_CASE
    )
    private val upiIdRegex = Regex(
        """UPI\s+Transaction\s+ID[:\s]+(\d+)""", RegexOption.IGNORE_CASE
    )
    // ₹1,234.56 or Rs. 100 — amount is always on its own line
    private val amountRegex = Regex("""^[₹Rs.\s]+([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""")

    // Date parts — each on its own line
    private val dateLineRegex = Regex("""^(\d{1,2})\s+(\w{3}),?$""")       // "01 Sep,"
    private val yearLineRegex = Regex("""^(20\d{2})$""")                    // "2025"
    private val timeLineRegex = Regex("""^(\d{1,2}:\d{2})\s*([AaPp][Mm])$""") // "03:02 PM"

    // ─── Public API ───────────────────────────────────────────────────────────

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        val result = ("google pay" in lower || "gpay" in lower) && "upi transaction id" in lower
        Log.d(TAG, "canHandle=$result")
        return result
    }

    override fun parse(text: String): List<ParsedTransaction> {
        Log.i(TAG, "Starting parse — text length=${text.length}")

        val blocks = splitIntoBlocks(text)
        Log.i(TAG, "Split into ${blocks.size} blocks")

        val transactions = blocks.mapIndexedNotNull { index, block ->
            parseBlock(block, index)
        }

        Log.i(TAG, "Finished: ${transactions.size}/${blocks.size} transactions parsed")
        return transactions
    }

    // ─── Block splitting ──────────────────────────────────────────────────────

    /**
     * Splits raw PDF text into one string per transaction.
     *
     * The challenge: date lines appear on the 3 lines BEFORE the transaction
     * anchor. We keep a rolling buffer of recent pre-anchor lines and prepend
     * them to the block when an anchor is detected.
     *
     * An anchor is "Paid to <X>" where X does NOT end with 4 digits, or
     * "Received from <X>". This excludes "Paid to South Indian Bank 1234"
     * (the account line in income transactions) from being treated as anchors.
     */
    private fun splitIntoBlocks(text: String): List<String> {
        val blocks  = mutableListOf<String>()
        val buffer  = ArrayDeque<String>()   // pre-anchor lines (date, year, time)
        val current = StringBuilder()
        var inBlock = false

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (isTransactionAnchor(line)) {
                if (inBlock && current.isNotEmpty()) {
                    blocks.add(current.toString().trim())
                    current.clear()
                }
                // Reset inBlock so lines before the next anchor accumulate in
                // the buffer again (date, year, time lines precede every anchor).
                inBlock = false
                buffer.forEach { current.appendLine(it) }
                buffer.clear()
                inBlock = true
            }

            if (inBlock) {
                current.appendLine(line)
            } else {
                buffer.addLast(line)
                if (buffer.size > DATE_BUFFER_SIZE) buffer.removeFirst()
            }
        }

        if (current.isNotEmpty()) blocks.add(current.toString().trim())

        Log.d(TAG, "splitIntoBlocks: ${blocks.size} blocks found")
        return blocks
    }

    /**
     * Returns true only for genuine transaction anchors.
     * Account lines like "Paid to South Indian Bank 1234" are excluded by
     * matching against [bankAccountLineRegex] which requires a bank/card keyword.
     * This prevents "Paid to Store 2024" from being misclassified.
     */
    private fun isTransactionAnchor(line: String): Boolean {
        if (receivedAnchorRegex.matches(line)) return true
        if (merchantAnchorRegex.matches(line)) {
            return !bankAccountLineRegex.matches(line)
        }
        return false
    }

    // ─── Block parsing ────────────────────────────────────────────────────────

    private fun parseBlock(block: String, index: Int): ParsedTransaction? {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }

        Log.v(TAG, "Block[$index] lines: $lines")

        // Find the anchor line (may not be the first line due to prepended date lines)
        val anchorLine = lines.firstOrNull { isTransactionAnchor(it) }
        if (anchorLine == null) {
            Log.w(TAG, "Block[$index] — no anchor found, skipping. Preview: ${lines.take(3)}")
            return null
        }

        val isExpense = merchantAnchorRegex.matches(anchorLine)
        val type      = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
        val merchant  = extractMerchant(anchorLine, isExpense)

        if (merchant == null) {
            Log.w(TAG, "Block[$index] — could not extract merchant from: '$anchorLine'")
            return null
        }

        val amount = extractAmount(lines)
        if (amount == null) {
            Log.w(TAG, "Block[$index] merchant='$merchant' — no amount found. Lines: $lines")
            return null
        }

        val timestamp = extractTimestamp(lines, merchant)
        val upiId     = lines.firstNotNullOfOrNull { upiIdRegex.find(it)?.groupValues?.get(1) }
        val account   = extractAccountInfo(lines)

        Log.i(TAG, "Block[$index] OK — $type merchant='$merchant' amount=$amount " +
                "upi=$upiId bank='${account.bankName}' last4=${account.last4}")

        return ParsedTransaction(
            amount       = amount,
            type         = type,
            merchant     = merchant,
            reference    = upiId,
            accountLast4 = account.last4,
            balance      = null,
            smsBody      = block,
            sender       = "GPay PDF",
            timestamp    = timestamp ?: System.currentTimeMillis(),
            bankName     = account.bankName ?: "Google Pay",
        )
    }

    // ─── Field extractors ─────────────────────────────────────────────────────

    private fun extractMerchant(anchorLine: String, isExpense: Boolean): String? {
        val regex = if (isExpense) merchantAnchorRegex else receivedAnchorRegex
        return regex.find(anchorLine)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractAmount(lines: List<String>): BigDecimal? {
        for (line in lines) {
            val raw = amountRegex.find(line)?.groupValues?.get(1) ?: continue
            val cleaned = raw.replace(",", "")
            val amount  = cleaned.toBigDecimalOrNull()
            if (amount != null) return amount
        }
        return null
    }

    /**
     * Assembles timestamp from three separate lines:
     *   "01 Sep,"  → day=1, month=Sep
     *   "2025"     → year
     *   "03:02 PM" → time
     *
     * Builds the string "01 Sep, 2025 03:02 PM" and parses it.
     */
    private fun extractTimestamp(lines: List<String>, merchant: String): Long? {
        var dateLine: String? = null
        var yearLine: String? = null
        var timeLine: String? = null

        for (line in lines) {
            when {
                dateLine == null && dateLineRegex.matches(line) -> dateLine = line
                yearLine == null && yearLineRegex.matches(line) -> yearLine = line
                timeLine == null && timeLineRegex.matches(line) -> timeLine = line
            }
            if (dateLine != null && yearLine != null && timeLine != null) break
        }

        if (dateLine == null || yearLine == null || timeLine == null) {
            Log.e(TAG, "Incomplete timestamp for '$merchant' — " +
                    "date='$dateLine' year='$yearLine' time='$timeLine' | lines=$lines")
            return null
        }

        // Normalise: "01 Sep," → "01 Sep" then build "01 Sep, 2025 03:02 PM"
        val dateClean = dateLine.trimEnd(',', ' ')
        val timeClean = timeLine.replace(Regex("""\s+"""), " ").uppercase()
        val combined  = "$dateClean, $yearLine $timeClean"

        return try {
            val sdf = SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.ENGLISH).apply {
                timeZone = IST
                isLenient = false
            }
            val parsed = sdf.parse(combined)
            if (parsed == null) {
                Log.e(TAG, "Date parsed to null for '$merchant' — input='$combined'")
                null
            } else {
                Log.d(TAG, "Timestamp OK for '$merchant' — '$combined' → ${parsed.time}")
                parsed.time
            }
        } catch (e: Exception) {
            Log.e(TAG, "Date parse exception for '$merchant' — input='$combined': ${e.message}")
            null
        }
    }

    /**
     * Extracts bank name and last 4 digits from the account line.
     * Expense: "Paid by South Indian Bank 1234"
     * Income:  "Paid to South Indian Bank 1234"
     * Both are matched by [accountLineRegex].
     */
    private fun extractAccountInfo(lines: List<String>): AccountInfo {
        val accountLine = lines.firstOrNull { accountLineRegex.matches(it) }
        if (accountLine == null) {
            Log.d(TAG, "No account line found in lines: $lines")
            return AccountInfo(null, null)
        }

        val match    = accountLineRegex.find(accountLine)
        val bankName = match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val last4    = match?.groupValues?.get(2)?.trim()

        Log.d(TAG, "Account — bank='$bankName' last4='$last4' from '$accountLine'")
        return AccountInfo(bankName, last4)
    }

    // ─── Data classes ─────────────────────────────────────────────────────────

    private data class AccountInfo(val bankName: String?, val last4: String?)
}