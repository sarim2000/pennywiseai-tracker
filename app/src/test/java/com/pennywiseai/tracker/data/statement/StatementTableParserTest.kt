package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(listOf(BigDecimal("120.00"), BigDecimal("45.50"), BigDecimal("500.00")), txns.map { it.amount })
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
    fun `payee is read out of channel-prefixed narrations`() {
        val p = StatementTableParser
        assertEquals("GROCERY MART", p.merchantFrom("UPI/512345678901/GROCERY MART/okaxis"))
        assertEquals("EXAMPLE CORP", p.merchantFrom("NEFT/HDFC0001234/EXAMPLE CORP"))
        assertEquals("ATM WDL/SAMPLE ATM", p.merchantFrom("ATM WDL/SAMPLE ATM"))
        assertEquals("SALARY", p.merchantFrom("SALARY"))
        assertNull(p.merchantFrom(""))
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
        // Excel day serial — only when the column is read as Excel dates.
        assertEquals(LocalDate.of(2025, 1, 5), p.parseDate("45662", StatementTableParser.EXCEL_SERIAL))
        assertNull(p.parseDate("45662", "dd/MM/yyyy"))
        assertNull(p.parseDate("45662", null))
        // Serials outside 1990–2100 aren't dates either.
        assertNull(p.parseDate("200", StatementTableParser.EXCEL_SERIAL))
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
        assertEquals(BigDecimal("749.50"), txns[0].balance)
    }

    @Test
    fun `a number in a text date column is not an Excel date`() {
        // The summary row's "220" used to become a 1900s/2023-ish expense.
        val rows = StatementTableParser.readCsv(
            """
            Date,Narration,Debit,Credit
            02/01/2025,TEA,20.00,
            03/01/2025,LUNCH,200.00,
            220,Total debits,220.00,
            45000,Opening balance carried,500.00,
            """.trimIndent()
        )
        val mapping = StatementTableParser.guessMapping(rows)
        assertEquals("dd/MM/yyyy", StatementTableParser.effectiveDateFormat(rows, mapping))
        val result = StatementTableParser.parse(rows, mapping, account)
        assertEquals(listOf("TEA", "LUNCH"), result.transactions.map { it.merchant })
        assertEquals(2, result.skippedRows)
    }

    @Test
    fun `a date far outside the statement's span is dropped`() {
        val csv = buildString {
            append("Date,Narration,Debit,Credit\n")
            (1..12).forEach { append("%02d/01/2025,ROW $it,10.00,\n".format(it)) }
            append("15/06/2023,TYPO ROW,999.00,\n")
        }
        val rows = StatementTableParser.readCsv(csv)
        val result = StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account)
        assertEquals(12, result.transactions.size)
        assertTrue(result.transactions.none { it.merchant == "TYPO ROW" })
    }

    @Test
    fun `rows spread over years with no main body are all kept`() {
        val csv = """
            Date,Narration,Debit,Credit
            01/01/2021,FIRST,10.00,
            01/06/2022,SECOND,20.00,
            01/12/2023,THIRD,30.00,
        """.trimIndent()
        val rows = StatementTableParser.readCsv(csv)
        val result = StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account)
        assertEquals(3, result.transactions.size)
        assertEquals(0, result.skippedRows)
    }

    @Test
    fun `a quiet stretch of a long statement is kept`() {
        // 19 January rows and one in December: December is sparse, not a misread.
        val csv = buildString {
            append("Date,Narration,Debit,Credit\n")
            (1..19).forEach { append("%02d/01/2025,JAN $it,10.00,\n".format(it)) }
            append("15/12/2025,DECEMBER RENT,999.00,\n")
        }
        val rows = StatementTableParser.readCsv(csv)
        val result = StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account)
        assertEquals(20, result.transactions.size)
        assertEquals(0, result.skippedRows)
        assertEquals("DECEMBER RENT", result.transactions.last().merchant)
    }

    @Test
    fun `a huge number in an Excel date column is no date, not a crash`() {
        assertNull(StatementTableParser.parseDate("999999999999", StatementTableParser.EXCEL_SERIAL))
        assertNull(StatementTableParser.parseDate("1e300", StatementTableParser.EXCEL_SERIAL))
        assertNull(StatementTableParser.parseDate("-5", StatementTableParser.EXCEL_SERIAL))
    }

    @Test
    fun `one Debit-Credit header is a direction column, not two amount columns`() {
        val rows = StatementTableParser.readCsv(
            """
            Date,Description,Amount,Debit/Credit
            02/01/2025,SHOP,450.00,Dr
            03/01/2025,REFUND,450.00,Cr
            """.trimIndent()
        )
        val mapping = StatementTableParser.guessMapping(rows)
        assertFalse(mapping.usesDebitCredit)
        assertEquals(2, mapping.amountColumn)
        assertEquals(3, mapping.typeColumn)
        assertEquals(
            listOf(TransactionType.EXPENSE, TransactionType.INCOME),
            StatementTableParser.parse(rows, mapping, account).transactions.map { it.type }
        )
    }

    @Test
    fun `a channel in a Transaction Type column doesn't override the sign`() {
        val rows = StatementTableParser.readCsv(
            """
            Date,Description,Transaction Type,Amount
            02/01/2025,SHOP,Card,-450.00
            03/01/2025,SALARY,NEFT,50000.00
            """.trimIndent()
        )
        val mapping = StatementTableParser.guessMapping(rows)
        // A "type" header of channel names isn't a Dr/Cr column.
        assertEquals(StatementColumnMapping.NONE, mapping.typeColumn)
        val forced = mapping.copy(typeColumn = 2)
        assertEquals(
            listOf(TransactionType.EXPENSE, TransactionType.INCOME),
            StatementTableParser.parse(rows, forced, account).transactions.map { it.type }
        )
    }

    @Test
    fun `a Type column of Dr and Cr words is used`() {
        val rows = StatementTableParser.readCsv(
            """
            Date,Description,Type,Amount
            02/01/2025,SHOP,DR,450.00
            03/01/2025,REFUND,Cr.,450.00
            """.trimIndent()
        )
        val mapping = StatementTableParser.guessMapping(rows)
        assertEquals(2, mapping.typeColumn)
        assertEquals(
            listOf(TransactionType.EXPENSE, TransactionType.INCOME),
            StatementTableParser.parse(rows, mapping, account).transactions.map { it.type }
        )
    }

    @Test
    fun `a backslash before a closing quote doesn't break the CSV`() {
        val rows = StatementTableParser.readCsv("Date,Narration,Debit\n02/01/2025,\"PAYMENT C:\\\",20.00\n")
        assertEquals(listOf("02/01/2025", "PAYMENT C:\\", "20.00"), rows[1])
    }

    @Test
    fun `references come from the Reference column or a channel narration`() {
        val rows = StatementTableParser.readCsv("Txn Date,Narration,Chq/Ref No,Debit,Credit\n")
        assertEquals(2, StatementTableParser.guessMapping(rows).referenceColumn)
        val p = StatementTableParser
        assertEquals("UTR0000123456", p.referenceFrom("UTR0000123456", "NEFT TRANSFER"))
        assertEquals("612345678901", p.referenceFrom("", "UPI/612345678901/GROCERY MART/okaxis"))
        assertEquals("612345678901", p.referenceFrom("", "IMPS-612345678901-SOME NAME"))
        assertEquals("612345678901", p.referenceFrom("N289", "UPI/612345678901/X")) // a short cheque no. isn't one
        assertNull(p.referenceFrom("", "NEFT/SALARY OCT/EXAMPLE CORP"))
    }

    @Test
    fun `the preamble names an account by its last four`() {
        val p = StatementTableParser
        assertTrue(p.namesAccount("Sample Bank\nAccount: XXXXXX4321", "4321"))
        assertTrue(p.namesAccount("A/c No: 4321", "4321"))
        assertTrue(p.namesAccount("Account number 123456784321", "4321"))
        // A year in the statement period is not an account number.
        assertFalse(p.namesAccount("Period: 01/01/2021 to 31/01/2021", "2021"))
        assertFalse(p.namesAccount("Account: XXXXXX4321", "1234"))
        assertTrue(p.namesAccount("Card XXXX XXXX XXXX 4321", "4321"))
        assertTrue(p.namesAccount("Account No: ...4321", "4321"))
        // Numbers that merely end in the digits.
        assertFalse(p.namesAccount("Customer ID: 98764321", "4321"))
        assertFalse(p.namesAccount("Opening Balance: 154321.00", "4321"))
        assertFalse(p.namesAccount("Branch: MG Road 560034", "0034"))
        assertFalse(p.namesAccount("Statement period 01/04/2025", "2025"))
        assertFalse(p.namesAccount("Tax 4321", "4321"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `xlsx parts with a DTD are refused`() {
        StatementTableParser.readTable(
            zip(
                "xl/worksheets/sheet1.xml" to """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol "lol">]><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UTF-16 xlsx parts are refused`() {
        val sheet = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray(Charsets.UTF_16))
            zip.closeEntry()
        }
        StatementTableParser.readTable(out.toByteArray())
    }

    @Test
    fun `separator is counted outside quoted fields`() {
        val semicolons = "Date;Narration;Amount\n01/03/2025;\"PAY, TO, A, B, C\";\"1,23,456.78\"\n"
        assertEquals(';', StatementTableParser.detectSeparator(semicolons))
        assertEquals(listOf("01/03/2025", "PAY, TO, A, B, C", "1,23,456.78"), StatementTableParser.readCsv(semicolons)[1])
        assertEquals(',', StatementTableParser.detectSeparator("a,b,c\n1,2,3"))
        assertEquals('\t', StatementTableParser.detectSeparator("a\tb\tc"))
    }

    @Test
    fun `signature keeps empty header positions`() {
        val p = StatementTableParser
        val plain = p.signature(listOf("Date", "Description", "Debit", "Credit", "Balance"))
        val gap = p.signature(listOf("Date", "Description", "", "Debit", "Credit", "Balance"))
        assertNotEquals(plain, gap)
        // Trailing blanks (a ragged CSV) don't change it.
        assertEquals(plain, p.signature(listOf("Date", "Description", "Debit", "Credit", "Balance", "", " ")))
    }

    @Test
    fun `Excel numbers in exponent notation keep their value`() {
        assertEquals(BigDecimal("1500.00"), StatementTableParser.parseAmount("1.5E+3")!!.value)
        assertEquals(CellAmount(BigDecimal("250.50"), true), StatementTableParser.parseAmount("-250.5"))
    }

    @Test
    fun `Excel floating-point noise is rounded to paise`() {
        // A formula result as Excel stores it.
        assertEquals(BigDecimal("24521.00"), StatementTableParser.parseAmount("24521.000000000004")!!.value)
        assertEquals(BigDecimal("0.30"), StatementTableParser.parseAmount("0.30000000000000004")!!.value)
    }

    @Test
    fun `three-decimal amounts keep their precision`() {
        // OMR and JOD have three decimals.
        assertEquals(BigDecimal("12.345"), StatementTableParser.parseAmount("12.345")!!.value)
        assertEquals(BigDecimal("12.345"), StatementTableParser.parseAmount("12.345000000000001")!!.value)
        assertEquals(BigDecimal("12.345"), StatementTableParser.parseAmount("OMR 12.345")!!.value)
    }

    @Test
    fun `absurd amounts are rejected without expanding them`() {
        // Would otherwise build a billion-digit integer.
        assertNull(StatementTableParser.parseAmount("1e1000000000"))
        assertNull(StatementTableParser.parseAmount("1E+99"))
        assertNull(StatementTableParser.parseAmount("12345678901234567890"))
        assertNull(StatementTableParser.parseAmount("₹ 1,00,00,00,00,00,00,000.00"))
        assertEquals(BigDecimal("9999999999999.99"), StatementTableParser.parseAmount("9999999999999.99")!!.value)
    }

    @Test
    fun `oversized statements are refused, not cut short`() {
        val csv = buildString {
            append("Date,Narration,Amount\n")
            repeat(XlsxReader.MAX_ROWS) { append("01/03/2025,X,1\n") }
        }
        try {
            StatementTableParser.readCsv(csv)
            throw AssertionError("expected TooManyRowsException")
        } catch (_: StatementTableParser.TooManyRowsException) {
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `1904 date system workbooks are refused`() {
        StatementTableParser.readTable(
            zip(
                "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><workbookPr date1904="1"/><sheets/></workbook>""",
                "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""
            )
        )
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
