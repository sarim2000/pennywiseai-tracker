package com.pennywiseai.tracker.presentation.chat.speech

import java.util.Locale

/**
 * One on-device language the sheet can show. [tag] is the recognizer's BCP-47 tag.
 * [pending] means the pack is already scheduled for download.
 */
data class SpeechLanguageChoice(
    val tag: String,
    val pending: Boolean,
)

/**
 * What the mic offers. [selected] is the tag to listen in, or null when nothing
 * is installed. [suggestions] are the user's languages that still need a download.
 */
data class SpeechLanguageMenu(
    val selected: String?,
    val ready: List<SpeechLanguageChoice>,
    val suggestions: List<SpeechLanguageChoice>,
)

/** What [android.speech.SpeechRecognizer.checkRecognitionSupport] reported. */
data class SpeechPackReport(
    val installed: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val downloadable: List<String> = emptyList(),
    val known: Boolean = false,
)

/**
 * Dictation language is not the app language. A saved choice wins when that
 * language is installed. Otherwise [userTags] win, in order: phone languages,
 * then the app language, then keyboards. Suggestions are user languages the
 * recognizer can download and has not installed yet.
 */
internal fun speechLanguageMenu(
    installed: List<String>,
    pending: List<String>,
    downloadable: List<String>,
    userTags: List<String>,
    saved: String?,
): SpeechLanguageMenu {
    val ready = readyChoices(installed, userTags)
    val readyLanguages = ready.map { languageOf(it.tag) }.toSet()
    return SpeechLanguageMenu(
        selected = selectTag(ready, userTags, saved),
        ready = ready,
        suggestions = suggestionChoices(userTags, readyLanguages, pending, downloadable),
    )
}

/** Phone, app, then keyboard tags. One entry per language, blanks dropped. */
internal fun userLanguageTags(
    system: List<String>,
    app: List<String>,
    keyboards: List<String>,
): List<String> {
    val seen = mutableSetOf<String>()
    val tags = mutableListOf<String>()
    for (raw in system + app + keyboards) {
        val tag = normalizeTag(raw) ?: continue
        if (!seen.add(languageOf(tag))) continue
        tags.add(tag)
    }
    return tags
}

/**
 * Tag passed to the recognizer. A known menu wins, including its default when
 * nothing was saved. Before the menu exists, a saved tag is used, then [fallback].
 */
internal fun listeningLanguage(menu: SpeechLanguageMenu?, saved: String?, fallback: String): String {
    val selected = menu?.selected
    if (!selected.isNullOrBlank()) return selected
    if (!saved.isNullOrBlank()) return saved
    return fallback
}

/** Name in the language itself, so Arabic is العربية even in an English app. */
internal fun speechLanguageName(tag: String): String {
    val locale = localeOf(tag)
    val name = locale.getDisplayLanguage(locale).ifBlank { tag }
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** Name in the app language, for TalkBack and the secondary line. */
internal fun speechLanguageUiName(tag: String, ui: Locale = Locale.getDefault()): String {
    val name = localeOf(tag).getDisplayLanguage(ui)
    return name.ifBlank { speechLanguageName(tag) }
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ui) else it.toString() }
}

/** Short mark on the mic. Latin languages use the code. Others use the autonym's first letter. */
internal fun speechLanguageBadge(tag: String): String {
    val locale = localeOf(tag)
    val first = locale.getDisplayLanguage(locale).firstOrNull()
    val latin = first != null && first.code < 128 && first.isLetter()
    return if (latin) locale.language.uppercase(Locale.ROOT).take(2) else first?.toString().orEmpty()
}

