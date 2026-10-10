package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ICICI Bank savings-account statement (#874).
 *
 * The extracted text has no column positions, so each row comes out as loose
 * lines: description lines, the date (dd-MM-yyyy), maybe a mode/particulars
 * continuation, the amount, the running balance, then more description.
 * Rows are anchored on the date → amount → balance lines, and whether the amount
 * was a deposit or a withdrawal is read from how the balance moved, which is
 * exact where the DEPOSITS/WITHDRAWALS column itself is lost.
 */
class IciciBankPdfParser : PdfStatementParser {

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        return "icici" in lower && "particulars" in lower && "withdrawals" in lower && "deposits" in lower
    }

    override fun parse(text: String): List<ParsedTransaction> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = findRows(lines)
        var previousBalance = openingBalance(lines)
        return rows.mapIndexedNotNull { i, row ->
            val description = descriptionFor(rows, i, lines)
            val type = when {
                previousBalance != null && row.balance.compareTo(previousBalance!! - row.amount) == 0 -> TransactionType.EXPENSE
                previousBalance != null && row.balance.compareTo(previousBalance!! + row.amount) == 0 -> TransactionType.INCOME
                else -> guessType(description)
            }
            previousBalance = row.balance
            ParsedTransaction(
                amount = row.amount,
                type = type,
                merchant = merchantFrom(description),
                reference = REFERENCE.findAll(description.joinToString(" ")).lastOrNull()?.value,
                accountLast4 = null,
                balance = null,
                // Date and running balance lead the text: the import's duplicate hash is
                // built from it, and recurring rows (monthly FD interest) share the
                // same amount and particulars. The balance makes every row unique.
                smsBody = (listOf("${row.date.format(DATE_FORMAT)} · balance ${row.balance.toPlainString()}") + description)
                    .joinToString("\n"),
                sender = "ICICI PDF",
                timestamp = row.date.atTime(LocalTime.NOON).atZone(IST).toInstant().toEpochMilli(),
                bankName = "ICICI Bank"
            )
        }
    }

    /** A statement row: [dateLine] holds the date; amount and balance follow it. */
    private data class Row(val date: LocalDate, val amount: BigDecimal, val balance: BigDecimal, val dateLine: Int, val balanceLine: Int)

    private fun findRows(lines: List<String>): List<Row> {
        val rows = mutableListOf<Row>()
        var i = 0
        while (i < lines.size) {
            val date = parseDate(lines[i])
            if (date == null) { i++; continue }
            // Amount and balance are the next two amount-only lines, within a short reach
            // (a mode/particulars continuation can sit between the date and the amount).
            val amounts = (i + 1 until minOf(i + 1 + REACH, lines.size))
                .filter { AMOUNT.matches(lines[it]) && parseDate(lines[it]) == null }
                .take(2)
            if (amounts.size == 2 && lines.subList(i + 1, amounts[1]).none { parseDate(it) != null }) {
                rows += Row(date, money(lines[amounts[0]]), money(lines[amounts[1]]), i, amounts[1])
                i = amounts[1] + 1
            } else {
                i++
            }
        }
        return rows
    }

    /**
     * The row's text: its trailing lines after the previous row's balance, the
     * lines around its own date, and the lines after its balance up to where the
     * next row's description starts (a line opening with a known prefix).
     */
    private fun descriptionFor(rows: List<Row>, index: Int, lines: List<String>): List<String> {
        val row = rows[index]
        val leadStart = if (index == 0) headerEnd(lines, row.dateLine) else leadingStartAfter(rows[index - 1], row, lines)
        val leading = lines.subList(leadStart, row.dateLine)
        val middle = lines.subList(row.dateLine + 1, row.balanceLine).filterNot { AMOUNT.matches(it) }
        val trailingEnd = if (index + 1 < rows.size) leadingStartAfter(row, rows[index + 1], lines) else lines.size
        val trailing = lines.subList(row.balanceLine + 1, trailingEnd).takeWhile { !isNoise(it) }
        return (leading + middle + trailing).filterNot { isNoise(it) }
    }

    /** First line after [previous]'s balance that starts [next]'s description. */
    private fun leadingStartAfter(previous: Row, next: Row, lines: List<String>): Int {
        val from = previous.balanceLine + 1
        return (from until next.dateLine).firstOrNull { idx -> STARTS.any { lines[idx].startsWith(it, ignoreCase = true) } }
            ?: next.dateLine
    }

    /** Skip the column header (DATE … BALANCE) before the first row. */
    private fun headerEnd(lines: List<String>, firstDateLine: Int): Int {
        val header = (0 until firstDateLine).lastOrNull { lines[it].equals("BALANCE", ignoreCase = true) }
        val start = (header ?: -1) + 1
        return (start until firstDateLine).firstOrNull { idx -> STARTS.any { lines[idx].startsWith(it, ignoreCase = true) } }
            ?: firstDateLine
    }

    private fun openingBalance(lines: List<String>): BigDecimal? {
        val at = lines.indexOfFirst { OPENING.containsMatchIn(it) }
        if (at < 0) return null
        return (at until minOf(at + REACH, lines.size)).firstNotNullOfOrNull { idx ->
            AMOUNT_IN_LINE.findAll(lines[idx]).lastOrNull()?.value?.let(::money)
        }
    }

    private fun merchantFrom(description: List<String>): String? {
        description.firstNotNullOfOrNull { CHANNEL_PAYEE.find(it)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty) }
            ?.let { return it }
        if (description.any { it.startsWith("Int on", ignoreCase = true) || it.startsWith("Int.Pd", ignoreCase = true) }) {
            return "Interest"
        }
        return description.firstOrNull()?.take(MAX_MERCHANT)
    }

    /** Only for a row whose direction the balance can't settle (the first, with no opening balance). */
    private fun guessType(description: List<String>): TransactionType {
        val text = description.joinToString(" ").lowercase()
        return if (INCOME_HINTS.any { it in text }) TransactionType.INCOME else TransactionType.EXPENSE
    }

    private fun isNoise(line: String) = NOISE.any { it.containsMatchIn(line) }

    private fun parseDate(line: String): LocalDate? =
        if (DATE.matches(line)) runCatching { LocalDate.parse(line, DATE_FORMAT) }.getOrNull() else null

    private fun money(s: String) = BigDecimal(s.replace(",", ""))

    private companion object {
        val IST: ZoneId = ZoneId.of("Asia/Kolkata")
        val DATE = Regex("""\d{2}-\d{2}-\d{4}""")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val AMOUNT = Regex("""[\d,]+\.\d{2}""")
        val AMOUNT_IN_LINE = Regex("""\d[\d,]*\.\d{2}""")
        val REFERENCE = Regex("""(?<!\d)\d{12}(?!\d)""")
        val CHANNEL_PAYEE = Regex("""^(?:UPI|VIN|VSI|BIL|IMPS|NEFT|RTGS)/([^/]+)""", RegexOption.IGNORE_CASE)
        val OPENING = Regex("""\bB/F\b|opening balance""", RegexOption.IGNORE_CASE)
        // Lines that open a new row's description.
        val STARTS = listOf("UPI/", "VIN/", "VSI/", "BIL/", "IMPS/", "NEFT", "RTGS", "MMT/", "ACH/", "NFS/", "ATM/", "CMS/", "INF/", "Int on", "Int.Pd", "CLG/", "BY CASH", "TO CASH")
        val INCOME_HINTS = listOf("int on", "int.pd", "salary", "refund", "reversal", "cashback", "by cash")
        val NOISE = listOf(Regex("""^page \d+""", RegexOption.IGNORE_CASE), Regex("""^(DATE|MODE\**|PARTICULARS|DEPOSITS|WITHDRAWALS|BALANCE)$"""))
        const val REACH = 6
        const val MAX_MERCHANT = 40
    }
}
