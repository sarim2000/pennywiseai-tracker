package com.pennywiseai.tracker.data.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Synthetic receipts only: made-up shops, figures and dates. */
class ReceiptExtractorTest {

    private val today = LocalDate.of(2026, 3, 20)

    private fun scan(vararg lines: String, known: List<String> = emptyList(), monthFirst: Boolean = false, currency: String? = "USD") =
        ReceiptExtractor.extract(lines.toList(), known, today, monthFirst, currency)

    private fun amount(vararg lines: String, currency: String? = "USD") = scan(*lines, currency = currency).amount

    @Test
    fun `total wins over subtotal, tax, cash and change`() {
        val result = scan(
            "COFFEE HUT",
            "12 Sample Road",
            "Latte 2 x 4.50 9.00",
            "Subtotal 9.00",
            "Tax 0.45",
            "TOTAL 9.45",
            "Cash 10.00",
            "Change 0.55",
        )
        assertEquals(BigDecimal("9.45"), result.amount)
    }

    @Test
    fun `grand total and amount paid beat a plain total`() {
        assertEquals(BigDecimal("118.00"), amount("Total 100.00", "GST 18.00", "Grand Total 118.00"))
        assertEquals(BigDecimal("52.50"), amount("Total 50.00", "Tip 2.50", "Amount paid 52.50"))
    }

    @Test
    fun `figure on the line under its label`() {
        assertEquals(BigDecimal("27.30"), amount("Subtotal", "26.00", "TOTAL", "27.30"))
    }

    @Test
    fun `comma and dot decimals with thousands separators`() {
        assertEquals(BigDecimal("1234.56"), amount("Total 1,234.56"))
        assertEquals(BigDecimal("1234.56"), amount("Total 1.234,56"))
        assertEquals(BigDecimal("9.45"), amount("Total 9,45"))
        assertEquals(BigDecimal("1200"), amount("Total ₹1,200"))
        assertEquals(BigDecimal("125000.00"), amount("Net payable Rs. 1,25,000.00"))
    }

    @Test
    fun `three digits after the point are fils for a three-decimal currency, thousands otherwise`() {
        assertEquals(BigDecimal("12.500"), amount("TOTAL JOD 12.500"))
        assertEquals("JOD", scan("TOTAL JOD 12.500").currency)
        assertEquals(BigDecimal("12.500"), amount("TOTAL 12.500", currency = "KWD"))
        assertEquals(BigDecimal("12500"), amount("TOTAL 12.500", currency = "EUR"))
        assertEquals(BigDecimal("1234.500"), amount("Total OMR 1,234.500"))
        // No currency printed or known: "12.500" could be either, so it's left out.
        assertNull(amount("TOTAL 12.500", currency = null))
    }

    @Test
    fun `a total with its currency code on the line under the label`() {
        assertEquals(BigDecimal("27.30"), amount("EXCHANGE RATE 100.00", "TOTAL", "AED 27.30"))
        assertEquals("AED", scan("EXCHANGE RATE 100.00", "TOTAL", "AED 27.30").currency)
    }

    @Test
    fun `thousands grouped by spaces`() {
        assertEquals(BigDecimal("1234.56"), amount("TOTAL 1 234,56 EUR"))
        assertEquals(BigDecimal("1234.56"), amount("TOTAL 1\u202F234.56"))
        // A plain space before a decimal point parts two figures.
        assertEquals(BigDecimal("120.00"), amount("Total 3 120.00"))
    }

    @Test
    fun `a labeled total that can't be read leaves the amount out`() {
        assertNull(amount("Bread 3.20", "Cheese 15.20", "TOTAL", "see over"))
        assertNull(amount("1 USD = 3.67 AED", "TOTAL 27.30"))
        assertNull(amount("TOTAL USD 7.44", "TOTAL AED 27.30"))
    }

    @Test
    fun `dates and times on a total line aren't read as amounts`() {
        assertEquals(BigDecimal("15.00"), amount("Total 15.00 12.03.2026 10:45"))
    }

