package com.pennywiseai.tracker.presentation.chat.speech

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/** Languages this person actually uses: the phone, the app, then enabled keyboards. */
internal fun Context.speechUserTags(): List<String> =
    userLanguageTags(systemLanguageTags(), appLanguageTags(), keyboardLanguageTags())

private fun Context.systemLanguageTags(): List<String> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val tags = getSystemService(android.app.LocaleManager::class.java)?.systemLocales?.tags().orEmpty()
        if (tags.isNotEmpty()) return tags
    }
    // The app context follows the in-app language. The system resources do not.
    val tags = Resources.getSystem().configuration.locales.tags()
    if (tags.isNotEmpty()) return tags
    return listOf(java.util.Locale.getDefault().toLanguageTag())
}

private fun Context.appLanguageTags(): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
    val locales = getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
    if (locales == null || locales.isEmpty) return emptyList()
    return locales.tags()
}

private fun Context.keyboardLanguageTags(): List<String> {
    val manager = getSystemService(InputMethodManager::class.java) ?: return emptyList()
    val tags = mutableListOf<String>()
    for (method in manager.enabledInputMethodList) {
        val subtypes = manager.getEnabledInputMethodSubtypeList(method, true)
        for (subtype in subtypes) {
            if (subtype.mode != "keyboard") continue
            val tag = subtype.languageTag.ifBlank { subtype.localeTag() }
            if (tag.isNotBlank()) tags.add(tag)
        }
    }
    return tags
}

@Suppress("DEPRECATION")
private fun InputMethodSubtype.localeTag(): String = locale

private fun LocaleList.tags(): List<String> = List(size()) { index -> get(index).toLanguageTag() }
