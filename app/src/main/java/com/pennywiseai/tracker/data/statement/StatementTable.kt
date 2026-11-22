package com.pennywiseai.tracker.data.statement

import com.opencsv.CSVReaderBuilder
import com.opencsv.RFC4180ParserBuilder
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.math.BigDecimal
import java.math.MathContext
import java.text.ParsePosition
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How a bank's spreadsheet statement maps onto transactions (#13): which row
 * holds the column names and which column is which. Saved per header signature
 * (DataStore, see [StatementTableParser.encodeMappings]) so the next statement
 * from the same bank imports in one tap.
 *
 * Every field has a default, so a mapping saved by an older version still
 * decodes after fields are added.
 */
@Serializable
data class StatementColumnMapping(
    val headerRow: Int = 0,
    val dateColumn: Int = NONE,
    /** One of [StatementTableParser.DATE_FORMATS]; null = detect from the column. */
    val dateFormat: String? = null,
    val descriptionColumn: Int = NONE,
    /** Single signed amount (with an optional Dr/Cr [typeColumn])… */
    val amountColumn: Int = NONE,
    val typeColumn: Int = NONE,
    /** …or separate debit and credit columns. */
    val debitColumn: Int = NONE,
    val creditColumn: Int = NONE,
    val balanceColumn: Int = NONE,
    /** Cheque/UTR/reference number; a UPI/IMPS/NEFT reference in the narration is used when absent. */
    val referenceColumn: Int = NONE,
    /** The account the statement belongs to (bank name + last 4), if linked. */
    val accountBankName: String? = null,
    val accountLast4: String? = null
) {
    val usesDebitCredit: Boolean get() = debitColumn != NONE || creditColumn != NONE
    val isComplete: Boolean get() = dateColumn != NONE && (amountColumn != NONE || usesDebitCredit)

    companion object {
        const val NONE = -1
    }
}

/** Account the imported rows are booked on; [currency] tags every amount. */
data class StatementAccount(val bankName: String, val last4: String?, val currency: String)

/** A cell's amount; [negative] is null when nothing (sign, Dr/Cr, brackets) marked it. */
data class CellAmount(val value: BigDecimal, val negative: Boolean?)

data class TableParseResult(val transactions: List<ParsedTransaction>, val skippedRows: Int)

/** Pure: spreadsheet rows + a [StatementColumnMapping] → transactions. */
object StatementTableParser {

