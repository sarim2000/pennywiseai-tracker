package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Regression tests for [GPayPdfParser], particularly issue #250 — all
 * transactions after the first were falling back to `System.currentTimeMillis()`
 * because the pre-anchor date buffer was only populated once at the start of
 * the document and never refilled.
 */
class GPayPdfParserTest {

    private val parser = GPayPdfParser()

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val dateFormat = SimpleDateFormat("dd MMM, yyyy hh:mm a", Locale.ENGLISH).apply {
        timeZone = ist
    }

    private fun epochForIstDate(text: String): Long = dateFormat.parse(text)!!.time

    /**
     * Fixture modelled after real GPay PDF exports: each transaction is preceded
     * by a "date / year / time" triplet on its own lines, then the anchor line
     * (`"Paid to …"` / `"Received from …"`), then amount, UPI id, and account
     * line. This is the shape the parser's docstrings describe.
     */
    private val statementText = """
        Google Pay Statement
        01 Sep,
        2025
        03:02 PM
        Paid to Starbucks
        ₹450
        UPI Transaction ID: 123456789012
        Paid by HDFC Bank 1234
        02 Sep,
        2025
        11:30 AM
        Received from Alice
        ₹2,000
        UPI Transaction ID: 223456789012
        Paid to HDFC Bank 1234
        03 Sep,
        2025
        07:45 PM
        Paid to Amazon
        ₹1,250.50
        UPI Transaction ID: 323456789012
        Paid by HDFC Bank 1234
    """.trimIndent()

    private val tableLayoutStatementText = """
        Transaction statement
        Date & time               Transaction details                                      Amount

        15 Oct, 2025              Paid to SAMPLE MERCHANT A                               ₹12.34
        11:08 AM                  UPI Transaction ID: 123456789012
                                       Paid by Example Bank 1234

        16 Oct, 2025              Received from SAMPLE SENDER                            ₹567.89
        09:41 AM                  UPI Transaction ID: 223456789012
                                       Paid to Example Bank 1234

        16 Oct, 2025              Paid to SAMPLE MERCHANT B                                ₹42.00
        11:13 AM                  UPI Transaction ID: 323456789012
                                       Paid by Example Bank 1234
    """.trimIndent()

    @Test
    fun `parser handles the fixture`() {
        assertTrue("canHandle should recognise the GPay statement", parser.canHandle(statementText))
    }

    @Test
    fun `parses all three transactions`() {
        val txs = parser.parse(statementText)
        assertEquals(3, txs.size)
    }

    @Test
    fun `each transaction gets its own correct timestamp - issue 250 regression`() {
        val txs = parser.parse(statementText)
        assertEquals(3, txs.size)

        val expected1 = epochForIstDate("01 Sep, 2025 03:02 PM")
        val expected2 = epochForIstDate("02 Sep, 2025 11:30 AM")
        val expected3 = epochForIstDate("03 Sep, 2025 07:45 PM")

        assertEquals("txn1 should parse to 01 Sep 2025 03:02 PM IST", expected1, txs[0].timestamp)
        assertEquals("txn2 should parse to 02 Sep 2025 11:30 AM IST", expected2, txs[1].timestamp)
        assertEquals("txn3 should parse to 03 Sep 2025 07:45 PM IST", expected3, txs[2].timestamp)
    }

    @Test
    fun `last transaction does not fall back to current time`() {
        // Before the fix, the last transaction in the statement had no "next"
        // transaction's date lines to pollute its block, so extractTimestamp
        // returned null and the parser fell back to System.currentTimeMillis().
        // A fresh parse should land nowhere near "now".
        val txs = parser.parse(statementText)
        val lastTimestamp = txs.last().timestamp
        val now = System.currentTimeMillis()
        val diff = Math.abs(now - lastTimestamp)
        assertTrue(
            "Last timestamp ($lastTimestamp) looks like current time ($now) — fallback regression",
            diff > 24L * 60 * 60 * 1000 // more than 1 day away from now
        )
    }

    @Test
    fun `merchant type and account are extracted correctly`() {
        val txs = parser.parse(statementText)

        assertEquals("Starbucks", txs[0].merchant)
        assertEquals(TransactionType.EXPENSE, txs[0].type)
        assertEquals(BigDecimal("450"), txs[0].amount)
        assertEquals("1234", txs[0].accountLast4)

        assertEquals("Alice", txs[1].merchant)
        assertEquals(TransactionType.INCOME, txs[1].type)
        assertEquals(BigDecimal("2000"), txs[1].amount)

        assertEquals("Amazon", txs[2].merchant)
        assertEquals(TransactionType.EXPENSE, txs[2].type)
        assertEquals(BigDecimal("1250.50"), txs[2].amount)
    }

