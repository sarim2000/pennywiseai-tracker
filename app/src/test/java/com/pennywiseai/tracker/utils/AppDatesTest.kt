package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.preferences.DateFormatStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class AppDatesTest {

    private val oct31 = LocalDate.of(2026, 10, 31)

    // dateFormatStyle is process-wide state; reset after each test.
    @After
    fun reset() {
        AppDates.dateFormatStyle = DateFormatStyle.AUTO
    }

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

    @Test
    fun eachStyle_formatsFullAndShortDates() {
        val expected = mapOf(
            DateFormatStyle.DAY_MONTH_YEAR to ("31 Oct 2026" to "31 Oct"),
            DateFormatStyle.MONTH_DAY_YEAR to ("Oct 31, 2026" to "Oct 31"),
            DateFormatStyle.DMY_NUMERIC to ("31/10/2026" to "31/10"),
            DateFormatStyle.MDY_NUMERIC to ("10/31/2026" to "10/31"),
            DateFormatStyle.ISO to ("2026-10-31" to "10-31"),
        )
        assertEquals(DateFormatStyle.entries.toSet() - DateFormatStyle.AUTO, expected.keys)
        expected.forEach { (style, dates) ->
            assertEquals(style.name, dates.first, oct31.format(AppDates.dateFormatter(Locale.ENGLISH, style)))
            assertEquals(style.name, dates.second, oct31.format(AppDates.dayMonthFormatter(Locale.ENGLISH, style)))
        }
    }

    @Test
    fun dateTime_followsTheChosenStyle() {
        AppDates.dateFormatStyle = DateFormatStyle.DMY_NUMERIC
        val at = LocalDateTime.of(2026, 10, 31, 15, 45)
        assertEquals("31/10/2026 · 3:45 PM", at.format(AppDates.dateTimeFormatter(base = Locale.ENGLISH)))
        assertEquals("Sat, 31/10/2026 · 3:45 PM", at.format(AppDates.dateTimeFormatter(weekday = true, base = Locale.ENGLISH)))
        assertEquals("31/10 · 3:45 PM", at.format(AppDates.dayMonthTimeFormatter(Locale.ENGLISH)))
    }
}