    /** Formats offered in the picker, in auto-detect priority (day-first before month-first). */
    val DATE_FORMATS = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "dd/MM/yy", "dd-MM-yy",
        "dd-MMM-yy", "dd-MMM-yyyy", "dd MMM yyyy", "dd MMM yy", "dd/MMM/yyyy",
        "yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy", "MMM dd, yyyy"
    )

    /** Pseudo-format: the Date column holds Excel day numbers (see [detectDateFormat]). */
    const val EXCEL_SERIAL = "excel-serial"
    private val YEARS = 1990..2100

    const val STATEMENT_SENDER = "STATEMENT"
    private const val HEADER_SCAN_ROWS = 50
    private const val MAX_MERCHANT = 60
    private val REFERENCE = Regex("""(?<!\d)\d{12}(?!\d)""")
    // "UPI/612345678901/…", "IMPS-612345678901-…", "NEFT/N289/…": the token after the channel.
    private val CHANNEL_REFERENCE = Regex("""^(?:UPI|IMPS|NEFT|RTGS)[/\-: ]+([A-Za-z0-9]*\d[A-Za-z0-9]{5,})""", RegexOption.IGNORE_CASE)
    private val REFERENCE_TOKEN = Regex("""[A-Za-z0-9]{6,32}""")
    private val DIRECTION_HEADERS = listOf("dr/cr", "cr/dr", "dr / cr", "cr / dr", "debit/credit", "credit/debit")
    private val WHITESPACE = Regex("""\s+""")
    private val CHANNELS = setOf("UPI", "IMPS", "NEFT", "RTGS", "POS", "ACH", "NACH", "BIL", "VIN", "MMT", "CMS", "INF")
    private val IFSC = Regex("""[A-Z]{4}0[A-Z0-9]{6}""")
    private val LEADING_MINUS = Regex("""^[^\d]*-""")
    // Exponent bounded to two digits: "1e1000000000" must not expand into a billion-digit number.
    private val PLAIN_NUMBER = Regex("""[+-]?\d{1,20}(\.\d{1,20})?([eE][+-]?\d{1,2})?""")
    private val EXPONENT = Regex("""\d[eE][+-]?\d""")
    /** Larger than any statement amount (≈ 10 trillion); anything beyond is a misread cell. */
    private const val MAX_INTEGER_DIGITS = 13
    private val json = Json { ignoreUnknownKeys = true }

    // ── Reading ──────────────────────────────────────────────────────────────

    class LegacyXlsException : IllegalArgumentException("Legacy .xls isn't supported. Save it as .xlsx or .csv and import that.")

    /** .xlsx (zip) or CSV text, told apart by content, not by file name. */
    fun readTable(bytes: ByteArray): List<List<String>> = when {
        bytes.startsWith(0x50, 0x4B, 0x03, 0x04) -> XlsxReader.readRows(ByteArrayInputStream(bytes))
        bytes.startsWith(0xD0, 0xCF, 0x11, 0xE0) -> throw LegacyXlsException()
        else -> readCsv(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
    }

    fun readCsv(text: String): List<List<String>> {
        val separator = detectSeparator(text.lineSequence().take(20).joinToString("\n"))
        // RFC 4180: quotes are escaped by doubling. No backslash escapes, so a
        // narration ending "C:\" before its closing quote doesn't swallow the row.
        val reader = CSVReaderBuilder(StringReader(text))
            .withCSVParser(RFC4180ParserBuilder().withSeparator(separator).build())
            .build()
        val rows = reader.use { r ->
            generateSequence { r.readNext() }.take(XlsxReader.MAX_ROWS + 1).map { it.toList() }.toList()
        }
        if (rows.size > XlsxReader.MAX_ROWS) throw TooManyRowsException()
        return rows
    }

    /** Comma, semicolon or tab: whichever appears most outside quoted fields. */
    internal fun detectSeparator(sample: String): Char {
        val counts = IntArray(3)
        var quoted = false
        for (ch in sample) {
            when {
                ch == '"' -> quoted = !quoted
                quoted -> Unit
                ch == ',' -> counts[0]++
                ch == ';' -> counts[1]++
                ch == '\t' -> counts[2]++
            }
        }
        return ",;\t"[counts.indices.maxBy { counts[it] }]
    }

    /** Refused rather than silently cut: a partial statement would import as if complete. */
    class TooManyRowsException :
        IllegalArgumentException("This statement has more than ${XlsxReader.MAX_ROWS} rows. Export a shorter period and import that.")

    private fun ByteArray.startsWith(vararg magic: Int) =
        size >= magic.size && magic.indices.all { this[it] == magic[it].toByte() }

    // ── Header + mapping ────────────────────────────────────────────────────

    /**
     * Normalized header text, the key a saved mapping is found by. Empty cells
     * keep their place: mappings are column positions, so "Date,,Debit" must not
     * reuse the mapping saved for "Date,Debit".
     */
    fun signature(row: List<String>): String =
        row.map { it.trim().lowercase().replace(WHITESPACE, " ") }.dropLastWhile { it.isEmpty() }.joinToString("|")

    /** A saved mapping whose header row appears in [rows] (any position), with that row's index. */
    fun findSavedMapping(rows: List<List<String>>, saved: Map<String, StatementColumnMapping>): StatementColumnMapping? {
        if (saved.isEmpty()) return null
        for (i in 0 until minOf(HEADER_SCAN_ROWS, rows.size)) {
            val sig = signature(rows[i])
            if (sig.isNotEmpty()) saved[sig]?.let { return it.copy(headerRow = i) }
        }
        return null
    }

    /** The text above the header row: where a statement prints its bank, account and period. */
    fun preamble(rows: List<List<String>>, headerRow: Int): String =
        rows.take(headerRow).joinToString("\n") { row -> row.filter { it.isNotBlank() }.joinToString(" ") }

    /**
     * Whether [preamble] names the account ending [last4]: right after a mask
     * ("XXXXXX4321", "XXXX XXXX XXXX 4321", "••4321"), or as the number after
     * an account or card label ("A/c No: 4321", "Account No: ...4321"). Any
     * other number that merely ends in the digits (a customer ID, a balance, a
     * PIN code, a year) doesn't count.
     */
    fun namesAccount(preamble: String, last4: String): Boolean {
        if (last4.length < 3 || last4.any { !it.isDigit() }) return false
        val digits = Regex.escape(last4)
        return Regex("""(?<![A-Za-z])[Xx*•][Xx*• ]*+$digits(?!\d)""").containsMatchIn(preamble) ||
            Regex("""(?i)(?:a/c|acc(?:oun)?t|acct|card)(?: ?(?:no|number|num)\b| ending(?: in)?)?\.?[ :#-]*[Xx*•.\d -]*?$digits(?!\d)""")
                .containsMatchIn(preamble)
    }

    /** First row that names a date column among 3+ filled cells; else the widest early row. */
    fun guessHeaderRow(rows: List<List<String>>): Int {
        val scan = rows.take(HEADER_SCAN_ROWS)
        scan.indexOfFirst { row ->
            row.count { it.isNotBlank() } >= 3 && row.any { "date" in it.lowercase() }
        }.takeIf { it >= 0 }?.let { return it }
        return scan.indices.maxByOrNull { scan[it].count { c -> c.isNotBlank() } } ?: 0
    }

    /** Best guess from the header names; the user confirms or fixes it. */
    fun guessMapping(rows: List<List<String>>, headerRow: Int = guessHeaderRow(rows)): StatementColumnMapping {
        val header = rows.getOrNull(headerRow).orEmpty().map { it.trim().lowercase() }
        fun find(vararg keys: String, exact: Set<String> = emptySet()): Int =
            keys.firstNotNullOfOrNull { k -> header.indexOfFirst { k in it }.takeIf { it >= 0 } }
                ?: header.indexOfFirst { it in exact }.takeIf { it >= 0 }
                ?: StatementColumnMapping.NONE

        val debit = find("debit", "withdrawal", "paid out", exact = setOf("dr"))
        val credit = find("credit", "deposit", "paid in", exact = setOf("cr"))
        // Two different columns: one "Debit/Credit" header is a direction column, not both amounts.
        val separate = debit != StatementColumnMapping.NONE && credit != StatementColumnMapping.NONE && debit != credit
        return StatementColumnMapping(
            headerRow = headerRow,
            dateColumn = find("transaction date", "txn date", "tran date", "date"),
            descriptionColumn = find("narration", "description", "particulars", "details", "remarks"),
            amountColumn = if (separate) StatementColumnMapping.NONE else find("amount", "amt"),
            typeColumn = if (separate) StatementColumnMapping.NONE else guessTypeColumn(rows, headerRow, header),
            debitColumn = if (separate) debit else StatementColumnMapping.NONE,
            creditColumn = if (separate) credit else StatementColumnMapping.NONE,
            balanceColumn = find("balance"),
            referenceColumn = find("utr", "ref no", "ref.", "reference", "chq", "cheque", "ref")
        )
    }

    /**
     * A Dr/Cr column: named so ("Dr/Cr"), or a "Type" column whose cells really
     * are Dr/Cr words. A "Transaction Type" of Card/UPI/NEFT is a channel, not a direction.
     */
    private fun guessTypeColumn(rows: List<List<String>>, headerRow: Int, header: List<String>): Int {
        header.indexOfFirst { h -> DIRECTION_HEADERS.any { it in h } }.takeIf { it >= 0 }?.let { return it }
        val typed = header.indexOfFirst { "type" in it }.takeIf { it >= 0 } ?: return StatementColumnMapping.NONE
        val cells = rows.drop(headerRow + 1).map { it.cell(typed) }.filter { it.isNotBlank() }.take(200)
        return if (cells.isNotEmpty() && cells.count { directionWord(it) != null } * 10 >= cells.size * 9) typed
        else StatementColumnMapping.NONE
    }

    /** Debit (true) / credit (false) for an exact Dr/Cr word, else null. */
    internal fun directionWord(raw: String): Boolean? =
        when (raw.trim().lowercase().replace(".", "")) {
            "dr", "d", "debit", "withdrawal" -> true
            "cr", "c", "credit", "deposit" -> false
            else -> null
        }

    fun encodeMappings(mappings: Map<String, StatementColumnMapping>): String = json.encodeToString(mappings)

    fun decodeMappings(raw: String?): Map<String, StatementColumnMapping> =
        raw?.let { runCatching { json.decodeFromString<Map<String, StatementColumnMapping>>(it) }.getOrNull() }.orEmpty()

    // ── Cells ────────────────────────────────────────────────────────────────

    /**
     * "1,23,456.78", "₹ 500.00 Cr", "(250.00)", "-99", "1.234,56" → amount and
     * its direction marker. Null for a blank or non-numeric cell.
     */
    fun parseAmount(raw: String?): CellAmount? {
        var s = raw?.trim()?.replace(' ', ' ').orEmpty()
        if (s.isEmpty()) return null
        // A bare number, as Excel stores it (possibly "1.5E+3"): read it exactly.
        if (PLAIN_NUMBER.matches(s)) {
            val n = BigDecimal(s)
            if (n.precision() - n.scale() > MAX_INTEGER_DIGITS) return null
            // Excel stores formula results as doubles ("24521.000000000004"): drop the
            // binary noise at 15 significant digits. Never round to paise: OMR and
            // JOD amounts have three decimals.
            val money = n.round(MathContext(15)).stripTrailingZeros().let { if (it.scale() < 2) it.setScale(2) else it }
            return CellAmount(money.abs(), if (n.signum() < 0) true else if (s.startsWith("+")) false else null)
        }
        // Exponent notation outside those bounds isn't an amount we can trust.
        if (EXPONENT.containsMatchIn(s)) return null
        var negative: Boolean? = null
        val lower = s.lowercase().trimEnd('.')
        when {
            lower.endsWith("dr") -> { negative = true; s = s.substring(0, lower.length - 2) }
            lower.endsWith("cr") -> { negative = false; s = s.substring(0, lower.length - 2) }
        }
        s = s.trim()
        if (s.startsWith("(") && s.endsWith(")")) negative = true
        if (LEADING_MINUS.containsMatchIn(s) || s.endsWith("-")) negative = true
        if (s.startsWith("+")) negative = false
        // Only between the first and last digit, so "Rs." can't lend its dot.
        var digits = s.dropWhile { !it.isDigit() }.dropLastWhile { !it.isDigit() }
            .filter { it.isDigit() || it == '.' || it == ',' }
        if (digits.none { it.isDigit() }) return null
        val lastComma = digits.lastIndexOf(',')
        val lastDot = digits.lastIndexOf('.')
        digits = if (lastComma > lastDot && digits.length - lastComma - 1 in 1..2) {
            // Decimal comma ("1.234,56" / "12,5"): Indian grouping never ends in a 1–2 digit group.
            digits.replace(".", "").replace(',', '.')
        } else {
            digits.replace(",", "")
        }
        val value = digits.toBigDecimalOrNull() ?: return null
        if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) return null
        return CellAmount(value, negative)
    }

    // Concurrent: a superseded mapping parse can still be running beside the new one.
    private val formatterCache = java.util.concurrent.ConcurrentHashMap<String, DateTimeFormatter>()

    private fun formatter(pattern: String): DateTimeFormatter = formatterCache.getOrPut(pattern) {
        // Accept 1-digit days/months ("5/1/2025") and any-case month names ("05-JAN-25").
        val lenient = pattern.replace("dd", "d").replace(Regex("""(?<!M)MM(?!M)"""), "M")
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(lenient).toFormatter(Locale.ENGLISH)
    }

    /**
     * The date in [raw], read with [pattern] (a time after the date is fine).
     * With [EXCEL_SERIAL] the cell is an Excel day number, and only then: a
     * stray number in a text-date column (a summary row's count) is not a date.
     */
    fun parseDate(raw: String?, pattern: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || pattern == null) return null
        if (pattern == EXCEL_SERIAL) {
            // Range-checked before any date arithmetic: a reference number picked as
            // the Date column would otherwise overflow plusDays and throw.
            val serial = text.toDoubleOrNull()?.takeIf { it in SERIAL_RANGE } ?: return null
            // Excel's 1900 date system; day 0 is 1899-12-30 (it counts the fake 1900-02-29).
            return EXCEL_EPOCH.plusDays(serial.toLong())
        }
        val position = ParsePosition(0)
        val parsed = runCatching { formatter(pattern).parse(text, position) }.getOrNull() ?: return null
        if (position.errorIndex >= 0) return null
        val rest = text.substring(position.index)
        // "05/01/2025" must not read as dd/MM/yy + leftover "25".
        if (rest.isNotEmpty() && !rest[0].isWhitespace() && rest[0] != 'T' && rest[0] != ',') return null
        return runCatching { LocalDate.from(parsed) }.getOrNull()?.takeIf { it.year in YEARS }
    }

    /**
     * How [cells] hold their dates: [EXCEL_SERIAL] when most are bare numbers
     * (an .xlsx date column), else the text format that reads the most of them
     * (earlier formats win ties).
     */
    fun detectDateFormat(cells: List<String>): String? {
        val filled = cells.filter { it.isNotBlank() }.take(200)
        if (filled.isEmpty()) return null
        if (filled.count { it.trim().toDoubleOrNull() != null } * 2 > filled.size) return EXCEL_SERIAL
        val sample = filled.filter { it.trim().toDoubleOrNull() == null }
        return DATE_FORMATS
            .map { f -> f to sample.count { parseDate(it, f) != null } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
    }

    // ── Rows → transactions ─────────────────────────────────────────────────

    /** The date format [mapping] reads with: the chosen one, or the detected one. */
    fun effectiveDateFormat(rows: List<List<String>>, mapping: StatementColumnMapping): String? =
        mapping.dateFormat ?: detectDateFormat(rows.drop(mapping.headerRow + 1).map { it.cell(mapping.dateColumn) })

    /**
     * Turns the rows below the header into transactions. Rows without a
     * readable date or a non-zero amount (blank lines, "Opening balance",
     * page footers, totals) are skipped and counted.
     */
    fun parse(rows: List<List<String>>, mapping: StatementColumnMapping, account: StatementAccount): TableParseResult {
        val format = effectiveDateFormat(rows, mapping)
        val seen = HashMap<String, Int>()
        var skipped = 0
        val parsed = rows.drop(mapping.headerRow + 1).mapNotNull { row ->
            if (row.all { it.isBlank() }) return@mapNotNull null
            val txn = parseRow(row, mapping, format, account, seen)
            if (txn == null) skipped++
            txn
        }
        // A date more than a year away from every other row is a misread cell (a
        // footer's number, a year typo), not a transaction — it would also move the
        // closing row. Quiet stretches are fine: only isolation counts, not density.
        // Only a few strays beside a clear main body are dropped; when isolated
        // rows are anything like half the file there's no main body, so keep all.
        val dates = parsed.map { StatementBalanceCheck.dateOf(it) }
        val isolated = (if (dates.size < 3) emptySet() else isolatedDates(dates.sorted()))
            .takeIf { set -> dates.count { it in set } * 4 < dates.size }
            .orEmpty()
        val transactions = parsed.filterIndexed { i, _ -> dates[i] !in isolated }
        return TableParseResult(transactions, skipped + parsed.size - transactions.size)
    }

    /**
     * The dates in [sorted] whose nearest other row is over a year away, in one
     * walk. A date shared by two rows has a same-day neighbour, so it's never isolated.
     */
    private fun isolatedDates(sorted: List<LocalDate>): Set<LocalDate> {
        fun farApart(a: LocalDate?, b: LocalDate?) = a == null || b == null || ChronoUnit.DAYS.between(a, b) > ISOLATION_DAYS
        return sorted.indices
            .filter { i -> farApart(sorted.getOrNull(i - 1), sorted[i]) && farApart(sorted[i], sorted.getOrNull(i + 1)) }
            .mapTo(HashSet()) { sorted[it] }
    }

    private fun parseRow(
        row: List<String>,
        mapping: StatementColumnMapping,
        format: String?,
        account: StatementAccount,
        seen: MutableMap<String, Int>
    ): ParsedTransaction? {
        val date = parseDate(row.cell(mapping.dateColumn), format) ?: return null
        val (amount, type) = amountAndType(row, mapping) ?: return null
        val balance = parseAmount(row.cell(mapping.balanceColumn))?.let { if (it.negative == true) it.value.negate() else it.value }
        val description = row.cell(mapping.descriptionColumn).trim().replace(WHITESPACE, " ")

        // The duplicate hash is built from this text. Identical rows (two ₹20 teas
        // on one day) get an occurrence number, so both import and a re-import of
        // the same file still matches each to itself.
        val direction = if (type == TransactionType.INCOME) "Cr" else "Dr"
        val key = listOf(date, description, amount.toPlainString(), direction, balance?.toPlainString(), account.bankName, account.last4)
            .joinToString("|")
        val occurrence = seen.merge(key, 1, Int::plus)!! - 1
        val body = buildString {
            append("$date · $description · $direction ${amount.toPlainString()}")
            balance?.let { append(" · balance ${it.toPlainString()}") }
            append(" · ${account.bankName}${account.last4?.let { " $it" }.orEmpty()}")
            if (occurrence > 0) append(" · #${occurrence + 1}")
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchantFrom(description),
            reference = referenceFrom(row.cell(mapping.referenceColumn), description),
            accountLast4 = account.last4,
            balance = balance,
            smsBody = body,
            sender = STATEMENT_SENDER,
            timestamp = date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            bankName = account.bankName,
            currency = account.currency
        )
    }

    /**
     * The payee in a channel-prefixed narration ("UPI/612345678901/GROCERY MART/okaxis"
     * → "GROCERY MART"); any other narration is used as it is.
     */
    internal fun merchantFrom(description: String): String? {
        val parts = description.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val payee = if (parts.size >= 2 && parts[0].uppercase() in CHANNELS) {
            parts.drop(1).firstOrNull { p -> p.any(Char::isLetter) && '@' !in p && !IFSC.matches(p) }
        } else {
            null
        }
        return (payee ?: description).take(MAX_MERCHANT).ifBlank { null }
    }

    /**
     * The row's payment reference: the Reference column when it holds one, else
     * the reference in a channel narration ("UPI/612345678901/…", "IMPS-…",
     * "NEFT/N289/…"), else a bare 12-digit UPI reference anywhere in it.
     */
    internal fun referenceFrom(referenceCell: String, description: String): String? {
        referenceCell.trim().takeIf { REFERENCE_TOKEN.matches(it) && it.any(Char::isDigit) }?.let { return it }
        CHANNEL_REFERENCE.find(description)?.let { return it.groupValues[1] }
        return REFERENCE.findAll(description).lastOrNull()?.value
    }

    private fun amountAndType(row: List<String>, mapping: StatementColumnMapping): Pair<BigDecimal, TransactionType>? {
        if (mapping.usesDebitCredit) {
            val debit = parseAmount(row.cell(mapping.debitColumn))?.value?.abs()
            val credit = parseAmount(row.cell(mapping.creditColumn))?.value?.abs()
            return when {
                debit != null && debit.signum() > 0 -> debit to TransactionType.EXPENSE
                credit != null && credit.signum() > 0 -> credit to TransactionType.INCOME
                else -> null
            }
        }
        val cell = parseAmount(row.cell(mapping.amountColumn)) ?: return null
        if (cell.value.signum() == 0) return null
        // Only an exact Dr/Cr word overrides the sign; "Card" or "UPI" says nothing about direction.
        val debit = directionWord(row.cell(mapping.typeColumn)) ?: (cell.negative == true)
        val type = if (debit) TransactionType.EXPENSE else TransactionType.INCOME
        return cell.value.abs() to type
    }

    private fun List<String>.cell(index: Int): String = if (index < 0) "" else getOrNull(index).orEmpty()

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    /** Day numbers of 1990-01-01 … 2100-12-31 ([YEARS]) in Excel's 1900 system. */
    private val SERIAL_RANGE: ClosedFloatingPointRange<Double> =
        ChronoUnit.DAYS.between(EXCEL_EPOCH, LocalDate.of(YEARS.first, 1, 1)).toDouble()..
            ChronoUnit.DAYS.between(EXCEL_EPOCH, LocalDate.of(YEARS.last, 12, 31)).toDouble()

    private const val ISOLATION_DAYS = 366L
}
