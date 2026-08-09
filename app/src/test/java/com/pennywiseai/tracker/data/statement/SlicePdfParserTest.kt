package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Fixtures are entirely synthetic — invented names, refs, amounts and VPAs
 * that only mimic the statement's layout. Never paste real statement content
 * here (hard constraint: no PII anywhere).
 */
class SlicePdfParserTest {

    private val parser = SlicePdfParser()

    private val statement = """
        01 Jan '26 - 31 Jan '26
        1/1
        Mr TEST PERSON
        Customer ID 100000000001
        Account SAVINGS
        A/C number 999900005678
        IFSC NESF0000999
        Opening balance Total credits Interest earned Total debits Closing balance
        + + - =
        ₹10,000 ₹6,000 ₹1.23 ₹5,679.1 ₹10,322.13
        DATE DETAILS REF NO. AMOUNT BALANCE
        02 Jan '26 Interest Cr. for 01-Jan-2026 111122223333 ₹1.23 ₹10,001.23
        02 Jan '26 Transfer from Holiday fund atom 444455556 ₹6,000 ₹16,001.23
        02 Jan '26 UPI-Debit-999900001111-SOME MERCHA
        NT NAME-ABCD0EFGH11-somevpa@bank-Paid V
        ia Elements
        2026010212345678 -₹5,000 ₹11,001.23
        03 Jan '26 Auto save to Daily saver atom 444455557 -₹100 ₹10,901.23
        03 Jan '26 Round ups 444455558 -₹18.2 ₹10,883.03
        04 Jan '26 UPI-Debit-999900002222-OTHER SHOP-WXYZ0AB1234-shop@upi-123456 2026010412345679 -₹560.9 ₹10,322.13
        Generated on 31 Jan '26
        Need help? Contact our support team at help@slice.bank.in or +91-0000000000 slice small finance bank
    """.trimIndent()

    @Test
    fun `handles slice statements only`() {
        assertTrue(parser.canHandle(statement))
        assertFalse(parser.canHandle("Paid to Somewhere UPI transaction ID 123 google pay"))
    }

    @Test
    fun `parses all rows with amounts dates and refs`() {
        val txns = parser.parse(statement)
        assertEquals(6, txns.size)

        val interest = txns[0]
        assertEquals(BigDecimal("1.23"), interest.amount)
        assertEquals(TransactionType.INCOME, interest.type)
        assertEquals("111122223333", interest.reference)
        assertEquals("5678", interest.accountLast4)
        assertEquals("Slice", interest.bankName)
        assertEquals(BigDecimal("10001.23"), interest.balance)

        // 02 Jan 2026 noon IST = 2026-01-02T06:30Z
        assertEquals(1767335400000L, interest.timestamp)
    }

    @Test
    fun `pot movements are transfers not income or spending`() {
        val txns = parser.parse(statement)
        val potIn = txns[1]      // Transfer from Holiday fund atom
        val autoSave = txns[3]   // Auto save to Daily saver atom
        val roundUps = txns[4]   // Round ups

        assertEquals(TransactionType.TRANSFER, potIn.type)
        assertEquals("Holiday fund", potIn.merchant)
        assertEquals(TransactionType.TRANSFER, autoSave.type)
        assertEquals("Daily saver", autoSave.merchant)
        assertEquals(TransactionType.TRANSFER, roundUps.type)
        assertEquals(BigDecimal("18.2"), roundUps.amount)
    }

    @Test
    fun `multiline upi debit reassembles merchant between ref and ifsc`() {
        val txns = parser.parse(statement)
        val upi = txns[2]
        assertEquals(TransactionType.EXPENSE, upi.type)
        assertEquals(BigDecimal("5000"), upi.amount)
        assertEquals("2026010212345678", upi.reference)
        // Wrapped mid-word: joined with a space, never glued or truncated.
        assertEquals("SOME MERCHA NT NAME", upi.merchant)
    }

    @Test
    fun `single line upi debit parses merchant and amount`() {
        val txns = parser.parse(statement)
        val upi = txns[5]
        assertEquals(TransactionType.EXPENSE, upi.type)
        assertEquals(BigDecimal("560.9"), upi.amount)
        assertEquals("OTHER SHOP", upi.merchant)
    }

    @Test
    fun `per page footers do not truncate multipage statements`() {
        // Page 1 ends with the "Generated on" footer, page 2 continues with
        // more rows — everything after a mid-stream footer must still parse.
        val multiPage = """
            A/C number 999900005678
            DATE DETAILS REF NO. AMOUNT BALANCE
            02 Jan '26 Interest Cr. for 01-Jan-2026 111122223333 ₹1.23 ₹10,001.23
            Generated on 31 Jan '26
            2/2
            05 Jan '26 UPI-Debit-999900003333-PAGE TWO SHOP-WXYZ0AB1234-x@upi-1 2026010512345680 -₹200 ₹9,801.23
            Generated on 31 Jan '26
        """.trimIndent()
        val txns = parser.parse(multiPage)
        assertEquals(2, txns.size)
        assertEquals("PAGE TWO SHOP", txns[1].merchant)
    }

    @Test
    fun `transaction straddling a page boundary is not dropped`() {
        // The nasty case: a multi-line UPI row starts at the bottom of page 1,
        // the full page furniture repeats (footer, help line, range header —
        // which itself starts with a date — page marker, table header), and the
        // amount tail only arrives on page 2. The block must survive all of it.
        val straddling = """
            A/C number 999900005678
            DATE DETAILS REF NO. AMOUNT BALANCE
            05 Jan '26 UPI-Debit-999900004444-SPLIT ACROSS
            Generated on 31 Jan '26
            Need help? Contact our support team at help@slice.bank.in or +91-0000000000 slice small finance bank
            01 Jan '26 - 31 Jan '26
            2/2
            DATE DETAILS REF NO. AMOUNT BALANCE
            PAGES SHOP-WXYZ0AB1234-y@upi-2
            2026010512345681 -₹300 ₹9,501.23
        """.trimIndent()
        val txns = parser.parse(straddling)
        assertEquals(1, txns.size)
        assertEquals("SPLIT ACROSS PAGES SHOP", txns[0].merchant)
        assertEquals(BigDecimal("300"), txns[0].amount)
        assertEquals("2026010512345681", txns[0].reference)
    }

    @Test
    fun `pot rows normalize regardless of casing`() {
        val shouty = """
            A/C number 999900005678
            DATE DETAILS REF NO. AMOUNT BALANCE
            03 Jan '26 AUTO SAVE TO Daily saver ATOM 444455559 -₹50 ₹9,751.23
        """.trimIndent()
        val txns = parser.parse(shouty)
        assertEquals(1, txns.size)
        assertEquals(TransactionType.TRANSFER, txns[0].type)
        assertEquals("Daily saver", txns[0].merchant)
    }

    @Test
    fun `summary figures above the table are not transactions`() {
        // The opening/closing summary row also contains ₹ figures; none of the
        // parsed transactions may come from it.
        val txns = parser.parse(statement)
        assertTrue(txns.none { it.amount == BigDecimal("10000") && it.reference == null })
        assertNull(txns.firstOrNull { it.smsBody.contains("Opening balance") })
    }
}
