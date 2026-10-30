package com.pennywiseai.tracker.utils

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale-aware date formatting for anything the user reads. */
object AppDates {

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
}
