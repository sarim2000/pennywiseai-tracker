package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Fixture mirrors real PdfBox extraction of Paytm UPI statements: date, time,
 * anchor, and amount each on their own line; merchant names wrapping across
 * lines; page furniture interleaved mid-transaction; Notes containing
 * date-like lines; occasional merged-column lines; and a statement period
 * spanning two calendar years. All names, IDs, and references are fictional.
 */
class PaytmPdfParserTest {

    private val parser = PaytmPdfParser()

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val dateFormat = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.ENGLISH).apply {
        timeZone = ist
    }

    private fun epochForIstDate(text: String): Long = dateFormat.parse(text)!!.time

    private val statementText = """
        Page  of 1 3
        For any queries,
        Contact Us
        SAMPLE USER
        9876543210, null
        Paytm Statement for
        18 JUL'25 - 17 JUL'26
        Total Money Paid
        - Rs.10,500.77
        7 Payments made
        Total Money Received
        + Rs.275
        2 Payments received
        Note:
        Self transfer payments are not included in the total money paid and money received calculations
        Accounts Payment made Payment received
        State Bank Of India - 17 Rs.10,500.77
        (7 Payments)
        Rs.275
        (2 Payments)
        Passbook Payments History
        All payments done by you on Paytm App are reflected in this statement
        Date &
        Time
        Transaction Details Notes & Tags Your Account Amount
        17 Jul
        8:24 PM
        Money sent to Alice Smith
         UPI ID: alicesmith@okbank  on
        UPI Ref No: 310000000001
        Note: Badminton
        26th July meetup
        contribution
         Tag:
        # Money Transfer
        State Bank
        Of India - 17
        - Rs.220
        17 Jul
        9:01 AM
        Recharge of BSNL Mobile 9876543210
         UPI ID: recharge123@ptybl  on
        UPI Ref No: 210000000002
        Order ID: 27000000001
         Tag:
        # Bill Payments
        State Bank
        Of India - 17
        - Rs.141
        14 Jul
        3:59 PM
        Money sent to Ramesh Chandrakant
        Patil
        UPI ID: 9876500001@kotak
        UPI Ref No: 610000000003
         Tag:
        # Money Transfer
        State Bank
        Of India - 17
        - Rs.300
        Page  of 2 3
        For any queries,
        Contact Us
        Passbook Payments History
        All payments done by you on Paytm App are reflected in this statement
        Date &
        Time
        Transaction Details Notes & Tags Your Account Amount
        30 Jun
        2:28 AM
        Money unblocked for Sample Jewels
        Limited IPO
        UPI ID: samplejewels.ipo@validbank
        UPI Ref No: 654000000004
        Note: Payee
        Initiated Revoke
        State Bank
        Of India - 17
        Rs.13,800
        Note: This payment is not included in the total money paid and money received calculations.
        28 Jun
        9:08 AM
        Money sent to Bob Kumar
         UPI ID: bobkumar@okbank  on
        UPI Ref No: 609000000005
        Note: Badminton
        28 Jun
         Tag:
        # Money Transfer
        State Bank
        Of India - 17
        - Rs.186
        25 Jun
        1:39 PM
        Money blocked for Sample Jewels Limited
        IPO
        UPI ID: samplejewels.ipo@validbank
        UPI Ref No: 104000000006
        Note: SAMPLEREF
        State Bank
        Of India - 17
        Rs.13,800
        Page  of 3 3
        For any queries,
        Contact Us
        Passbook Payments History
        All payments done by you on Paytm App are reflected in this statement
        Date &
        Time
        Transaction Details Notes & Tags Your Account Amount
        Note: This payment is not included in the total money paid and money received calculations.
        25 Jun
        7:01 AM
        Received from Chetan Verma
         UPI ID: chetanverma@okaxis  on
        UPI Ref No: 617000000007
         Tag:
        # Money Received
        State Bank
        Of India - 17
        + Rs.75
        21 Jun
        1:43 PM
        Automatic payment for Sample Insurance
        Brokers
         UPI ID: sampleinsurance@paytm  on
        UPI Ref No: 617200000008
        Note: OidSAMPLE123
         Tag:
        # Financial
        State Bank
        Of India - 17
        - Rs.482
        05 Jan
        11:15 AM
        Paid to Sample Store
         UPI ID: samplestore@ybl  on
        UPI Ref No: 500000000001
         Tag:
        # Groceries
        State Bank
        Of India - 17
        - Rs.80
        20 Jul
        6:30 PM
        Received from Sample Labs Pvt Ltd
        UPI ID: refunds.samplelabs@axisbank
        UPI Ref No: 559000000010
        Note: Store
        refund
         Tag:
        # Money Received
        State Bank
        Of India - 17
        + Rs.200
        12 Jun
        8:54 AM
        Money sent to Ravi Verma  Tag: State Bank - Rs.2,187
        UPI ID: 9876500002@ibl  on
        UPI Ref No: 208000000011
        # Food Of India - 17
    """.trimIndent()

    @Test
    fun `parser detects Paytm statements`() {
        assertTrue("canHandle should recognise Paytm statement", parser.canHandle(statementText))
    }

    @Test
    fun `parser rejects non-Paytm statements`() {
        val gpayText = "Google Pay Statement\nUPI Transaction ID: 123"
        assertFalse("canHandle should reject GPay statement", parser.canHandle(gpayText))
    }

    @Test
    fun `parses all nine real transactions and skips the two mandates`() {
        val txs = parser.parse(statementText)
        assertEquals(9, txs.size)
    }

    @Test
    fun `extracts correct merchants including wrapped names`() {
        val txs = parser.parse(statementText)

        assertEquals("Alice Smith", txs[0].merchant)
        assertEquals("BSNL Mobile 9876543210", txs[1].merchant)
        assertEquals("Ramesh Chandrakant Patil", txs[2].merchant)
        assertEquals("Bob Kumar", txs[3].merchant)
        assertEquals("Chetan Verma", txs[4].merchant)
        assertEquals("Sample Insurance Brokers", txs[5].merchant)
        assertEquals("Sample Store", txs[6].merchant)
        assertEquals("Sample Labs Pvt Ltd", txs[7].merchant)
        assertEquals("Ravi Verma", txs[8].merchant)
    }

    @Test
    fun `transaction type follows the amount sign`() {
        val txs = parser.parse(statementText)

        assertEquals(TransactionType.EXPENSE, txs[0].type)
        assertEquals(TransactionType.EXPENSE, txs[1].type)
        assertEquals(TransactionType.EXPENSE, txs[2].type)
        assertEquals(TransactionType.EXPENSE, txs[3].type)
        assertEquals(TransactionType.INCOME, txs[4].type)
        assertEquals(TransactionType.EXPENSE, txs[5].type)
        assertEquals(TransactionType.EXPENSE, txs[6].type)
        assertEquals(TransactionType.INCOME, txs[7].type)
        assertEquals(TransactionType.EXPENSE, txs[8].type)
    }

    @Test
    fun `extracts correct amounts`() {
        val txs = parser.parse(statementText)

        assertEquals(BigDecimal("220"), txs[0].amount)
        assertEquals(BigDecimal("141"), txs[1].amount)
        assertEquals(BigDecimal("300"), txs[2].amount)
        assertEquals(BigDecimal("186"), txs[3].amount)
        assertEquals(BigDecimal("75"), txs[4].amount)
        assertEquals(BigDecimal("482"), txs[5].amount)
        assertEquals(BigDecimal("80"), txs[6].amount)
        assertEquals(BigDecimal("200"), txs[7].amount)
        assertEquals(BigDecimal("2187"), txs[8].amount)
    }

    @Test
    fun `extracts UPI references`() {
        val txs = parser.parse(statementText)

        assertEquals("310000000001", txs[0].reference)
        assertEquals("210000000002", txs[1].reference)
        assertEquals("610000000003", txs[2].reference)
        assertEquals("609000000005", txs[3].reference)
        assertEquals("617000000007", txs[4].reference)
        assertEquals("617200000008", txs[5].reference)
        assertEquals("500000000001", txs[6].reference)
        assertEquals("559000000010", txs[7].reference)
        assertEquals("208000000011", txs[8].reference)
    }

    @Test
    fun `extracts account bank name and last digits`() {
        val txs = parser.parse(statementText)
        assertEquals("State Bank Of India", txs[0].bankName)
        assertEquals("17", txs[0].accountLast4)
    }

    @Test
    fun `infers year from statement period across the year boundary`() {
        val txs = parser.parse(statementText)

        // Statement period: 18 JUL'25 - 17 JUL'26
        assertEquals(epochForIstDate("17 Jul 2026 08:24 PM"), txs[0].timestamp)
        assertEquals(epochForIstDate("28 Jun 2026 09:08 AM"), txs[3].timestamp)
        // 05 Jan falls inside the period only in 2026
        assertEquals(epochForIstDate("05 Jan 2026 11:15 AM"), txs[6].timestamp)
        // 20 Jul is after the period end in 2026, so it must be 2025
        assertEquals(epochForIstDate("20 Jul 2025 06:30 PM"), txs[7].timestamp)
    }

    @Test
    fun `date-like line inside a Note does not split the block`() {
        val txs = parser.parse(statementText)
        // "Note: Badminton / 28 Jun" belongs to Bob Kumar's transaction; it
        // must not start a bogus block or steal the next transaction's date.
        val bob = txs.first { it.merchant == "Bob Kumar" }
        assertEquals(epochForIstDate("28 Jun 2026 09:08 AM"), bob.timestamp)
        assertEquals(BigDecimal("186"), bob.amount)
    }

    @Test
    fun `sender is set to Paytm PDF`() {
        val txs = parser.parse(statementText)
        assertTrue(txs.all { it.sender == "Paytm PDF" })
    }

    @Test
    fun `mandate blocks with unsigned amounts are never imported`() {
        val txs = parser.parse(statementText)
        assertTrue(txs.none { it.amount == BigDecimal("13800") })
        assertTrue(txs.none { (it.merchant ?: "").contains("Jewels", ignoreCase = true) })
    }
}
