package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Against a page of a real ICICI savings statement, redacted by the reporter (#874). */
class IciciBankPdfParserTest {

    private val parser = IciciBankPdfParser()

    // The sample starts at the table; a real statement carries the bank name above it.
    private val statement = "ICICI Bank Limited\nStatement of Transactions\n" +
        javaClass.getResource("/statements/icici_savings_sample.txt")!!.readText()

    private val parsed by lazy { parser.parse(statement) }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()

    @Test
    fun `recognises ICICI statements only`() {
        assertTrue(parser.canHandle(statement))
        assertFalse(parser.canHandle("Google Pay transaction statement"))
    }

    @Test
    fun `every row is found with its amount and date`() {
        assertEquals(23, parsed.size)
        assertEquals(BigDecimal("100.00"), parsed.first().amount)
        assertEquals(LocalDate.of(2025, 10, 17), dateOf(parsed.first().timestamp))
        assertEquals(BigDecimal("10.00"), parsed.last().amount)
        assertEquals(LocalDate.of(2025, 10, 25), dateOf(parsed.last().timestamp))
    }

    @Test
    fun `direction comes from the running balance`() {
        // 1,24,118.55 -> 1,24,718.55: the FD interest row is a deposit.
        val interest = parsed.first { it.amount == BigDecimal("600.00") }
        assertEquals(TransactionType.INCOME, interest.type)
        assertEquals("Interest", interest.merchant)
        // 1,25,000.00 -> 1,24,950.00: a withdrawal.
        assertEquals(TransactionType.EXPENSE, parsed[1].type)
        assertEquals(20, parsed.count { it.type == TransactionType.EXPENSE })
        assertEquals(3, parsed.count { it.type == TransactionType.INCOME })
    }

    @Test
    fun `merchant comes from the UPI, card and VSI particulars`() {
        assertEquals("MERCHANT 13", parsed[0].merchant)   // VIN/ before the date
        assertEquals("MERCHANT 01", parsed[1].merchant)   // UPI/
        assertEquals("MERCHANT 07", parsed[7].merchant)   // VSI/ after the date
        assertEquals("MERCHANT 11", parsed[11].merchant)  // payee split over two lines
        assertEquals("MERCHANT 16", parsed[21].merchant)
    }

    @Test
    fun `rows carry the bank and the source text`() {
        assertTrue(parsed.all { it.bankName == "ICICI Bank" && it.currency == "INR" })
        assertTrue(parsed[1].smsBody.contains("UPI/MERCHANT 01"))
        assertFalse(parsed[1].smsBody.contains("MERCHANT 02"))
    }

    @Test
    fun `an opening balance settles the first row's direction`() {
        val withOpening = statement.replace("VIN/MERCHANT 13\n17-10-2025", "B/F 1,25,100.00\nVIN/MERCHANT 13\n17-10-2025")
        assertEquals(TransactionType.EXPENSE, parser.parse(withOpening).first().type)
    }
}
