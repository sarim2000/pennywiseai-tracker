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
 * We tell them apart by row structure: the account line follows the row's
 * "UPI Transaction ID" line (see [splitIntoBlocks]).
 */
class GPayPdfParser : PdfStatementParser {

    // ─── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG                 = "GPayPdfParser"
        private const val DATE_FORMAT_PATTERN = "dd MMM, yyyy hh:mm a"
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
    // Account line shape: "Paid by/to <name> 1234". The shape alone can't tell it
    // from a payee ("Paid to CARD SHOP 2024"), so the row structure decides: it
    // is the account only after the row's anchor and UPI ID line (#1100).
    private val bankAccountLineRegex = Regex(
        """^Paid\s+(?:by|to)\s+(.+?)\s+(\d{4})$""", RegexOption.IGNORE_CASE
    )
    private val upiIdRegex = Regex(
        """UPI\s+Transaction\s+ID[:\s]+(\d+)""", RegexOption.IGNORE_CASE
    )
    // Amount on its own line — currency prefix (₹, Rs.) required; bare numbers
    // like the year line ("2025") are intentionally excluded by the [₹Rs.\s]+ prefix.
    private val amountRegex = Regex("""^(?:₹|Rs\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""")

    // Date parts — each on its own line
    private val dateLineRegex = Regex("""^(\d{1,2})\s+(\w{3}),?$""")       // "01 Sep,"
    private val fullDateLineRegex = Regex("""^(\d{1,2}\s+\w{3},\s+20\d{2})$""") // "15 Oct, 2025"
    private val datePrefixedRowRegex = Regex("""^(\d{1,2}\s+\w{3},\s+20\d{2})\s+(.+)$""")
    private val yearLineRegex = Regex("""^(20\d{2})$""")                    // "2025"
    private val timeLineRegex = Regex("""^(\d{1,2}:\d{2})\s*([AaPp][Mm])(?:\s+(.+))?$""") // "03:02 PM ..."

