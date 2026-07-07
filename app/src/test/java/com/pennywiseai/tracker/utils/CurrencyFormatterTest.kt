package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Guards [CurrencyFormatter.resolveAccountCurrency], the rule that decides whether an
 * account's currency comes from its stored value or its bank parser.
 *
 * Regression: account reinserts (edit / update balance) stamp source_type = MANUAL so a
 * rescan won't purge them. That made resolveAccountCurrency trust the stored currency,
 * which for an SMS-tracked non-INR account is the INR default — silently flipping the
 * displayed currency. The reinsert paths now write the *resolved* currency, so these
 * cases must stay stable.
 */
class CurrencyFormatterTest {

    @Test
    fun `manual account trusts its stored currency`() {
        // A user-created account stores the currency it was created with — even when it
        // differs from the bank parser's default.
        assertEquals(
            "NGN",
            CurrencyFormatter.resolveAccountCurrency(
                sourceType = "MANUAL",
                storedCurrency = "NGN",
                bankName = "Cash"
            )
        )
    }

    @Test
    fun `sms-tracked non-INR account ignores stored INR default and uses parser currency`() {
        // The core regression: an SMS-tracked UAE account stores the INR default but must
        // display its parser currency (AED). Both the null and "TRANSACTION" source types
        // (what SMS rows carry) must resolve to the parser value.
        listOf(null, "TRANSACTION", "SMS_BALANCE", "CARD_LINK").forEach { sourceType ->
            assertEquals(
                "source_type=$sourceType should fall back to the bank parser currency",
                "AED",
                CurrencyFormatter.resolveAccountCurrency(
                    sourceType = sourceType,
                    storedCurrency = "INR",
                    bankName = "Liv Bank"
                )
            )
        }
    }

    @Test
    fun `sms-tracked account on an unknown bank defaults to INR`() {
        assertEquals(
            "INR",
            CurrencyFormatter.resolveAccountCurrency(
                sourceType = null,
                storedCurrency = "INR",
                bankName = "Totally Unknown Bank"
            )
        )
    }

    // numberFormatStyle is a process-wide @Volatile field; reset after each test.
    @After
    fun resetNumberFormatStyle() {
        CurrencyFormatter.numberFormatStyle = NumberFormatStyle.AUTO
    }

    // ─── formatByCurrency / sumByCurrency: the multi-currency total guardrail ───
    //
    // These assert on currency symbols and the " · " separator (locale-independent
    // — they come from the symbol map, not NumberFormat grouping), so they're
    // stable on the plain JVM test runtime.

    @Test
    fun `formatByCurrency renders a single currency as one figure`() {
        val out = CurrencyFormatter.formatByCurrency(mapOf("USD" to Money(BigDecimal(600), "USD")))
        assertTrue("expected a \$ figure, got: $out", out.contains("$"))
        assertFalse("single currency must not use the separator: $out", out.contains(" · "))
    }

    @Test
    fun `formatByCurrency joins mixed currencies and never sums across them`() {
        val out = CurrencyFormatter.formatByCurrency(
            mapOf("USD" to Money(BigDecimal(600), "USD"), "INR" to Money(BigDecimal(1250), "INR")),
            signPrefix = "-"
        )
        assertTrue("expected both symbols, got: $out", out.contains("$") && out.contains("₹"))
        assertTrue("expected the ' · ' separator, got: $out", out.contains(" · "))
        // The sign prefix is applied to each currency, not just the first.
        assertEquals("each figure should be signed", 2, out.split("-").size - 1)
    }

    @Test
    fun `formatByCurrency does not double the sign prefix on a net-negative total`() {
        // A currency whose net is negative (refunds > charges) must render as
        // "-<symbol>200", never "--<symbol>200".
        val out = CurrencyFormatter.formatByCurrency(
            mapOf("USD" to Money(BigDecimal(-200), "USD")),
            signPrefix = "-"
        )
        assertFalse("sign prefix must not double: $out", out.contains("--"))
        assertEquals("exactly one sign prefix expected", 1, out.split("-").size - 1)
    }

    @Test
    fun `formatByCurrency keeps the input currency when a single-currency total cancels to zero`() {
        // A USD-only group whose amounts cancel ($300 charge + $300 refund) must
        // still show "$0", not the INR fallback.
        val out = CurrencyFormatter.formatByCurrency(
            mapOf("USD" to Money(BigDecimal.ZERO, "USD"))
            // fallbackCurrency left at its INR default on purpose
        )
        assertTrue("zero should keep the USD symbol, got: $out", out.contains("$"))
        assertFalse("must not fall back to ₹: $out", out.contains("₹"))
    }

