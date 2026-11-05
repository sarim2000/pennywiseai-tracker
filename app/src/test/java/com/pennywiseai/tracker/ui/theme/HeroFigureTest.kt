package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroFigureTest {

    /** The parts of the text that were set small. */
    private fun softened(text: String): List<String> {
        val figure = PennyWiseText.heroFigure(text, Color.Gray)
        assertEquals(text, figure.text)
        return figure.spanStyles.map { text.substring(it.start, it.end) }
    }

    @Test
    fun `a leading currency is softened and the digits are not`() {
        assertEquals(listOf("JD"), softened("JD1,585.930"))
        assertEquals(listOf("$"), softened("$800.00"))
    }

    @Test
    fun `a trailing currency is softened`() {
        assertEquals(listOf(" €"), softened("1.234,56 €"))
    }

    @Test
    fun `the sign stays with the figure`() {
        assertEquals(listOf("JD"), softened("−JD41.350"))
        assertEquals(listOf("JD"), softened("+JD250.000"))
    }

    @Test
    fun `a masked figure has nothing to soften`() {
        assertEquals(emptyList<String>(), softened("••••••"))
    }
}
