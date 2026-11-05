package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CategoryPieWidgetTest {

    private fun spend(vararg amounts: Pair<String, String>) =
        amounts.associate { (currency, amount) -> currency to Money(BigDecimal(amount), currency) }

    @Test
    fun `the base currency wins even when a foreign face value is bigger`() {
        assertEquals("JOD", pieCurrency(spend("INR" to "5000", "JOD" to "40"), "JOD"))
    }

    @Test
    fun `with no spend in the base currency the largest spend is shown`() {
        assertEquals("USD", pieCurrency(spend("USD" to "90", "EUR" to "10"), "JOD"))
    }

    @Test
    fun `a zero base-currency total doesn't take over the pie`() {
        assertEquals("USD", pieCurrency(spend("USD" to "90", "JOD" to "0"), "JOD"))
    }

    @Test
    fun `a lowercase base currency still comes back as its code`() {
        assertEquals("JOD", pieCurrency(spend("JOD" to "40"), "jod"))
    }

    @Test
    fun `with no spend at all it falls back to the base currency`() {
        assertEquals("JOD", pieCurrency(emptyMap(), "JOD"))
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