    @Test
    fun `arabic total label`() {
        assertEquals(BigDecimal("42.00"), amount("المجموع الفرعي 40.00", "ضريبة 2.00", "الإجمالي 42.00"))
    }

    @Test
    fun `no total line falls back to the largest amount with cents`() {
        assertEquals(BigDecimal("18.40"), amount("FRESH MART", "Bread 3.20", "Cheese 15.20", "Bag 18.40", "Store 4411"))
    }

    @Test
    fun `junk reads as nothing`() {
        val result = scan("~~~ ###", "1234", "ok")
        assertNull(result.amount)
        assertNull(result.date)
        assertNull(result.currency)
        assertTrue(result.isEmpty)
        assertTrue(scan().isEmpty)
    }

    @Test
    fun `numeric dates, day first unless the region reads month first`() {
        assertEquals(LocalDate.of(2026, 3, 12), scan("Date: 12/03/2026").date)
        assertEquals(LocalDate.of(2026, 3, 4), scan("03/04/26", monthFirst = true).date)
        assertEquals(LocalDate.of(2026, 3, 18), scan("2026-03-18 14:02").date)
        // 18 can't be a month, whatever the region.
        assertEquals(LocalDate.of(2026, 3, 18), scan("03/18/2026").date)
    }

    @Test
    fun `month name dates`() {
        assertEquals(LocalDate.of(2026, 3, 5), scan("05 Mar 2026").date)
        assertEquals(LocalDate.of(2026, 2, 14), scan("February 14, 2026").date)
        assertEquals(LocalDate.of(2026, 1, 9), scan("9-JAN-26").date)
    }

    @Test
    fun `a future date is skipped for the next one`() {
        assertEquals(
            LocalDate.of(2026, 3, 10),
            scan("Return by 10/04/2026", "Date 10/03/2026").date
        )
    }

    @Test
    fun `currency only when one is named`() {
        assertEquals("INR", scan("Total ₹450.00").currency)
        assertEquals("AED", scan("TOTAL AED 75.00").currency)
        assertEquals("EUR", scan("Summe 12,50 €").currency)
        assertEquals("USD", scan("Total US$12.00").currency)
        assertNull(scan("USD 10.00", "EUR 9.00").currency)
    }

    @Test
    fun `the total's own currency, not another one on the receipt`() {
        val result = scan("Card fee USD 1.00", "TOTAL AED 75.00")
        assertEquals(BigDecimal("75.00"), result.amount)
        assertEquals(setOf("AED"), result.currencies)
    }

    @Test
    fun `a shared mark only says which currencies it could be`() {
        assertEquals(setOf("INR", "PKR", "LKR", "NPR"), scan("Net payable Rs. 450.00").currencies)
        assertTrue("USD" in scan("Total $12.00").currencies)
        assertTrue("CAD" in scan("Total $12.00").currencies)
        assertNull(scan("Total $12.00").currency)
    }

    @Test
    fun `merchant is the first header line, matched to a past merchant`() {
        assertEquals("Coffee Hut", scan("COFFEE HUT", "Total 9.45").merchant)
        assertEquals(
            "Green Leaf Grocers",
            scan("TAX INVOICE", "GREEN LEAF", "GROCERS", "Total 9.45", known = listOf("Green Leaf Grocers", "Leaf Bar")).merchant
        )
        assertEquals("Fresh Mart", scan("Welcome to", "Fresh Mart", "Total 1.00").merchant)
    }

    @Test
    fun `merchant matching`() {
        val known = listOf("Coffee Hut Downtown", "Cafe Corner", "BOOK NOOK")
        assertEquals("BOOK NOOK", ReceiptExtractor.matchMerchant("Book Nook", known))
        assertEquals("Coffee Hut Downtown", ReceiptExtractor.matchMerchant("COFFEE HUT", known))
        // A single generic word doesn't pull in another shop.
        assertNull(ReceiptExtractor.matchMerchant("Cafe", known))
        assertNull(ReceiptExtractor.matchMerchant("Pizza Place", known))
    }
}
