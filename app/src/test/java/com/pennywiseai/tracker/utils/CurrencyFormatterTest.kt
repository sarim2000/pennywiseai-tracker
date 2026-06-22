package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
