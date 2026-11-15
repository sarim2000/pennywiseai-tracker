package com.pennywiseai.tracker.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * This context in the app's own language. A widget is drawn from whichever
 * context its session or worker started with, and that can carry the
 * device's language (English) while the app is set to Arabic, which flipped
 * widget text between the two. Older Androids have no per-app language, so
 * there the device's language is the app's.
 */
fun Context.inAppLanguage(): Context = appLanguageContext().withLatinDigits()

private fun Context.appLanguageContext(): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val appLocales = getSystemService(LocaleManager::class.java)?.applicationLocales
    if (appLocales == null || appLocales.isEmpty || resources.configuration.locales == appLocales) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(appLocales) })
}
