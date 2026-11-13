package com.pennywiseai.tracker.presentation.chat.speech

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The dictation language, shared by Home and Chat. The pack list is read from
 * the on-device recognizer. A choice is written when the person picks one, or
 * when a download they started finishes and they have not picked another
 * language in the meantime.
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
    /** Pack groups, such as `en` or `zh-hans`, with a download in flight. */
    var downloading by mutableStateOf(emptySet<String>())
        private set
    var downloadFailed by mutableStateOf(false)
        private set

    /** Pack the recognizer is still fetching. Independent of [autoSelect]. */
    private var activeTag: String? = null

    /** When false, a finished download must not replace the language the person picked. */
    private var autoSelect = false
    private var downloadArmedAt = 0L
    private var downloadAccepted = false

    fun refresh() {
        userTags = appContext.speechUserTags()
        packs.query(::applyReport)
    }

    fun save(tag: String) {
        val normalized = normalizeTag(tag) ?: return
        prefs.edit { putString(KEY_TAG, normalized) }
        saved = normalized
        downloadFailed = false
        // The in-flight pack may still finish. It must not become the saved language.
        autoSelect = false
    }

    fun markHintSeen() {
        if (hintSeen) return
        prefs.edit { putBoolean(KEY_HINT, true) }
        hintSeen = true
    }

    fun download(tag: String) {
        val normalized = normalizeTag(tag) ?: return
        val group = packGroup(normalized)
        // One pack at a time. A second request would drop the first callback and
        // leave that row on "Downloading…" for the rest of the process.
        if (group.isEmpty() || downloading.isNotEmpty()) return
        downloadFailed = false
        downloadAccepted = false
        activeTag = normalized
        autoSelect = true
        downloadArmedAt = SystemClock.elapsedRealtime()
        downloading = setOf(group)
        packs.download(normalized) { result ->
            if (activeTag != normalized) return@download
            when (result) {
                // A newer download() already owns the busy row.
                PackDownloadResult.REPLACED -> Unit
                PackDownloadResult.FAILED -> {
                    clearDownload()
                    downloadFailed = true
                }
                PackDownloadResult.ACCEPTED -> {
                    // Do not arm autoSelect again. save() may have cleared it
                    // while the system dialog was open.
                    downloadAccepted = true
                    downloadArmedAt = SystemClock.elapsedRealtime()
                    downloading = setOf(group)
                    refresh()
                }
            }
        }
    }

    private fun applyReport(next: SpeechPackReport) {
        report = next
        settled = true
        val tag = activeTag ?: return
        val elapsed = SystemClock.elapsedRealtime() - downloadArmedAt
        if (!downloadAccepted) {
            // The dialog can outlast the spinner, and onSuccess can be the first
            // callback once a slow pack finishes. Hide the row, keep the request.
            if (!downloadRowVisible(accepted = false, elapsedMs = elapsed)) downloading = emptySet()
            return
        }
        val step = stepDownload(
            track = DownloadTrack(
                tag = tag,
                autoSelect = autoSelect,
                accepted = true,
                elapsedMs = elapsed,
            ),
            report = next,
        ) ?: return
        val saveTag = step.saveTag
        clearDownload()
        if (saveTag != null) save(saveTag)
    }

    private fun clearDownload() {
        downloading = emptySet()
        activeTag = null
        autoSelect = false
        downloadAccepted = false
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
