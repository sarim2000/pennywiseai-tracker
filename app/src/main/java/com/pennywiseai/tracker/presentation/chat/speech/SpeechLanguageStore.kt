package com.pennywiseai.tracker.presentation.chat.speech

import android.content.Context
import android.os.SystemClock
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
    /** Pack groups, such as `en` or `zh-hans`, with a download in flight. */
    var downloading by mutableStateOf(emptySet<String>())
        private set
    var downloadFailed by mutableStateOf(false)
        private set

    private var requestedTag: String? = null
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
        requestedTag = normalized
        downloadArmedAt = SystemClock.elapsedRealtime()
        downloading = setOf(group)
        packs.download(normalized) { result ->
            when (result) {
                // A newer download() already published its own busy row before the
                // client reports that this one lost the recognizer.
                PackDownloadResult.REPLACED -> Unit
                PackDownloadResult.FAILED -> {
                    if (requestedTag != normalized) return@download
                    downloading = downloading - group
                    requestedTag = null
                    downloadAccepted = false
                    downloadFailed = true
                }
                PackDownloadResult.ACCEPTED -> {
                    requestedTag = normalized
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
        val tag = requestedTag ?: return
        val group = packGroup(tag)
        if (group !in downloading) return
        // The system dialog can sit open for a while. The grace clock starts
        // only once the recognizer accepts the download.
        if (!downloadAccepted) {
            if (SystemClock.elapsedRealtime() - downloadArmedAt >= DOWNLOAD_DIALOG_MS) {
                downloading = emptySet()
                requestedTag = null
            }
            return
        }
        val decision = watchDownload(
            report = next,
            group = group,
            elapsedMs = SystemClock.elapsedRealtime() - downloadArmedAt,
        )
        when {
            decision.installedTag != null -> {
                downloading = emptySet()
                requestedTag = null
                downloadAccepted = false
                save(decision.installedTag)
            }
            !decision.keepWaiting -> {
                downloading = emptySet()
                requestedTag = null
                downloadAccepted = false
            }
        }
    }

    companion object {
        private const val PREFS = "speech_language"
        private const val KEY_TAG = "tag"
        private const val KEY_HINT = "hint_seen"
        private const val DOWNLOAD_DIALOG_MS = 60_000L

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