    @Test
    fun `formatByCurrency falls back to a single zero when empty`() {
        val out = CurrencyFormatter.formatByCurrency(
            mapOf("USD" to Money(BigDecimal.ZERO, "USD")),
            fallbackCurrency = "USD"
        )
        assertFalse(out.contains(" · "))
        assertTrue("zero should still render in the fallback currency: $out", out.contains("$"))
    }

    // ─── PKR/LKR symbol collision ───
    //
    // Both are valid ISO codes, so `Currency.getInstance()` succeeds and the
    // locale-driven NumberFormat path is normally used — but some runtimes render
    // LKR with a plain "Rs", identical to PKR's symbol, making a mixed PKR+LKR
    // total unreadable. formatCurrency forces the explicit CURRENCY_SYMBOLS value
    // for both so they never collide regardless of runtime ICU data.

    @Test
    fun `formatCurrency renders PKR and LKR with distinct explicit symbols`() {
        val pkr = CurrencyFormatter.formatCurrency(BigDecimal(500), "PKR")
        val lkr = CurrencyFormatter.formatCurrency(BigDecimal(500), "LKR")
        assertTrue("PKR should use its explicit symbol, got: $pkr", pkr.startsWith("Rs"))
        assertTrue("LKR should use its explicit symbol, got: $lkr", lkr.startsWith("රු"))
        assertFalse("PKR and LKR must not render identically", pkr == lkr)
    }

    @Test
    fun `formatByCurrency keeps PKR and LKR distinguishable in a mixed total`() {
        val out = CurrencyFormatter.formatByCurrency(
            mapOf("PKR" to Money(BigDecimal(500), "PKR"), "LKR" to Money(BigDecimal(500), "LKR"))
        )
        assertTrue("expected the PKR symbol, got: $out", out.contains("Rs"))
        assertTrue("expected the LKR symbol, got: $out", out.contains("රු"))
    }

    @Test
    fun `sumByCurrency buckets per currency instead of folding into one`() {
        data class Row(val amount: BigDecimal, val currency: String)
        val rows = listOf(
            Row(BigDecimal(100), "USD"),
            Row(BigDecimal(500), "USD"),
            Row(BigDecimal(1250), "INR")
        )
        val totals = rows.sumByCurrency({ it.currency }, { it.amount })
        assertEquals(BigDecimal(600), totals["USD"]?.amount)
        assertEquals(BigDecimal(1250), totals["INR"]?.amount)
        assertEquals("a mixed list must stay split by currency", 2, totals.size)
    }

    @Test
    fun `Money refuses to add two different currencies`() {
        try {
            Money(BigDecimal(500), "INR") + Money(BigDecimal(10), "USD")
            org.junit.Assert.fail("adding INR + USD should throw, not silently total")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("INR") && e.message!!.contains("USD"))
        }
    }

    @Test
    fun `Money adds within the same currency`() {
        assertEquals(
            Money(BigDecimal(600), "USD"),
            Money(BigDecimal(100), "USD") + Money(BigDecimal(500), "USD")
        )
    }

    // Note: grouping *values* (1,50,000 vs 150,000) can't be asserted here — the plain
    // JVM test runtime uses COMPAT locale data (western grouping for en-IN), whereas
    // Android uses CLDR (Indian grouping). The style→branch selection is instead covered
    // by the abbreviation test below, which shares the same when(style) logic and uses
    // our own locale-independent L/Cr-vs-K/M strings.
    @Test
    fun `abbreviation follows the style`() {
        CurrencyFormatter.numberFormatStyle = NumberFormatStyle.INTERNATIONAL
        assertTrue(
            "INR under INTERNATIONAL abbreviates with M, not Cr",
            CurrencyFormatter.formatAbbreviated(10_000_000.0, "INR").endsWith("M")
        )
        CurrencyFormatter.numberFormatStyle = NumberFormatStyle.INDIAN
        assertTrue(
            "TZS under INDIAN abbreviates with Cr",
            CurrencyFormatter.formatAbbreviated(10_000_000.0, "TZS").endsWith("Cr")
        )
    }

    @Test
    fun `AUTO abbreviation is currency-driven (the shipped default)`() {
        CurrencyFormatter.numberFormatStyle = NumberFormatStyle.AUTO
        // INR/NPR/PKR keep Indian L/Cr; everything else uses western K/M.
        assertTrue(
            "INR under AUTO abbreviates with Cr",
            CurrencyFormatter.formatAbbreviated(10_000_000.0, "INR").endsWith("Cr")
        )
        assertTrue(
            "NPR under AUTO abbreviates with Cr",
            CurrencyFormatter.formatAbbreviated(10_000_000.0, "NPR").endsWith("Cr")
        )
        assertTrue(
            "TZS under AUTO abbreviates with M, not Cr",
            CurrencyFormatter.formatAbbreviated(10_000_000.0, "TZS").endsWith("M")
        )
    }
}
