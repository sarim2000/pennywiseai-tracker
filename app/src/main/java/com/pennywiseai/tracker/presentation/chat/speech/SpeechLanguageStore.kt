package com.pennywiseai.tracker.presentation.chat.speech

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The dictation language, shared by Home and Chat. The pack list is read from
 * the on-device recognizer. A choice is written only when the person picks one
 * or a download they started becomes installed.
 */
internal class SpeechLanguageStore(
    context: Context,
    private val packs: AndroidSpeechPackClient = AndroidSpeechPackClient(context),
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var saved by mutableStateOf(prefs.getString(KEY_TAG, null))
        private set
    var hintSeen by mutableStateOf(prefs.getBoolean(KEY_HINT, false))
        private set
    var userTags by mutableStateOf(appContext.speechUserTags())
        private set
    var report by mutableStateOf(SpeechPackReport())
        private set
    var settled by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(emptySet<String>())
        private set
    var downloadFailed by mutableStateOf(false)
        private set

    fun refresh() {
        userTags = appContext.speechUserTags()
        packs.query { next ->
            report = next
            settled = true
        }
    }

    fun save(tag: String) {
        val normalized = normalizeTag(tag) ?: return
        prefs.edit { putString(KEY_TAG, normalized) }
        saved = normalized
        downloadFailed = false
    }

    fun markHintSeen() {
        if (hintSeen) return
        prefs.edit { putBoolean(KEY_HINT, true) }
        hintSeen = true
    }

    fun download(tag: String) {
        val language = languageOf(tag)
        if (language.isEmpty()) return
        downloadFailed = false
        downloading = downloading + language
        packs.download(tag) { ok ->
            downloading = downloading - language
            if (!ok) {
                downloadFailed = true
                return@download
            }
            packs.query { next ->
                report = next
                settled = true
                val menu = speechLanguageMenu(
                    installed = next.installed,
                    pending = next.pending,
                    downloadable = next.downloadable,
                    userTags = userTags,
                    saved = saved,
                )
                val ready = menu.ready.firstOrNull { languageOf(it.tag) == language }
                if (ready != null) save(ready.tag)
            }
        }
    }

    companion object {
        private const val PREFS = "speech_language"
        private const val KEY_TAG = "tag"
        private const val KEY_HINT = "hint_seen"

        @Volatile
        private var instance: SpeechLanguageStore? = null

        fun get(context: Context): SpeechLanguageStore {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: SpeechLanguageStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
