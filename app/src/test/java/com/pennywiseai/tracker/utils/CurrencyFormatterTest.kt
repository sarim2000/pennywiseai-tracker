package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
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
}
