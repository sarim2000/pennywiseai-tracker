package com.pennywiseai.tracker.utils

import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pennywiseai.tracker.data.preferences.DateFormatStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale-aware date formatting for anything the user reads. */
object AppDates {

    /**
     * The user's date format, pushed from preferences by PennyWiseApplication. Snapshot state
     * so screens already on show (Settings itself, Home on cold start) recompose when it lands.
     */
    var dateFormatStyle: DateFormatStyle by mutableStateOf(DateFormatStyle.AUTO)

    /**
     * The locale to format dates in: the app language, except that Arabic always uses
     * يناير، فبراير… ديسمبر. Android's locale data gives ar-JO/LB/SY/IQ/PS the Levantine
     * names (كانون الثاني، تشرين الأول…) and the Maghreb its own (جانفي، ماي…), but most
     * Arabic speakers read the first set everywhere, so the region is dropped for dates.
     */
    fun locale(base: Locale = Locale.getDefault()): Locale =
        if (base.language == "ar" && base.country.isNotEmpty()) Locale.Builder().setLocale(base).setRegion("").build()
        else base

    /** [DateTimeFormatter.ofPattern] in [locale]. Built per call so it follows app-language changes. */
    fun pattern(pattern: String, base: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, locale(base))

    /** A date with its year ("31 Oct 2026") in the user's [DateFormatStyle]. */
    fun dateFormatter(base: Locale = Locale.getDefault(), style: DateFormatStyle = dateFormatStyle): DateTimeFormatter =
        pattern(fullPattern(base, style), base)

    /** Day and month ("31 Oct") in the user's [DateFormatStyle]. */
    fun dayMonthFormatter(base: Locale = Locale.getDefault(), style: DateFormatStyle = dateFormatStyle): DateTimeFormatter =
        pattern(shortPattern(base, style), base)

    /** [dateFormatter] then the time ("31 Oct 2026 · 3:45 PM"), optionally led by the weekday. */
    fun dateTimeFormatter(weekday: Boolean = false, base: Locale = Locale.getDefault()): DateTimeFormatter =
        pattern((if (weekday) "EEE, " else "") + fullPattern(base, dateFormatStyle) + TIME, base)

    /** [dayMonthFormatter] then the time ("31 Oct · 3:45 PM"). */
    fun dayMonthTimeFormatter(base: Locale = Locale.getDefault()): DateTimeFormatter =
        pattern(shortPattern(base, dateFormatStyle) + TIME, base)

    private const val TIME = " · h:mm a"

    private fun fullPattern(base: Locale, style: DateFormatStyle) = style.full ?: bestPattern(base, "yMMMd", "d MMM yyyy")
    private fun shortPattern(base: Locale, style: DateFormatStyle) = style.short ?: bestPattern(base, "MMMd", "d MMM")

    // Android picks the language's own day/month order; null off-device (unit tests).
    private fun bestPattern(base: Locale, skeleton: String, fallback: String): String =
        DateFormat.getBestDateTimePattern(locale(base), skeleton) ?: fallback
}
