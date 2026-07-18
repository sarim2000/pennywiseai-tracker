package com.pennywiseai.tracker.data.statement

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Parses Paytm UPI statement PDF exports into [ParsedTransaction] objects.
 *
 * PdfBox extracts each transaction as a vertical run of lines:
 * ```
 * 17 Jul                          <- date (day + month, no year)
 * 8:24 PM                         <- time on its own line
 * Money sent to Some Merchant     <- anchor (merchant may wrap to the next line)
 *  UPI ID: merchant@bank  on
 * UPI Ref No: 310604264196
 * Note: optional free text        <- may itself contain date-like lines
 *  Tag:
 * # Money Transfer
 * State Bank                      <- account, wraps across two lines
 * Of India - 17
 * - Rs.220                        <- signed amount; "+" = received
 * ```
 * A new block starts at a date line immediately followed by a time line —
 * date-like text inside a Note (e.g. "Note: Badminton / 28 Jun") is not
 * followed by a time line, so it never falsely starts a block.
 *
 * Statements can span two calendar years ("18 JUL'25 - 17 JUL'26") while
 * transaction dates carry no year, so the year is inferred from the statement
 * period: try the end year, and roll back one year if that lands after the
 * period end.
 *
 * "Money blocked/unblocked for" entries (IPO mandates) carry unsigned amounts
 * and are excluded from Paytm's own totals — they are skipped.
 */
class PaytmPdfParser : PdfStatementParser {

    companion object {
        private const val TAG = "PaytmPdfParser"
    }

    private val IST = TimeZone.getTimeZone("Asia/Kolkata")

    private val dateLineRegex = Regex("""^(\d{1,2})\s+([A-Za-z]{3})$""")
    private val timeLineRegex = Regex("""^(\d{1,2}:\d{2})\s*([AaPp][Mm])$""")
    private val signedAmountRegex = Regex("""^([+-])\s*Rs\.?\s*([\d,]+(?:\.\d{1,2})?)$""")

    // PdfBox occasionally merges table columns onto one line, e.g.
    // "Money sent to Someone  Tag: State Bank - Rs.2,187" — fallback for
    // amounts embedded at the end of such merged lines.
    private val inlineAmountRegex = Regex("""([+-])\s*Rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s*$""")
    private val upiRefRegex = Regex("""UPI\s+Ref\s+No[:\s]+(\d+)""", RegexOption.IGNORE_CASE)
    private val statementPeriodRegex = Regex(
        """(\d{1,2})\s+([A-Za-z]{3})'(\d{2})\s*-\s*(\d{1,2})\s+([A-Za-z]{3})'(\d{2})"""
    )
    private val accountLineRegex = Regex("""^(.+?)\s*-\s*(\d{1,4})$""")

    private val anchorPrefixes = listOf(
        "Cashback received from",
        "Money sent to",
        "Received from",
        "Paid to",
        "Paid for",
        "Recharge of",
        "Automatic payment for",
        "Automatic payment of",
        "Purchase of",
    )

    private val skipAnchorPrefixes = listOf(
        "Money blocked for",
        "Money unblocked for",
    )

