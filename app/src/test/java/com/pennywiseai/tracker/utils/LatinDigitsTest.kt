package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LatinDigitsTest {

    @Test
    fun `Arabic formats 1 2 3, not Arabic-Indic digits`() {
        val arabic = Locale.forLanguageTag("ar-SA").withLatinDigits()
        assertEquals("ar", arabic.language)
        assertEquals("SA", arabic.country)
        assertEquals("100% 4", String.format(arabic, "%d%% %d", 100, 4))
    }

    @Test
    fun `other languages are left as they are`() {
        val english = Locale.US
        assertEquals(english, english.withLatinDigits())
    }

    @Test
    fun `a count keeps the language's grouping in Latin digits`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA"))
            assertEquals("1234", formatCount(1_234).filter(Char::isDigit))
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.234", formatCount(1_234))
        } finally {
            Locale.setDefault(before)
        }
    }
}