private fun readyChoices(installed: List<String>, userTags: List<String>): List<SpeechLanguageChoice> {
    val byLanguage = linkedMapOf<String, MutableList<String>>()
    for (raw in installed) {
        val tag = normalizeTag(raw) ?: continue
        val language = languageOf(tag)
        if (language.isEmpty()) continue
        byLanguage.getOrPut(language) { mutableListOf() }.add(tag)
    }
    val order = mutableListOf<String>()
    for (user in userTags) {
        val language = languageOf(user)
        if (language in byLanguage && language !in order) order.add(language)
    }
    for (language in byLanguage.keys) {
        if (language !in order) order.add(language)
    }
    return order.map { language ->
        val variants = byLanguage.getValue(language)
        val wanted = userTags.firstOrNull { languageOf(it) == language }
        val tag = if (wanted == null) variants.first() else bestVariant(wanted, variants)
        SpeechLanguageChoice(tag, pending = false)
    }
}

private fun suggestionChoices(
    userTags: List<String>,
    readyLanguages: Set<String>,
    pending: List<String>,
    downloadable: List<String>,
): List<SpeechLanguageChoice> {
    val pendingTags = pending.mapNotNull { normalizeTag(it) }
    val downloadTags = downloadable.mapNotNull { normalizeTag(it) }
    val suggestions = mutableListOf<SpeechLanguageChoice>()
    val seen = mutableSetOf<String>()
    for (user in userTags) {
        val language = languageOf(user)
        if (language.isEmpty() || language in readyLanguages || !seen.add(language)) continue
        val pendingMatch = bestVariantOrNull(user, pendingTags)
        if (pendingMatch != null) {
            suggestions.add(SpeechLanguageChoice(pendingMatch, pending = true))
            continue
        }
        val downloadMatch = bestVariantOrNull(user, downloadTags)
        if (downloadMatch != null) suggestions.add(SpeechLanguageChoice(downloadMatch, pending = false))
    }
    return suggestions
}

private fun selectTag(
    ready: List<SpeechLanguageChoice>,
    userTags: List<String>,
    saved: String?,
): String? {
    if (ready.isEmpty()) return null
    if (!saved.isNullOrBlank()) {
        ready.firstOrNull { sameLanguage(it.tag, saved) }?.let { return it.tag }
    }
    for (user in userTags) {
        ready.firstOrNull { sameLanguage(it.tag, user) }?.let { return it.tag }
    }
    return ready.first().tag
}

private fun bestVariant(wanted: String, candidates: List<String>): String =
    bestVariantOrNull(wanted, candidates) ?: candidates.first()

private fun bestVariantOrNull(wanted: String, candidates: List<String>): String? {
    var best: String? = null
    var bestRank = Int.MAX_VALUE
    for (candidate in candidates) {
        val rank = matchRank(wanted, candidate) ?: continue
        if (rank < bestRank) {
            best = candidate
            bestRank = rank
        }
    }
    return best
}

/** Lower is closer. Null when the language itself differs. */
private fun matchRank(wanted: String, candidate: String): Int? {
    if (!sameLanguage(wanted, candidate)) return null
    val left = localeOf(wanted)
    val right = localeOf(candidate)
    val sameTag = normalizeTag(wanted).equals(normalizeTag(candidate), ignoreCase = true)
    return when {
        sameTag -> 0
        left.country.isNotEmpty() && left.country.equals(right.country, ignoreCase = true) -> 1
        else -> 2
    }
}

private fun sameLanguage(left: String, right: String): Boolean {
    val language = languageOf(left)
    return language.isNotEmpty() && language == languageOf(right)
}

internal fun languageOf(tag: String): String = localeOf(tag).language.lowercase(Locale.ROOT)

internal fun normalizeTag(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim().replace('_', '-')
    if (trimmed.equals("und", ignoreCase = true) || trimmed.equals("zz", ignoreCase = true)) return null
    val locale = Locale.forLanguageTag(trimmed)
    if (locale.language.isBlank() || locale.language.equals("und", ignoreCase = true)) return null
    return locale.toLanguageTag()
}

private fun localeOf(tag: String): Locale = Locale.forLanguageTag(tag.replace('_', '-'))