    // Page furniture that repeats on every page and may interleave with a
    // transaction that spans a page break.
    private val headerPrefixes = listOf(
        "Page ",
        "For any queries",
        "Contact Us",
        "Passbook Payments History",
        "All payments done by you",
        "Date &",
        "Transaction Details",
    )

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        val result = "paytm" in lower && "upi ref no" in lower
        Log.d(TAG, "canHandle=$result")
        return result
    }

    override fun parse(text: String): List<ParsedTransaction> {
        Log.i(TAG, "Starting parse — text length=${text.length}")

        val period = extractStatementPeriod(text)
        val blocks = splitIntoBlocks(text)
        Log.i(TAG, "Split into ${blocks.size} blocks")

        val transactions = blocks.mapIndexedNotNull { index, block ->
            parseBlock(block, index, period)
        }

        Log.i(TAG, "Finished: ${transactions.size}/${blocks.size} transactions parsed")
        return transactions
    }

    // ─── Block splitting ──────────────────────────────────────────────────────

    private fun splitIntoBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var pendingDate: String? = null
        var inBlock = false

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (isHeaderLine(line)) continue

            // Date lines are held pending — they only start a block when the
            // very next content line is a time line. This keeps date-like text
            // inside Notes from starting a bogus block.
            if (dateLineRegex.matches(line)) {
                pendingDate = line
                continue
            }

            if (timeLineRegex.matches(line) && pendingDate != null) {
                if (inBlock && current.isNotEmpty()) {
                    blocks.add(current.toString().trim())
                    current.clear()
                }
                current.appendLine(pendingDate)
                current.appendLine(line)
                pendingDate = null
                inBlock = true
                continue
            }

            if (inBlock) current.appendLine(line)
        }

        if (current.isNotEmpty()) blocks.add(current.toString().trim())

        Log.d(TAG, "splitIntoBlocks: ${blocks.size} blocks found")
        return blocks
    }

    private fun isHeaderLine(line: String): Boolean {
        if (line == "Time") return true
        return headerPrefixes.any { line.startsWith(it, ignoreCase = true) }
    }

    // ─── Block parsing ────────────────────────────────────────────────────────

    private fun parseBlock(block: String, index: Int, period: StatementPeriod?): ParsedTransaction? {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 3) {
            Log.w(TAG, "Block[$index] — too short, skipping")
            return null
        }

        val dateLine = lines[0]
        val timeLine = lines[1]
        val anchorLine = lines[2]

        if (skipAnchorPrefixes.any { anchorLine.startsWith(it, ignoreCase = true) }) {
            Log.d(TAG, "Block[$index] — skipping blocked/unblocked mandate")
            return null
        }

        // Signed amount is required: "+" = money received, "-" = money paid.
        // Blocked/unblocked mandates only carry unsigned amounts, so this also
        // acts as a second guard against them. Standalone amount lines are the
        // norm; the inline fallback covers merged-column lines.
        val amountMatch = lines.firstNotNullOfOrNull { signedAmountRegex.find(it) }
            ?: lines.firstNotNullOfOrNull { inlineAmountRegex.find(it) }
        if (amountMatch == null) {
            Log.w(TAG, "Block[$index] — no signed amount found. Anchor: '$anchorLine'")
            return null
        }
        val type = if (amountMatch.groupValues[1] == "+") TransactionType.INCOME else TransactionType.EXPENSE
        val amount = amountMatch.groupValues[2].replace(",", "").toBigDecimalOrNull()
        if (amount == null) {
            Log.w(TAG, "Block[$index] — unparseable amount '${amountMatch.value}'")
            return null
        }

        val merchant = extractMerchant(lines)
        val upiRef = lines.firstNotNullOfOrNull { upiRefRegex.find(it)?.groupValues?.get(1) }
        val account = extractAccountInfo(lines)
        val timestamp = extractTimestamp(dateLine, timeLine, period)

        Log.i(
            TAG, "Block[$index] OK — $type merchant='$merchant' amount=$amount " +
                    "upi=$upiRef bank='${account.bankName}' last4=${account.last4}"
        )

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant,
            reference = upiRef,
            accountLast4 = account.last4,
            balance = null,
            smsBody = block,
            sender = "Paytm PDF",
            timestamp = timestamp ?: System.currentTimeMillis(),
            bankName = account.bankName ?: "Paytm",
        )
    }

    // ─── Field extractors ─────────────────────────────────────────────────────

    private fun extractMerchant(lines: List<String>): String {
        val anchorLine = lines[2]
        val prefix = anchorPrefixes.firstOrNull { anchorLine.startsWith(it, ignoreCase = true) }
        var merchant = if (prefix != null) anchorLine.substring(prefix.length).trim() else anchorLine

        // On merged-column lines the merchant is followed by other table cells
        // ("Someone  Tag: State Bank - Rs.2,187" / "Someone Note: 22 Feb") —
        // cut at the first inline field. A merged anchor already holds the
        // full merchant, so continuation lines must not be appended to it.
        val cutAt = Regex("""\s+(?:Note:|Tag:)|\s*[+-]\s*Rs\.""").find(merchant)
        if (cutAt != null) {
            return merchant.substring(0, cutAt.range.first).replace(Regex("""\s+"""), " ").trim()
        }

        // Merchant names wrap: "Money sent to Suraj Maheshchandra " / "Garg".
        // Continuation lines are anything before the first recognised field line.
        var i = 3
        while (i < lines.size && i <= 4 && isMerchantContinuation(lines[i])) {
            merchant = "$merchant ${lines[i]}"
            i++
        }

        return merchant.replace(Regex("""\s+"""), " ").trim()
    }

    private fun isMerchantContinuation(line: String): Boolean {
        if (line.startsWith("UPI ID", ignoreCase = true)) return false
        if (line.startsWith("UPI Ref", ignoreCase = true)) return false
        if (line.startsWith("Note:", ignoreCase = true)) return false
        if (line.startsWith("Tag:", ignoreCase = true)) return false
        if (line.startsWith("#")) return false
        if (line.startsWith("Order ID", ignoreCase = true)) return false
        if (line.startsWith("Rs.", ignoreCase = true)) return false
        if (signedAmountRegex.matches(line)) return false
        return true
    }

    /**
     * The account sits on the 1–2 lines directly above the signed amount line,
     * e.g. "State Bank " / "Of India - 17" → bank="State Bank Of India", last4="17".
     */
    private fun extractAccountInfo(lines: List<String>): AccountInfo {
        val amountIndex = lines.indexOfFirst { signedAmountRegex.matches(it) }
        if (amountIndex < 1) return AccountInfo(null, null)

        val candidates = lines.subList(maxOf(3, amountIndex - 2), amountIndex)
        if (candidates.isEmpty()) return AccountInfo(null, null)

        val joined = candidates.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        val match = accountLineRegex.find(joined) ?: return AccountInfo(null, null)

        val bankName = match.groupValues[1].trim().takeIf { it.isNotEmpty() }
        val last4 = match.groupValues[2].trim()
        return AccountInfo(bankName, last4)
    }

    // ─── Timestamp ────────────────────────────────────────────────────────────

    private data class StatementPeriod(val startYear: Int, val endYear: Int, val endEpoch: Long)

    private fun extractStatementPeriod(text: String): StatementPeriod? {
        val match = statementPeriodRegex.find(text) ?: return null
        val (startDay, startMon, startYy, endDay, endMon, endYy) = match.destructured
        val startYear = 2000 + (startYy.toIntOrNull() ?: return null)
        val endYear = 2000 + (endYy.toIntOrNull() ?: return null)

        val endEpoch = parseDateTime("$endDay ${normalizeMonth(endMon)} $endYear 11:59 PM") ?: return null
        Log.d(TAG, "Statement period: $startDay $startMon $startYear — $endDay $endMon $endYear")
        return StatementPeriod(startYear, endYear, endEpoch)
    }

    private fun extractTimestamp(dateLine: String, timeLine: String, period: StatementPeriod?): Long? {
        val dateMatch = dateLineRegex.find(dateLine) ?: return null
        val timeMatch = timeLineRegex.find(timeLine) ?: return null

        val day = dateMatch.groupValues[1]
        val month = normalizeMonth(dateMatch.groupValues[2])
        val time = "${timeMatch.groupValues[1]} ${timeMatch.groupValues[2].uppercase()}"

        val endYear = period?.endYear ?: Calendar.getInstance(IST).get(Calendar.YEAR)
        var timestamp = parseDateTime("$day $month $endYear $time") ?: return null

        // Statements can span the new year — dates after the period end belong
        // to the earlier year.
        if (period != null && timestamp > period.endEpoch) {
            timestamp = parseDateTime("$day $month ${period.startYear} $time") ?: return null
        }
        return timestamp
    }

    private fun parseDateTime(text: String): Long? {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.ENGLISH).apply {
                timeZone = IST
                isLenient = false
            }
            sdf.parse(text)?.time
        } catch (e: Exception) {
            Log.e(TAG, "Date parse failed — input='$text': ${e.message}")
            null
        }
    }

    private fun normalizeMonth(raw: String): String =
        raw.lowercase().replaceFirstChar { it.uppercase() }

    private data class AccountInfo(val bankName: String?, val last4: String?)
}
