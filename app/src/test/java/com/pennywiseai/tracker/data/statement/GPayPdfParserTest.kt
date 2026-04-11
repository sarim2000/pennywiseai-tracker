package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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
