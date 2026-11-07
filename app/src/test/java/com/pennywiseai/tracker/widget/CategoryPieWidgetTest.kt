package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CategoryPieWidgetTest {

    private fun spend(vararg amounts: Pair<String, String>) =
        amounts.associate { (currency, amount) -> currency to Money(BigDecimal(amount), currency) }

    /** USD is worth 83 INR. Anything else has no rate. */
    private fun currencyOfPie(spend: Map<String, Money>, base: String) = runBlocking {
        pieCurrency(spend, base) { money ->
            when (money.currency) {
                "USD" -> money.amount * BigDecimal(83)
                "INR" -> money.amount
                else -> null
            }
        }
    }

    @Test
    fun `the base currency wins even when a foreign face value is bigger`() {
        assertEquals("JOD", currencyOfPie(spend("INR" to "5000", "JOD" to "40"), "JOD"))
    }

    @Test
    fun `with no spend in the base currency the largest value wins, not the largest number`() {
        assertEquals("USD", currencyOfPie(spend("INR" to "5000", "USD" to "500"), "JOD"))
    }

    @Test
    fun `a currency with no rate ranks below one that has a rate`() {
        assertEquals("INR", currencyOfPie(spend("XAF" to "90000", "INR" to "10"), "JOD"))
    }

    @Test
    fun `with no rate at all the largest face value wins`() {
        assertEquals("XAF", currencyOfPie(spend("XOF" to "10", "XAF" to "90"), "JOD"))
    }

    @Test
    fun `a zero base-currency total doesn't take over the pie`() {
        assertEquals("USD", currencyOfPie(spend("USD" to "90", "JOD" to "0"), "JOD"))
    }

    @Test
    fun `a lowercase base currency still comes back as its code`() {
        assertEquals("JOD", currencyOfPie(spend("JOD" to "40"), "jod"))
    }

    @Test
    fun `with no spend at all it falls back to the base currency`() {
        assertEquals("JOD", currencyOfPie(emptyMap(), "JOD"))
    }

    @Test
    fun `only colors a theme background would swallow follow the text color`() {
        assertTrue(isLowContrast(0xFF000000.toInt()))
        assertTrue(isLowContrast(0xFF1C1B1F.toInt()))
        assertTrue(isLowContrast(0xFFFFFFFF.toInt()))
        // The usual category colors and the grey "Other" keep their own.
        listOf(0xFFFF7043, 0xFF42A5F5, 0xFFAB47BC, 0xFF26A69A, 0xFF9E9E9E, 0xFF424242).forEach {
            assertFalse(it.toString(16), isLowContrast(it.toInt()))
        }
    }
}
