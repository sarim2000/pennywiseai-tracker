package com.pennywiseai.tracker.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/*
 * Arabic shows 1, 2, 3 — not ١، ٢، ٣. Android formats numbers for "ar" with
 * Arabic-Indic digits, so "%d" in a string resource, String.format and
 * NumberFormat all printed ٤ where amounts beside them read 4. Tagging the
 * locale -u-nu-latn keeps the language and only swaps the digits.
 */

fun Locale.withLatinDigits(): Locale =
    if (language == "ar" && getUnicodeLocaleType("nu") == null)
        Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("nu", "latn").build()
    else this

/** This context with its locales in [withLatinDigits]; the same context when nothing changes. */
fun Context.withLatinDigits(): Context {
    val locales = resources.configuration.locales
    val fixed = LocaleList(*Array(locales.size()) { locales[it].withLatinDigits() })
    if (fixed == locales) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(fixed) })
}

/** Process default (String.format, NumberFormat, SimpleDateFormat without an explicit locale). */
fun useLatinDigitsByDefault() {
    Locale.setDefault(Locale.getDefault().withLatinDigits())
}

/** Any decimal digit (٠-٩, ۰-۹, ०-९…) as 0-9; everything else unchanged. */
fun String.toLatinDigits(): String =
    map { c -> c.digitToIntOrNull()?.let { '0' + it } ?: c }.joinToString("")
