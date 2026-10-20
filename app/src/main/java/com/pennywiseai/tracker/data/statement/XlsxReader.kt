package com.pennywiseai.tracker.data.statement

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Reads the first sheet of an .xlsx workbook into rows of cell text (#13).
 *
 * An .xlsx is a zip of XML parts, so the JDK's zip and SAX APIs (both on
 * Android and in plain JVM tests) are all it takes — no Apache POI, which
 * would add megabytes for what a bank statement needs: values, not styles,
 * formulas or charts. Numbers come back as their stored text ("45567",
 * "1234.5"); a date cell is a day serial, which [StatementTableParser]
 * converts when that column is the Date column.
 *
 * Legacy binary .xls (BIFF) is not read: it needs a heavy library, and every
 * spreadsheet app can save as .xlsx or .csv.
 */
object XlsxReader {

    /** Uncompressed cap per part — a statement is kilobytes; this stops a zip bomb. */
    private const val MAX_PART_BYTES = 32L * 1024 * 1024
    const val MAX_ROWS = 20_000

    fun readRows(input: InputStream): List<List<String>> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/')
                if (name == SHARED_STRINGS || name == WORKBOOK || name == WORKBOOK_RELS ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                ) {
                    parts[name] = zip.readCapped()
                }
            }
        }
        val sheet = parts[firstSheetPath(parts)]
            ?: parts.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull()?.let { parts[it] }
            ?: throw IllegalArgumentException("No worksheet found in this file")
        val shared = parts[SHARED_STRINGS]?.let(::sharedStrings).orEmpty()
        return sheetRows(sheet, shared)
    }

    /** The workbook's first sheet, by its order in workbook.xml (not its file name). */
    private fun firstSheetPath(parts: Map<String, ByteArray>): String? {
        val workbook = parts[WORKBOOK] ?: return null
        val rels = parts[WORKBOOK_RELS] ?: return null
        var firstId: String? = null
        parse(workbook, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
                if (localName == "sheet" && firstId == null) {
                    firstId = (0 until attributes.length)
                        .firstOrNull { attributes.getLocalName(it) == "id" }
                        ?.let { attributes.getValue(it) }
                }
            }
        })
        var target: String? = null
        parse(rels, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
                if (localName == "Relationship" && attributes.getValue("Id") == firstId) {
                    target = attributes.getValue("Target")
                }
            }
        })
        return target?.let { t -> if (t.startsWith("/")) t.trimStart('/') else "xl/$t" }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val strings = ArrayList<String>()
        parse(bytes, object : DefaultHandler() {
            private val current = StringBuilder()
            private var inText = false
            private var inPhonetic = false
            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
                when (localName) {
                    "si" -> current.setLength(0)
                    "rPh" -> inPhonetic = true
                    "t" -> inText = !inPhonetic
                }
            }
            override fun endElement(uri: String?, localName: String, qName: String?) {
                when (localName) {
                    "si" -> strings += current.toString()
                    "rPh" -> inPhonetic = false
                    "t" -> inText = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.append(ch, start, length)
            }
        })
        return strings
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        parse(bytes, object : DefaultHandler() {
            private var row: MutableList<String>? = null
            private var rowIndex = -1
            private var column = -1
            private var type: String? = null
            private val value = StringBuilder()
            private var capture = false

            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
                when (localName) {
                    "row" -> {
                        // Rows can skip numbers (empty rows aren't written); keep positions.
                        val r = attributes.getValue("r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                        while (rows.size < r && rows.size < MAX_ROWS) rows.add(emptyList())
                        rowIndex = r
                        row = ArrayList()
                        column = -1
                    }
                    "c" -> {
                        column = attributes.getValue("r")?.let(::columnIndex) ?: (column + 1)
                        type = attributes.getValue("t")
                        value.setLength(0)
                    }
                    "v", "t" -> capture = true
                }
            }

            override fun endElement(uri: String?, localName: String, qName: String?) {
                when (localName) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        val cells = row
                        if (cells != null && column in cells.size until MAX_COLUMNS) {
                            while (cells.size < column) cells += ""
                            cells += text
                        }
                    }
                    "row" -> {
                        if (rows.size < MAX_ROWS) row?.let { rows += it }
                        row = null
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capture) value.append(ch, start, length)
            }
        })
        return rows
    }

    /** "AB12" → 27 (zero-based column of a cell reference). */
    internal fun columnIndex(reference: String): Int? {
        var index = 0
        var letters = 0
        for (ch in reference) {
            if (ch !in 'A'..'Z') break
            index = index * 26 + (ch - 'A' + 1)
            letters++
        }
        return if (letters == 0) null else index - 1
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        // No DTDs or external entities in an untrusted file (not every platform
        // parser supports every feature flag, hence runCatching).
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.newSAXParser().xmlReader.apply {
            contentHandler = handler
            parse(InputSource(ByteArrayInputStream(bytes)))
        }
    }

    private fun ZipInputStream.readCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_PART_BYTES) throw IllegalArgumentException("This spreadsheet is too large to import")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private const val MAX_COLUMNS = 200
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
}
