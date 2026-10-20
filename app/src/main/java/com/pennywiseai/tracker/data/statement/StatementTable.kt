package com.pennywiseai.tracker.data.statement

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.math.BigDecimal
import java.text.ParsePosition
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
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

    const val STATEMENT_SENDER = "STATEMENT"
    private const val HEADER_SCAN_ROWS = 50
    private const val MAX_MERCHANT = 60
    private val REFERENCE = Regex("""(?<!\d)\d{12}(?!\d)""")
    private val WHITESPACE = Regex("""\s+""")
    private val LEADING_MINUS = Regex("""^[^\d]*-""")
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
        val sample = text.lineSequence().take(20).joinToString("\n")
        val separator = listOf(',', ';', '\t').maxBy { sep -> sample.count { it == sep } }
        val reader = CSVReaderBuilder(StringReader(text))
            .withCSVParser(CSVParserBuilder().withSeparator(separator).build())
            .build()
        return reader.use { r ->
            generateSequence { r.readNext() }.take(XlsxReader.MAX_ROWS).map { it.toList() }.toList()
        }
    }

    private fun ByteArray.startsWith(vararg magic: Int) =
        size >= magic.size && magic.indices.all { this[it] == magic[it].toByte() }

    // ── Header + mapping ────────────────────────────────────────────────────

    /** Normalized header text, the key a saved mapping is found by. */
    fun signature(row: List<String>): String =
        row.map { it.trim().lowercase().replace(WHITESPACE, " ") }.filter { it.isNotEmpty() }.joinToString("|")

    /** A saved mapping whose header row appears in [rows] (any position), with that row's index. */
    fun findSavedMapping(rows: List<List<String>>, saved: Map<String, StatementColumnMapping>): StatementColumnMapping? {
        if (saved.isEmpty()) return null
        for (i in 0 until minOf(HEADER_SCAN_ROWS, rows.size)) {
            val sig = signature(rows[i])
            if (sig.isNotEmpty()) saved[sig]?.let { return it.copy(headerRow = i) }
        }
        return null
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
        val separate = debit != StatementColumnMapping.NONE && credit != StatementColumnMapping.NONE
        return StatementColumnMapping(
            headerRow = headerRow,
            dateColumn = find("transaction date", "txn date", "tran date", "date"),
            descriptionColumn = find("narration", "description", "particulars", "details", "remarks"),
            amountColumn = if (separate) StatementColumnMapping.NONE else find("amount", "amt"),
            typeColumn = if (separate) StatementColumnMapping.NONE else find("dr/cr", "cr/dr", "type"),
            debitColumn = if (separate) debit else StatementColumnMapping.NONE,
            creditColumn = if (separate) credit else StatementColumnMapping.NONE,
            balanceColumn = find("balance")
        )
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
        return CellAmount(value, negative)
    }

    private val formatterCache = HashMap<String, DateTimeFormatter>()

    private fun formatter(pattern: String): DateTimeFormatter = formatterCache.getOrPut(pattern) {
        // Accept 1-digit days/months ("5/1/2025") and any-case month names ("05-JAN-25").
        val lenient = pattern.replace("dd", "d").replace(Regex("""(?<!M)MM(?!M)"""), "M")
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(lenient).toFormatter(Locale.ENGLISH)
    }

    /**
     * The date in [raw], read with [pattern] (a time after the date is fine), or
     * as an Excel day serial when the cell is a bare number. Null otherwise.
     */
    fun parseDate(raw: String?, pattern: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        text.toDoubleOrNull()?.let { serial ->
            // Excel's 1900 date system; day 0 is 1899-12-30 (it counts the fake 1900-02-29).
            return if (serial in 20_000.0..80_000.0) EXCEL_EPOCH.plusDays(serial.toLong()) else null
        }
        if (pattern == null) return null
        val position = ParsePosition(0)
        val parsed = runCatching { formatter(pattern).parse(text, position) }.getOrNull() ?: return null
        if (position.errorIndex >= 0) return null
        val rest = text.substring(position.index)
        // "05/01/2025" must not read as dd/MM/yy + leftover "25".
        if (rest.isNotEmpty() && !rest[0].isWhitespace() && rest[0] != 'T' && rest[0] != ',') return null
        return runCatching { LocalDate.from(parsed) }.getOrNull()?.takeIf { it.year in 1990..2100 }
    }

    /** The format that reads the most of [cells]; earlier formats win ties. */
    fun detectDateFormat(cells: List<String>): String? {
        val sample = cells.filter { it.isNotBlank() && it.trim().toDoubleOrNull() == null }.take(200)
        if (sample.isEmpty()) return null
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
        val transactions = rows.drop(mapping.headerRow + 1).mapNotNull { row ->
            if (row.all { it.isBlank() }) return@mapNotNull null
            val txn = parseRow(row, mapping, format, account, seen)
            if (txn == null) skipped++
            txn
        }
        return TableParseResult(transactions, skipped)
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
            merchant = description.take(MAX_MERCHANT).ifBlank { null },
            reference = REFERENCE.findAll(description).lastOrNull()?.value,
            accountLast4 = account.last4,
            balance = balance,
            smsBody = body,
            sender = STATEMENT_SENDER,
            timestamp = date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            bankName = account.bankName,
            currency = account.currency
        )
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
        val typeText = row.cell(mapping.typeColumn).trim().lowercase()
        val type = when {
            typeText.startsWith("d") || "withdraw" in typeText -> TransactionType.EXPENSE
            typeText.startsWith("c") || "deposit" in typeText -> TransactionType.INCOME
            cell.negative == true -> TransactionType.EXPENSE
            else -> TransactionType.INCOME
        }
        return cell.value.abs() to type
    }

    private fun List<String>.cell(index: Int): String = if (index < 0) "" else getOrNull(index).orEmpty()

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
}