    // ─── Public API ───────────────────────────────────────────────────────────

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        val result = ("google pay" in lower || "gpay" in lower) && "upi transaction id" in lower
        Log.d(TAG, "canHandle=$result")
        return result
    }

    override fun parse(text: String): List<ParsedTransaction> {
        Log.i(TAG, "Starting parse — text length=${text.length}")

        // PDF text can carry no-break spaces (e.g. U+202F before "AM") that
        // `\s` doesn't match; without this every date fell back to now (#1100).
        val blocks = splitIntoBlocks(text.replace(Regex("""\p{Zs}"""), " "))
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
     * The challenge: date lines (`"01 Sep,"`, `"2025"`, `"03:02 PM"`) appear on
     * the lines BEFORE the transaction anchor in GPay exports. We keep a rolling
     * "pending" triplet of the most-recent date/year/time lines seen, and each
     * time a new anchor arrives we prepend that triplet to the new block before
     * consuming it. Date-pattern lines are never appended to the *current* block
     * because they belong to the *next* transaction — otherwise N+1's date lines
     * would bleed into N's block and confuse `extractTimestamp`.
     *
     * An anchor is "Paid to <X>" or "Received from <X>". The account line of a
     * received row ("Paid to South Indian Bank 1234") is told apart by its place
     * in the row (after the UPI ID line), not by its wording.
     */
    private fun splitIntoBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var pendingDate: String? = null
        var pendingYear: String? = null
        var pendingTime: String? = null
        var inBlock = false
        var awaitingAccount = false

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val isDate = dateLineRegex.matches(line)
            val isFullDate = fullDateLineRegex.matches(line)
            val isYear = yearLineRegex.matches(line)
            val isTime = timeLineRegex.matches(line)

            val datePrefixedRow = datePrefixedRowRegex.find(line)
            if (datePrefixedRow != null) {
                val fullDate = datePrefixedRow.groupValues[1]
                val rest = datePrefixedRow.groupValues[2].trim()
                val rowParts = splitAnchorAndAmount(rest)
                if (isTransactionAnchor(rowParts.anchorLine)) {
                    awaitingAccount = false
                    if (inBlock && current.isNotEmpty()) {
                        blocks.add(current.toString().trim())
                        current.clear()
                    }
                    current.appendLine(fullDate)
                    current.appendLine(rowParts.anchorLine)
                    rowParts.amountLine?.let { current.appendLine(it) }
                    pendingDate = null
                    pendingYear = null
                    pendingTime = null
                    inBlock = true
                    continue
                }
            }

            // The row's account line ("Paid to State Bank of India 1234" on a
            // received row) follows its UPI ID line. A "Paid to" line anywhere
            // else, or once a new date has started the next row, is a payee.
            if (awaitingAccount && pendingDate == null && bankAccountLineRegex.matches(line)) {
                current.appendLine(line)
                awaitingAccount = false
                continue
            }

            if (isTransactionAnchor(line)) {
                awaitingAccount = false
                if (inBlock && current.isNotEmpty()) {
                    blocks.add(current.toString().trim())
                    current.clear()
                }
                // Prepend the most-recent pending date triplet so this block
                // owns its own dates — then clear pending so the next transaction
                // starts fresh.
                pendingDate?.let { current.appendLine(it) }
                pendingYear?.let { current.appendLine(it) }
                pendingTime?.let { current.appendLine(it) }
                pendingDate = null
                pendingYear = null
                pendingTime = null
                current.appendLine(line)
                inBlock = true
                continue
            }

            // Date-pattern lines are NEVER appended to the current block — they
            // always belong to the next anchor's transaction. Track the most
            // recent triplet so the next anchor can consume it.
            if (isDate) { pendingDate = line; continue }
            if (isFullDate) { pendingDate = line; pendingYear = null; continue }
            if (isYear) { pendingYear = line; continue }
            if (isTime && inBlock && !timeLineRegex.find(line)?.groupValues?.getOrNull(3).isNullOrBlank()) {
                current.appendLine(line)
                if (upiIdRegex.containsMatchIn(line)) awaitingAccount = true
                continue
            }
            if (isTime) { pendingTime = line; continue }

            if (inBlock) {
                current.appendLine(line)
                if (upiIdRegex.containsMatchIn(line)) awaitingAccount = true
            }
        }

        if (current.isNotEmpty()) blocks.add(current.toString().trim())

        Log.d(TAG, "splitIntoBlocks: ${blocks.size} blocks found")
        return blocks
    }

    private fun isTransactionAnchor(line: String): Boolean =
        receivedAnchorRegex.matches(line) || merchantAnchorRegex.matches(line)

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
            Log.w(TAG, "Block[$index] — could not extract merchant")
            return null
        }

        val amount = extractAmount(lines)
        if (amount == null) {
            Log.w(TAG, "Block[$index] — no amount found")
            return null
        }

        val timestamp = extractTimestamp(lines, merchant)
        val upiId     = lines.firstNotNullOfOrNull { upiIdRegex.find(it)?.groupValues?.get(1) }
        // Only after the anchor: the anchor itself can look like an account ("Paid to CARD SHOP 2024").
        val account   = extractAccountInfo(lines.drop(lines.indexOf(anchorLine) + 1))

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
                dateLine == null && (dateLineRegex.matches(line) || fullDateLineRegex.matches(line)) -> {
                    dateLine = line
                }
                yearLine == null && yearLineRegex.matches(line) -> yearLine = line
                timeLine == null && timeLineRegex.matches(line) -> {
                    val match = timeLineRegex.find(line)
                    timeLine = listOfNotNull(
                        match?.groupValues?.getOrNull(1),
                        match?.groupValues?.getOrNull(2)
                    ).joinToString(" ")
                }
            }
            if (dateLine != null && (yearLine != null || fullDateLineRegex.matches(dateLine)) && timeLine != null) break
        }

        if (dateLine == null || timeLine == null || (yearLine == null && !fullDateLineRegex.matches(dateLine))) {
            Log.e(TAG, "Incomplete timestamp for '$merchant' — " +
                "date='$dateLine' year='$yearLine' time='$timeLine' | lines=$lines")
            return null
        }

        // Normalise: "01 Sep," → "01 Sep" then build "01 Sep, 2025 03:02 PM"
        val dateClean = dateLine.trimEnd(',', ' ')
        val timeClean = timeLine.replace(Regex("""\s+"""), " ").uppercase()
        val combined = if (fullDateLineRegex.matches(dateLine)) {
            "$dateClean $timeClean"
        } else {
            "$dateClean, $yearLine $timeClean"
        }

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

    private fun splitAnchorAndAmount(value: String): RowParts {
        val amountMatch = Regex("""\s+((?:₹|Rs\.?)\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?)$""")
            .find(value)
            ?: return RowParts(value.trim(), null)

        val anchorLine = value.substring(0, amountMatch.range.first).trim()
        return RowParts(anchorLine, amountMatch.groupValues[1].trim())
    }

    /**
     * Extracts bank name and last 4 digits from the account line.
     * Expense: "Paid by South Indian Bank 1234"
     * Income:  "Paid to South Indian Bank 1234"
     */
    private fun extractAccountInfo(lines: List<String>): AccountInfo {
        val accountLine = lines.firstOrNull { bankAccountLineRegex.matches(it) }
        if (accountLine == null) {
            Log.d(TAG, "No account line found in lines: $lines")
            return AccountInfo(null, null)
        }

        val match    = bankAccountLineRegex.find(accountLine)
        val bankName = match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val last4    = match?.groupValues?.getOrNull(2)?.trim()

        Log.d(TAG, "Account — bank='$bankName' last4='$last4' from '$accountLine'")
        return AccountInfo(bankName, last4)
    }

    // ─── Data classes ─────────────────────────────────────────────────────────

    private data class AccountInfo(val bankName: String?, val last4: String?)
    private data class RowParts(val anchorLine: String, val amountLine: String?)
}
