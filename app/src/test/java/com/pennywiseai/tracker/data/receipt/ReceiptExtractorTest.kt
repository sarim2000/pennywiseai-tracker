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

    private fun scan(vararg lines: String, known: List<String> = emptyList(), currency: String? = "USD") =
        ReceiptExtractor.extract(lines.toList(), known, today, currency)

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
    fun `the whole bill or what was paid beats what's still due, and nothing due isn't a total`() {
        assertEquals(BigDecimal("75.00"), amount("Grand Total AED 75.00", "Balance Due AED 0.00"))
        assertEquals(BigDecimal("60.00"), amount("Amount paid 60.00", "Balance due 40.00"))
        assertNull(amount("TOTAL 0.00", "Bread 3.20"))
    }

    @Test
    fun `a negative total is left out, and a dash between words isn't a minus`() {
        assertNull(amount("TOTAL -12.50 EUR"))
        assertNull(amount("TOTAL -€12.50"))
        assertNull(amount("TOTAL 12.50-"))
        assertEquals(BigDecimal("12.50"), amount("TOTAL - 12.50"))
        // A discount isn't the largest figure on a receipt with no total.
        assertEquals(BigDecimal("18.40"), amount("Bread 3.20", "Bag 18.40", "Promo -50.00"))
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
        // Right after the total's currency, a plain space groups thousands too.
        assertEquals(BigDecimal("1250.00"), amount("GRAND TOTAL AED 1 250.00"))
        assertEquals(BigDecimal("1250.00"), amount("TOTAL", "AED 1 250.00"))
        // Anywhere else it may part two figures (a count and a price), so it's left out.
        assertNull(amount("Total 3 120.00"))
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
    fun `numeric dates only when they read one way`() {
        assertEquals(LocalDate.of(2026, 3, 18), scan("2026-03-18 14:02").date)
        // 18 can't be a month, and December 3rd is still to come.
        assertEquals(LocalDate.of(2026, 3, 18), scan("03/18/2026").date)
        assertEquals(LocalDate.of(2026, 3, 12), scan("Date: 12/03/2026").date)
        assertEquals(LocalDate.of(2026, 2, 2), scan("02/02/2026").date)
        // April 3rd is still to come, so it's March 4th.
        assertEquals(LocalDate.of(2026, 3, 4), scan("03/04/26").date)
        // February 3rd or March 2nd: both past, so it's left for the user, not taken from another line.
        assertNull(scan("03/02/26").date)
        assertNull(scan("03/02/26", "Printed 2026-01-10").date)
    }

    @Test
    fun `the purchase date, not an expiry or a return date`() {
        assertEquals(
            LocalDate.of(2026, 3, 1),
            scan("Card exp 2026-02-28", "Valid until 2026-03-15", "Purchased 2026-03-01").date
        )
        assertEquals(
            LocalDate.of(2026, 3, 2),
            scan("Ref 2026-01-15", "Date 2026-03-02").date
        )
        assertNull(scan("Returns accepted until 2026-03-10").date)
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
    fun `a code touching its figure, and any code the app offers`() {
        assertEquals("AED", scan("TOTAL 27.30AED").currency)
        assertEquals(BigDecimal("27.30"), amount("TOTAL 27.30AED"))
        assertEquals("AED", scan("TOTAL AED27.30").currency)
        assertEquals("RUB", scan("TOTAL RUB 27.30").currency)
        assertEquals(BigDecimal("27.30"), amount("Card fee AED 1.00", "TOTAL RUB 27.30"))
        assertEquals(setOf("INR", "PKR", "LKR", "NPR"), scan("Net payable Rs450.00").currencies)
    }

    @Test
    fun `a total with no currency by it borrows none, except one the receipt declares`() {
        val borrowed = scan("Card fee USD 1.00", "TOTAL 75.00")
        assertEquals(BigDecimal("75.00"), borrowed.amount)
        assertEquals(emptySet<String>(), borrowed.currencies)
        assertEquals("AED", scan("Currency: AED", "TOTAL 75.00").currency)
        assertEquals("EUR", scan("All amounts in EUR", "Bread 3.20", "Bag 18.40").currency)
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