    @Test
    fun `parses table layout extracted from Google Pay PDF`() {
        val txs = parser.parse(tableLayoutStatementText)

        assertEquals(3, txs.size)

        assertEquals("SAMPLE MERCHANT A", txs[0].merchant)
        assertEquals(TransactionType.EXPENSE, txs[0].type)
        assertEquals(BigDecimal("12.34"), txs[0].amount)
        assertEquals("123456789012", txs[0].reference)
        assertEquals("1234", txs[0].accountLast4)
        assertEquals(epochForIstDate("15 Oct, 2025 11:08 AM"), txs[0].timestamp)

        assertEquals("SAMPLE SENDER", txs[1].merchant)
        assertEquals(TransactionType.INCOME, txs[1].type)
        assertEquals(BigDecimal("567.89"), txs[1].amount)
        assertEquals("223456789012", txs[1].reference)
        assertEquals(epochForIstDate("16 Oct, 2025 09:41 AM"), txs[1].timestamp)

        assertEquals("SAMPLE MERCHANT B", txs[2].merchant)
        assertEquals(TransactionType.EXPENSE, txs[2].type)
        assertEquals(BigDecimal("42.00"), txs[2].amount)
        assertEquals("323456789012", txs[2].reference)
        assertEquals(epochForIstDate("16 Oct, 2025 11:13 AM"), txs[2].timestamp)
    }

    /**
     * #1100: a synthetic two-page statement in the current "Date & time |
     * Transaction details | Amount" layout. Times carry a narrow no-break space
     * before AM/PM (as ICU formats them), the account is "State Bank of India
     * 1234" (bank keyword not right before the digits), and one row is a
     * Google Pay rewards credit. `cells` is the text in cell order (date, time,
     * details, amount on their own lines); `rows` is one visual line per row.
     */
    private fun assertIssue1100Fixture(name: String) {
        val text = javaClass.getResource("/statements/$name")!!.readText()
        assertTrue(parser.canHandle(text))
        val txs = parser.parse(text)

        assertEquals(6, txs.size)
        assertEquals(
            listOf("SAMPLE CAFE", "Google Pay rewards", "SAMPLE GROCERY", "SAMPLE FRIEND", "SAMPLE STORE 2024", "SAMPLE PHARMACY"),
            txs.map { it.merchant }
        )
        assertEquals(
            listOf(
                TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.EXPENSE,
                TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.EXPENSE
            ),
            txs.map { it.type }
        )
        assertEquals(
            listOf("500", "2", "1250.50", "3000", "99", "245.75").map(::BigDecimal),
            txs.map { it.amount }
        )
        assertEquals(
            listOf(
                "01 Nov, 2025 10:15 AM", "02 Nov, 2025 06:40 PM", "03 Nov, 2025 09:05 AM",
                "04 Nov, 2025 08:30 PM", "05 Nov, 2025 12:00 PM", "06 Nov, 2025 07:10 AM"
            ).map(::epochForIstDate),
            txs.map { it.timestamp }
        )
        assertEquals((1..6).map { "10000000000$it" }, txs.map { it.reference })
        txs.forEach {
            assertEquals("1234", it.accountLast4)
            assertEquals("State Bank of India", it.bankName)
        }
    }

    @Test
    fun `issue 1100 - cell-ordered statement keeps dates, payees and received rows`() =
        assertIssue1100Fixture("gpay_statement_cells.txt")

    @Test
    fun `issue 1100 - row-per-line statement keeps dates, payees and received rows`() =
        assertIssue1100Fixture("gpay_statement_rows.txt")

    @Test
    fun `middle transactions do not borrow next transaction's date`() {
        // Before the fix, block 2's date lines (02 Sep) were being appended to
        // block 1's content because splitIntoBlocks kept adding every non-anchor
        // line to the current block. extractTimestamp then picked block 2's
        // "02 Sep" for block 1, producing wildly wrong timestamps. Assert the
        // middle transaction specifically gets its OWN date, not the next one.
        val txs = parser.parse(statementText)
        val txn2 = txs[1]
        val expected = epochForIstDate("02 Sep, 2025 11:30 AM")
        val notExpected = epochForIstDate("03 Sep, 2025 07:45 PM")
        assertEquals(expected, txn2.timestamp)
        assertNotEquals(notExpected, txn2.timestamp)
    }
}
