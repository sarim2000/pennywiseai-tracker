package com.pennywiseai.tracker.ui.components.cards

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ShortDateLocaleTest {

    // Switching the app language changes the default locale mid-process; the month must follow.
    @Test
    fun shortDate_followsLocaleChange() {
        val original = Locale.getDefault()
        try {
            val date = LocalDate.of(2026, 10, 31)
            Locale.setDefault(Locale.forLanguageTag("de"))
            assertEquals("31 Okt.", date.format(SHORT_DATE))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("31 Oct", date.format(SHORT_DATE))
        } finally {
            Locale.setDefault(original)
        }
    }
}
