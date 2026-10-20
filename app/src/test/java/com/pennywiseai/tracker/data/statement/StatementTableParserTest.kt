package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StatementTableParserTest {

    private val account = StatementAccount(bankName = "Sample Bank", last4 = "1234", currency = "INR")

    // Synthetic statement: bank preamble rows, header on row 4, a blank line, an
    // opening-balance row and a totals footer that must all be skipped.
    private val debitCreditCsv = """
        Sample Bank Ltd,,,,,
        Statement of account,,,,,
        Period: 01/01/2025 - 31/01/2025,,,,,
        Txn Date,Narration,Ref No,Withdrawal Amt,Deposit Amt,Closing Balance
        ,Opening Balance,,,,"1,00,000.00"
        02/01/2025,UPI/512345678901/GROCERY MART/okaxis,512345678901,"1,250.50",,"98,749.50"
        03/01/2025,SALARY JAN,,,"1,23,456.78","2,22,206.28"

        05/01/2025,ATM WDL,,"2,000.00",,"2,20,206.28"
        ,,,,,
        Total,,,"3,250.50","1,23,456.78",
    """.trimIndent()

    @Test
    fun `separate debit and credit columns with a header that isn't the first row`() {
        val rows = StatementTableParser.readCsv(debitCreditCsv)
        assertEquals(3, StatementTableParser.guessHeaderRow(rows))
        val mapping = StatementTableParser.guessMapping(rows)
        assertEquals(0, mapping.dateColumn)
        assertEquals(1, mapping.descriptionColumn)
        assertEquals(3, mapping.debitColumn)
        assertEquals(4, mapping.creditColumn)
        assertEquals(5, mapping.balanceColumn)

        val result = StatementTableParser.parse(rows, mapping, account)
        val txns = result.transactions
        assertEquals(3, txns.size)
        assertEquals(2, result.skippedRows) // opening balance + totals; blank lines aren't counted

        assertEquals(BigDecimal("1250.50"), txns[0].amount)
        assertEquals(TransactionType.EXPENSE, txns[0].type)
        assertEquals("512345678901", txns[0].reference)
        assertEquals(BigDecimal("98749.50"), txns[0].balance)
        assertEquals(BigDecimal("123456.78"), txns[1].amount)
        assertEquals(TransactionType.INCOME, txns[1].type)
        assertEquals(LocalDate.of(2025, 1, 3), StatementBalanceCheck.dateOf(txns[1]))
        txns.forEach {
            assertEquals("INR", it.currency)
            assertEquals("Sample Bank", it.bankName)
            assertEquals("1234", it.accountLast4)
        }
    }

    @Test
    fun `single signed amount column`() {
        val rows = StatementTableParser.readCsv(
            """
            Date,Description,Amount,Balance
            2025-02-01,Coffee,-120.00,880.00
            2025-02-02,Refund,+45.5,925.50
            2025-02-03,Rent,(500.00),425.50
            """.trimIndent()
        )
        val mapping = StatementTableParser.guessMapping(rows)
        assertEquals(2, mapping.amountColumn)
        val txns = StatementTableParser.parse(rows, mapping, account).transactions
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.EXPENSE), txns.map { it.type })
        assertEquals(listOf(BigDecimal("120.00"), BigDecimal("45.5"), BigDecimal("500.00")), txns.map { it.amount })
    }

    @Test
    fun `amount with a Dr Cr type column`() {
        val rows = StatementTableParser.readCsv(
            """
            Value Date;Particulars;Amount;Dr/Cr
            05-JAN-25;POS SHOP;"1.234,56";DR
            06-Jan-25;INTEREST;10;CR
            """.trimIndent()
        )
        val mapping = StatementTableParser.guessMapping(rows)
        assertEquals(3, mapping.typeColumn)
        val txns = StatementTableParser.parse(rows, mapping, account).transactions
        assertEquals(TransactionType.EXPENSE, txns[0].type)
        assertEquals(BigDecimal("1234.56"), txns[0].amount)
        assertEquals(TransactionType.INCOME, txns[1].type)
        assertEquals(LocalDate.of(2025, 1, 5), StatementBalanceCheck.dateOf(txns[0]))
    }

    @Test
    fun `amounts in Indian and suffixed formats`() {
        fun amount(s: String) = StatementTableParser.parseAmount(s)
        assertEquals(BigDecimal("123456.78"), amount("1,23,456.78")!!.value)
        assertEquals(CellAmount(BigDecimal("500.00"), false), amount("₹ 500.00 Cr"))
        assertEquals(CellAmount(BigDecimal("500.00"), true), amount("500.00Dr"))
        assertEquals(true, amount("Rs.-75")!!.negative)
        assertEquals(true, amount("75-")!!.negative)
        assertNull(amount("1,000")!!.negative)
        assertEquals(BigDecimal("1000"), amount("1,000")!!.value)
        assertEquals(BigDecimal("12.5"), amount("12,5")!!.value)
        assertNull(amount(""))
        assertNull(amount("Opening Balance"))
    }

    @Test
    fun `dates in the supported formats`() {
        val p = StatementTableParser
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("05/01/2025", "dd/MM/yyyy"))
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("5/1/2025", "dd/MM/yyyy"))
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("05-Jan-25", "dd-MMM-yy"))
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("05-JAN-25", "dd-MMM-yy"))
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("2025-01-05", "yyyy-MM-dd"))
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("05/01/2025 14:32:10", "dd/MM/yyyy"))
        // A four-digit year must not be read as two digits plus leftovers.
        assertNull(p.parseDate("05/01/2025", "dd/MM/yy"))
        // Excel day serial.
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("45662", null))
        assertNull(p.parseDate("Total", "dd/MM/yyyy"))
    }

    @Test
    fun `date format detection prefers the format that reads the column`() {
        assertEquals("dd/MM/yyyy", StatementTableParser.detectDateFormat(listOf("05/01/2025", "31/01/2025", "Total")))
        assertEquals("dd-MMM-yy", StatementTableParser.detectDateFormat(listOf("05-Jan-25", "06-Feb-25")))
        assertEquals("yyyy-MM-dd", StatementTableParser.detectDateFormat(listOf("2025-01-05")))
        assertEquals("MM/dd/yyyy", StatementTableParser.detectDateFormat(listOf("01/31/2025", "02/28/2025")))
    }

    @Test
    fun `identical rows get distinct hashes that are stable across re-imports`() {
        val csv = """
            Date,Narration,Debit,Credit
            01/03/2025,TEA,20,
            01/03/2025,TEA,20,
        """.trimIndent()
        val rows = StatementTableParser.readCsv(csv)
        val mapping = StatementTableParser.guessMapping(rows)
        val first = StatementTableParser.parse(rows, mapping, account).transactions.map { it.generateTransactionId() }
        val second = StatementTableParser.parse(rows, mapping, account).transactions.map { it.generateTransactionId() }
        assertEquals(2, first.toSet().size)
        assertEquals(first, second)
        val otherAccount = StatementTableParser.parse(rows, mapping, account.copy(last4 = "9999")).transactions
        assertNotEquals(first[0], otherAccount[0].generateTransactionId())
    }

    @Test
    fun `saved mapping is found by header signature wherever the header sits`() {
        val rows = StatementTableParser.readCsv(debitCreditCsv)
        val mapping = StatementTableParser.guessMapping(rows).copy(accountBankName = "Sample Bank", accountLast4 = "1234")
        val saved = mapOf(StatementTableParser.signature(rows[3]) to mapping.copy(headerRow = 0))
        val shifted = listOf(listOf("extra preamble")) + rows
        assertEquals(4, StatementTableParser.findSavedMapping(shifted, saved)!!.headerRow)
        assertEquals("1234", StatementTableParser.findSavedMapping(shifted, saved)!!.accountLast4)
    }

    @Test
    fun `saved mappings round-trip and an older or partial one still decodes`() {
        val map = mapOf("date|narration" to StatementColumnMapping(dateColumn = 0, descriptionColumn = 1, debitColumn = 2))
        assertEquals(map, StatementTableParser.decodeMappings(StatementTableParser.encodeMappings(map)))
        // A mapping saved before some fields existed (and with one we no longer know).
        val old = """{"date|amount":{"dateColumn":0,"amountColumn":1,"retiredField":true}}"""
        val decoded = StatementTableParser.decodeMappings(old).getValue("date|amount")
        assertEquals(1, decoded.amountColumn)
        assertEquals(StatementColumnMapping.NONE, decoded.balanceColumn)
        assertNull(decoded.accountLast4)
        assertTrue(StatementTableParser.decodeMappings("not json").isEmpty())
    }

    @Test
    fun `xlsx with shared strings, numeric dates and a sparse row`() {
        val xlsx = zip(
            "[Content_Types].xml" to "<Types/>",
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Statement" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Type="worksheet" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Date</t></si><si><t>Narration</t></si><si><t>Debit</t></si><si><t>Credit</t></si><si><t>Balance</t></si><si><r><t>SHOP </t></r><r><t>ONE</t></r></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>wrong sheet</t></is></c></row></sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to """<x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><x:sheetData>
                <x:row r="2"><x:c r="A2" t="s"><x:v>0</x:v></x:c><x:c r="B2" t="s"><x:v>1</x:v></x:c><x:c r="C2" t="s"><x:v>2</x:v></x:c><x:c r="D2" t="s"><x:v>3</x:v></x:c><x:c r="E2" t="s"><x:v>4</x:v></x:c></x:row>
                <x:row r="3"><x:c r="A3"><x:v>45662</x:v></x:c><x:c r="B3" t="s"><x:v>5</x:v></x:c><x:c r="C3"><x:v>250.5</x:v></x:c><x:c r="E3"><x:v>749.5</x:v></x:c></x:row>
            </x:sheetData></x:worksheet>"""
        )
        val rows = StatementTableParser.readTable(xlsx)
        assertEquals(listOf<String>(), rows[0]) // row 1 absent in the file: kept as blank
        assertEquals(listOf("Date", "Narration", "Debit", "Credit", "Balance"), rows[1])
        assertEquals(listOf("45662", "SHOP ONE", "250.5", "", "749.5"), rows[2])

        val txns = StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account).transactions
        assertEquals(1, txns.size)
        assertEquals(LocalDate.of(2025, 1, 5), StatementBalanceCheck.dateOf(txns[0]))
        assertEquals(BigDecimal("749.5"), txns[0].balance)
    }

    @Test(expected = StatementTableParser.LegacyXlsException::class)
    fun `legacy xls is refused with a hint`() {
        StatementTableParser.readTable(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0))
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
