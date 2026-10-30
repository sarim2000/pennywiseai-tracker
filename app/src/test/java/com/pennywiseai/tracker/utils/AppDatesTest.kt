package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class AppDatesTest {

    private val oct31 = LocalDate.of(2026, 10, 31)

    @Test
    fun arabic_alwaysUsesJanuaryToDecemberNames() {
        for (tag in listOf("ar", "ar-JO", "ar-LB", "ar-IQ", "ar-MA", "ar-DZ", "ar-SA")) {
            assertEquals(tag, "31 أكتوبر", oct31.format(AppDates.pattern("d MMMM", Locale.forLanguageTag(tag))))
        }
    }

    @Test
    fun otherLanguages_keepTheirRegion() {
        val ptBR = Locale.forLanguageTag("pt-BR")
        assertEquals(ptBR, AppDates.locale(ptBR))
        assertEquals("31 Oct", oct31.format(AppDates.pattern("d MMM", Locale.ENGLISH)))
    }
}
