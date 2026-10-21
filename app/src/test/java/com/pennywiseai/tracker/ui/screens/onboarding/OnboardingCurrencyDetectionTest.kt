package com.pennywiseai.tracker.ui.screens.onboarding

import com.pennywiseai.tracker.utils.CurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** Candidates are SIM country, network country, device locale country — in that order. */
class OnboardingCurrencyDetectionTest {

    private val supported = CurrencyUtils.getAllSupportedCurrencies()

    @Test
    fun `supported SIM country currency is picked`() {
        assertEquals("JOD", detectCurrency(listOf("jo", "jo", "US"), supported))
    }

    @Test
    fun `unsupported SIM currency falls back to the device locale`() {
        // Swiss SIM (CHF isn't supported), no network country, en_US locale.
        assertEquals("USD", detectCurrency(listOf("ch", "", "US"), supported))
    }

    @Test
    fun `network country is tried before the device locale`() {
        assertEquals("AED", detectCurrency(listOf(null, "ae", "US"), supported))
    }

    @Test
    fun `nothing usable falls back to the default base currency`() {
        assertEquals("INR", detectCurrency(listOf(null, "", "CH"), supported))
    }
}
