package com.pennywiseai.tracker.ui.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.startFirstAvailable
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Voice input for chat and quick add (M5). The phone's on-device recognizer by default. A
 * phone language it can't recognize offline (Arabic on Google's recognizer today) can run on
 * the phone's online speech service, but only after the user agrees once. The words only
 * fill a box ([onText]) and, once the user is done, are handed over once ([onDone]): nothing
 * is sent from here, and no audio is kept.
 */
@Stable
class VoiceInput internal constructor(
    private val context: Context,
    private val prefs: SharedPreferences,
) {
    enum class State { Idle, Listening, Finishing }

    /** Something to say in the strip above the box; [arg] is a language tag shown by name. */
    data class Note(@param:StringRes val text: Int, val arg: String? = null, val isError: Boolean = true)

    // ponytail: API 33+ only, where language lists and downloads exist; older phones get no mic.
    val available: Boolean = isAvailable(context)

    var state by mutableStateOf(State.Idle)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var note by mutableStateOf<Note?>(null)
    var menuOpen by mutableStateOf(false)

    /** The one-time "use online" question: for [service] as it was when asked, named [label]. */
    data class OnlineAsk(val tag: String, val service: ComponentName, val label: String, val startAfter: Boolean)

    var askOnline by mutableStateOf<OnlineAsk?>(null)
        private set

    /** On-device languages ready to use. */
    var installed by mutableStateOf(emptyList<String>())
        private set
    var downloadable by mutableStateOf(emptyList<String>())
        private set
    var pending by mutableStateOf(emptyList<String>())
        private set

    /**
     * The user's languages with no on-device model at all: these can only run on the phone's
     * default speech service, online. Empty when that service can't be identified.
     */
    var online by mutableStateOf(emptyList<String>())
        private set

    /** The user's languages: phone, then app, then keyboard languages. Re-read on [refresh]. */
    var mine by mutableStateOf(userLanguages())
        private set

    /** False until the recognizer has said which languages it has (or if it couldn't). */
    private var supportKnown by mutableStateOf(false)

    /**
     * True once the first language query has answered, either way. Until then an online-only
     * language looks on-device, so a mic that starts by itself waits for this.
     */
    var ready by mutableStateOf(false)
        private set

    private var chosen by mutableStateOf(prefs.getString(KEY_LANGUAGE, null))

    /** Bumped on every pick, so a download can tell whether the user chose anything since. */
    private var picks = 0

    /**
     * Languages the mic can use. Until the recognizer answers, or if it can't, the saved pick
     * and the user's languages, unverified: the mic still works and a wrong one says so.
     */
    val usable: List<String>
        get() = if (supportKnown) installed + online else (listOfNotNull(chosen) + mine).distinct()

    /** The language the mic uses: the saved pick while still usable, else the user's own. */
    val language: String? get() = pickLanguage(chosen, usable, mine)

    val active get() = state != State.Idle

    internal var requestPermission: () -> Unit = {}
    internal var onText: (String) -> Unit = {}

    /**
     * The whole text once, when a recording ends with words heard and no error: never per
     * partial, and not on [cancel], so a caller can act on what was said exactly once.
     */
    internal var onDone: (String) -> Unit = {}
    private var recognizer: SpeechRecognizer? = null
    private val downloads = mutableListOf<SpeechRecognizer>()
    private var prefix = ""
    private var heard = ""
    private var pendingStart = false
    private var probe: SpeechRecognizer? = null
    private var userStopped = false
    private val main = Handler(Looper.getMainLooper())

    /** Mic tap: start with what's in the box as the start of the message, or stop. */
    fun toggle(text: String) {
        when (state) {
            State.Listening -> stop()
            State.Finishing -> Unit
            State.Idle -> {
                prefix = text
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    start()
                } else {
                    pendingStart = true
                    requestPermission()
                }
            }
        }
    }

    internal fun onPermission(granted: Boolean) {
        if (granted && pendingStart) start() else if (!granted) note = Note(R.string.voice_error_permission)
        pendingStart = false
    }

    fun select(tag: String) {
        menuOpen = false
        if (tag in online && !onlineAllowed()) {
            val wasListening = state == State.Listening
            if (wasListening) cancel()
            askConsent(tag, startAfter = wasListening)
            return
        }
        chosen = tag
        picks++
        prefs.edit { putString(KEY_LANGUAGE, tag) }
        // Switching mid-sentence says it again in the new language, replacing what was heard.
        if (state == State.Listening) {
            cancel()
            start()
        }
    }

    fun answerOnline(ok: Boolean) {
        val ask = askOnline ?: return
        askOnline = null
        if (!ok) return
        // The default service changed while the dialog was up: ask again, naming the new one.
        if (onlineService() != ask.service) return askConsent(ask.tag, ask.startAfter)
        prefs.edit {
            putString(KEY_ONLINE_OK, ask.service.flattenToString())
            putString(KEY_LANGUAGE, ask.tag)
        }
        chosen = ask.tag
        picks++
        if (ask.startAfter) start()
    }

    private fun askConsent(tag: String, startAfter: Boolean) {
        val service = onlineService() ?: run {
            note = Note(R.string.voice_error_language)
            return
        }
        val label = runCatching {
            context.packageManager.getApplicationInfo(service.packageName, 0).loadLabel(context.packageManager).toString()
        }.getOrDefault(service.packageName)
        askOnline = OnlineAsk(tag, service, label, startAfter)
    }

    fun stop() {
        val r = recognizer ?: return
        r.stopListening()
        userStopped = true
        finishing(r)
    }

    private fun finishing(r: SpeechRecognizer) {
        if (state == State.Finishing) return
        state = State.Finishing
        // A service that never sends its last callback would leave the box locked; keep what was heard.
        main.postDelayed({ finish(r, null) }, FINISH_TIMEOUT_MS)
    }

    /** Leaving the screen: drop the recognizer and keep whatever text already landed. */
    fun cancel() {
        pendingStart = false
        recognizer?.let {
            recognizer = null
            it.cancel()
            it.destroy()
        }
        state = State.Idle
        level = 0f
    }

    internal fun release() {
        cancel()
        probe?.destroy()
        probe = null
        downloads.forEach { it.destroy() }
        downloads.clear()
    }

    private fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val tag = language
        if (tag == null) {
            menuOpen = true
            return
        }
        val service = if (tag in online) onlineService() else null
        if (tag in online && (service == null || !onlineAllowed())) {
            askConsent(tag, startAfter = true)
            return
        }
        note = null
        heard = ""
        userStopped = false
        val r = if (service != null) {
            SpeechRecognizer.createSpeechRecognizer(context, service)
        } else {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        recognizer = r
        r.setRecognitionListener(Listener(r))
        state = State.Listening
        r.startListening(recognizeIntent(tag))
    }

    private fun finish(r: SpeechRecognizer, result: Note?) {
        if (recognizer !== r) return
        recognizer = null
        r.destroy()
        state = State.Idle
        level = 0f
        note = result
        if (result == null && heard.isNotEmpty()) onDone(spoken())
    }

    /** What was in the box before the mic, then the words heard. */
    private fun spoken() = listOf(prefix.trimEnd(), heard).filter { it.isNotEmpty() }.joinToString(" ")

    private inner class Listener(private val r: SpeechRecognizer) : RecognitionListener {
        private fun show(bundle: Bundle) {
            if (recognizer !== r) return
            val text = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (text.isNullOrEmpty()) return
            heard = text
            onText(spoken())
        }

        // Google's on-device recognizer sends the final words as a partial with
        // final_result=true and then an empty onResults, so partials carry the text.
        override fun onPartialResults(partialResults: Bundle) = show(partialResults)

        override fun onResults(results: Bundle) {
            show(results)
            finish(r, null)
        }

        override fun onError(error: Int) {
            if (recognizer !== r) return
            // Heard something, or the user stopped on purpose: no need to say "didn't catch that".
            if ((heard.isNotEmpty() || userStopped) && error in QUIET_ERRORS) return finish(r, null)
            finish(r, noteFor(error))
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            ) {
                refresh()
            }
        }

        override fun onEndOfSpeech() {
            if (recognizer === r) finishing(r)
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (recognizer === r) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * The speech service the user picked in system settings, if it's an installed one.
     * Consent names this service and is asked again if the user switches services.
     */
    private fun onlineService(): ComponentName? {
        val name = runCatching { Settings.Secure.getString(context.contentResolver, "voice_recognition_service") }
            .getOrNull()?.let(ComponentName::unflattenFromString) ?: return null
        val services = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        return name.takeIf { services.any { it.serviceInfo.packageName == name.packageName && it.serviceInfo.name == name.className } }
    }

    private fun onlineAllowed() = onlineService()?.flattenToString()?.let { it == prefs.getString(KEY_ONLINE_OK, null) } == true

    /**
     * Re-reads what the on-device recognizer has, needs downloading, or has queued. A poll
     * ([replace] false) waits for a slow answer instead of cancelling it.
     */
    fun refresh(replace: Boolean = true) {
        if (!available || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!replace && probe != null) return
        mine = userLanguages()
        querySupport()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun querySupport() {
        // One query at a time: a newer one replaces an older one still waiting.
        probe?.destroy()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        probe = r
        r.checkRecognitionSupport(
            recognizeIntent(language ?: Locale.getDefault().toLanguageTag()),
            ContextCompat.getMainExecutor(context),
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    if (probe !== r) return
                    probe = null
                    r.destroy()
                    installed = support.installedOnDeviceLanguages.distinct()
                    pending = support.pendingOnDeviceLanguages.filter { it !in installed }.distinct()
                    downloadable = support.supportedOnDeviceLanguages
                        .filter { it !in installed && it !in pending }
                        .distinct()
                        .sortedBy { displayName(it) }
                    val onDevice = installed + pending + downloadable
                    online = if (onlineService() == null) emptyList() else onlineOnly(mine, onDevice)
                    supportKnown = true
                    ready = true
                }

                override fun onError(error: Int) {
                    if (probe !== r) return
                    probe = null
                    r.destroy()
                    ready = true
                }
            }
        )
    }

    fun download(tag: String) {
        menuOpen = false
        if (!available || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        // API 34+: its own recognizer per download, so a second one can't swallow the first one's callback.
        // API 33 has no callback (so nothing to swallow, and no moment to release): reuse one.
        val r = downloads.takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE }?.firstOrNull()
            ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { downloads += it }
        val picksBefore = picks
        pending = pending + tag
        downloadable = downloadable - tag
        // Android asks first (with the size); the note follows its answer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            r.triggerModelDownload(
                recognizeIntent(tag),
                ContextCompat.getMainExecutor(context),
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) = Unit
                    override fun onSuccess() {
                        done(r)
                        note = Note(R.string.voice_download_done, tag, isError = false)
                        // Switch to it only if the user didn't pick something else meanwhile.
                        if (picks == picksBefore) {
                            chosen = tag
                            prefs.edit { putString(KEY_LANGUAGE, tag) }
                        }
                        refresh()
                    }

                    override fun onScheduled() {
                        done(r)
                        note = Note(R.string.voice_download_scheduled, tag, isError = false)
                        refresh()
                    }

                    override fun onError(error: Int) {
                        done(r)
                        note = Note(R.string.voice_download_failed, tag)
                        refresh()
                    }
                }
            )
        } else {
            // ponytail: no completion callback on API 33; the next menu open re-reads the lists.
            r.triggerModelDownload(recognizeIntent(tag))
            note = Note(R.string.voice_download_scheduled, tag, isError = false)
        }
    }

    /** A download that answered no longer needs its recognizer. */
    private fun done(r: SpeechRecognizer) {
        if (downloads.remove(r)) r.destroy()
    }

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Just the language ("English"), for the chip and the dialog; the menu shows the region too. */
    fun shortName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    fun isOnline(tag: String) = tag in online

    /**
     * Downloads worth listing up front: the user's own languages not already on the phone in some
     * region. The rest (other languages, other English accents) sit behind "Other languages".
     */
    fun isSuggested(tag: String) = suggests(mine, downloadable, tag) && installed.none { sameLanguage(it, tag) }

    /** Phone, app, then keyboard languages, in that order. */
    private fun userLanguages(): List<String> {
        val phone = Resources.getSystem().configuration.locales
        val app = context.resources.configuration.locales
        val imm = context.getSystemService(InputMethodManager::class.java)
        val keyboards = runCatching {
            imm.enabledInputMethodList.flatMap { imm.getEnabledInputMethodSubtypeList(it, true) }
                // Older keyboards only fill the legacy "ar_JO" form.
                .map { sub -> sub.languageTag.ifBlank { @Suppress("DEPRECATION") sub.locale.replace('_', '-') } }
        }.getOrDefault(emptyList())
        return ((0 until phone.size()).map { phone[it].toLanguageTag() } + (0 until app.size()).map { app[it].toLanguageTag() } + keyboards)
            .filter { it.isNotBlank() && it != "und" }
            .distinct()
    }

    private fun recognizeIntent(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    internal companion object {
        const val PREFS = "voice_input"
        const val KEY_LANGUAGE = "language"
        const val KEY_ONLINE_OK = "online_ok"
        const val KEY_ASKED = "mic_asked"
        const val FINISH_TIMEOUT_MS = 5_000L

        val QUIET_ERRORS = setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        /** Whether this phone gets a mic at all; widgets ask before showing one. */
        fun isAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        fun sameLanguage(a: String, b: String) = Locale.forLanguageTag(a).language == Locale.forLanguageTag(b).language

        /**
         * A download is suggested when it is one of [mine] exactly. Only a language of mine
         * with no exact download (a bare "ar" from a keyboard) brings in every region of it,
         * so an en-US phone isn't offered seven Englishes.
         */
        fun suggests(mine: List<String>, downloadable: List<String>, tag: String) = mine.any { m ->
            m.equals(tag, ignoreCase = true) ||
                (sameLanguage(m, tag) && downloadable.none { it.equals(m, ignoreCase = true) })
        }

        /** The saved pick while still usable, else the phone's languages in order (exact tag, then same language). */
        fun pickLanguage(chosen: String?, usable: List<String>, phone: List<String>): String? {
            chosen?.let { if (it in usable) return it }
            phone.forEach { p ->
                usable.firstOrNull { it.equals(p, true) }?.let { return it }
                usable.firstOrNull { sameLanguage(it, p) }?.let { return it }
            }
            return usable.firstOrNull()
        }

        /**
         * The user's languages the on-device recognizer can't do in any region: these can only go
         * online. Regions stay apart (ar-JO, ar-EG); a bare "ar" from a keyboard is dropped when a
         * regional Arabic is already there.
         */
        fun onlineOnly(mine: List<String>, onDevice: List<String>): List<String> {
            val offline = mine.filter { p -> onDevice.none { sameLanguage(it, p) } }
            fun bare(tag: String) = Locale.forLanguageTag(tag).let { it.toLanguageTag() == it.language }
            return offline.filterNot { tag -> bare(tag) && offline.any { it != tag && sameLanguage(it, tag) && !bare(it) } }
        }

        fun noteFor(error: Int) = Note(
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_no_match
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_error_language
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> R.string.voice_error_network
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> R.string.voice_error_busy
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permission
                SpeechRecognizer.ERROR_AUDIO -> R.string.voice_error_audio
                else -> R.string.voice_error_generic
            }
        )
    }
}

/**
 * [onText] gets the whole box text: what was there before the mic, then the words heard.
 * [onDone] gets it once more when the user is done speaking.
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit, onDone: (String) -> Unit = {}): VoiceInput {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences(VoiceInput.PREFS, Context.MODE_PRIVATE) }
    val voice = remember { VoiceInput(context.applicationContext, prefs) }
    val currentOnText by rememberUpdatedState(onText)
    val currentOnDone by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        voice.onPermission(it)
    }
    voice.onText = { currentOnText(it) }
    voice.onDone = { currentOnDone(it) }
    voice.requestPermission = {
        // Android stops showing the dialog after repeated denials; settings is the only way back.
        val blocked = prefs.getBoolean(VoiceInput.KEY_ASKED, false) && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        if (blocked) {
            context.startFirstAvailable()
        } else {
            prefs.edit { putBoolean(VoiceInput.KEY_ASKED, true) }
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(voice) {
        voice.refresh()
        onDispose { voice.release() }
    }
    return voice
}

/** The mic in the message box: tap to talk or stop, long-press to pick the language. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceMicButton(voice: VoiceInput, enabled: Boolean, onTap: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val pulse by animateFloatAsState(
        if (voice.state == VoiceInput.State.Listening) voice.level else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "micLevel"
    )
    val description = stringResource(if (voice.active) R.string.voice_stop else R.string.voice_start)
    val pick = stringResource(R.string.voice_language)
    Box {
        Box(
            modifier = Modifier
                .size(Dimensions.Component.minTouchTarget)
                .clip(CircleShape)
                .drawBehind {
                    if (voice.active) drawCircle(primary, radius = size.minDimension / 2 * (0.7f + 0.3f * pulse))
                }
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = description,
                    onLongClickLabel = pick,
                    onLongClick = {
                        voice.refresh()
                        voice.menuOpen = true
                    },
                    onClick = onTap
                ),
            contentAlignment = Alignment.Center
        ) {
            when (voice.state) {
                VoiceInput.State.Finishing -> CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.Icon.medium),
                    strokeWidth = 2.dp
                )
                else -> Icon(
                    if (voice.active) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = description,
                    tint = if (voice.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        VoiceLanguageMenu(voice)
    }
    voice.askOnline?.let { ask ->
        val tag = ask.tag
        AlertDialog(
            onDismissRequest = { voice.answerOnline(false) },
            icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
            title = { Text(stringResource(R.string.voice_online_title, voice.shortName(tag))) },
            text = { Text(stringResource(R.string.voice_online_body, voice.shortName(tag), ask.label)) },
            confirmButton = { TextButton(onClick = { voice.answerOnline(true) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.voice_online_confirm)) } },
            dismissButton = { TextButton(onClick = { voice.answerOnline(false) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun VoiceLanguageMenu(voice: VoiceInput) {
    // A queued download turns into a usable language without closing the menu.
    LaunchedEffect(voice.menuOpen, voice.pending) {
        while (voice.menuOpen && voice.pending.isNotEmpty()) {
            delay(PENDING_REFRESH_MS)
            voice.refresh(replace = false)
        }
    }
    var showAll by remember(voice.menuOpen) { mutableStateOf(false) }
    DropdownMenu(expanded = voice.menuOpen, onDismissRequest = { voice.menuOpen = false }) {
        val current = voice.language
        val usable = voice.usable
        usable.forEachIndexed { index, tag ->
            SelectableDropdownMenuItem(
                selected = tag == current,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(voice.displayName(tag))
                        if (voice.isOnline(tag)) {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = stringResource(R.string.voice_online),
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        }
                    }
                },
                onClick = { voice.select(tag) },
                shapes = MenuDefaults.itemShape(index, usable.size),
                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
            )
        }
        val (mine, others) = voice.downloadable.partition(voice::isSuggested)
        val shown = if (showAll) mine + others else mine
        if (voice.pending.isNotEmpty() || voice.downloadable.isNotEmpty()) {
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.voice_download_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {},
                enabled = false
            )
            voice.pending.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(voice.displayName(tag)) },
                    onClick = {},
                    enabled = false,
                    trailingIcon = { Text(stringResource(R.string.voice_download_pending), style = MaterialTheme.typography.labelSmall) }
                )
            }
            shown.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(voice.displayName(tag)) },
                    onClick = { voice.download(tag) },
                    trailingIcon = { Icon(Icons.Default.Download, contentDescription = stringResource(R.string.voice_download)) }
                )
            }
            if (!showAll && others.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.voice_other_languages)) },
                    onClick = { showAll = true },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) }
                )
            }
        }
    }
}

private const val PENDING_REFRESH_MS = 2_000L

/** Replaces the quick questions while the mic is on, or to say what went wrong. */
@Composable
fun VoiceStatusBar(voice: VoiceInput, modifier: Modifier = Modifier) {
    val note = voice.note
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimensions.Padding.content, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val text = when {
            voice.state == VoiceInput.State.Listening -> stringResource(R.string.voice_listening)
            voice.state == VoiceInput.State.Finishing -> stringResource(R.string.voice_finishing)
            note != null -> stringResource(note.text, note.arg?.let(voice::shortName).orEmpty())
            else -> ""
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (!voice.active && note?.isError == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        VoiceLanguageChip(voice)
        if (!voice.active) {
            IconButton(onClick = { voice.note = null }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_dismiss))
            }
        }
    }
}

/** The voice language, always in view next to the quick questions; tap to switch or download. */
@Composable
fun VoiceLanguageChip(voice: VoiceInput) {
    val tag = voice.language
    val online = tag != null && voice.isOnline(tag)
    AssistChip(
        onClick = {
            voice.refresh()
            voice.menuOpen = true
        },
        label = { Text(tag?.let(voice::shortName) ?: stringResource(R.string.voice_language), maxLines = 1) },
        leadingIcon = {
            Icon(
                if (online) Icons.Default.Cloud else Icons.Default.Mic,
                contentDescription = if (online) stringResource(R.string.voice_online) else null,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        },
        trailingIcon = {
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.voice_language), modifier = Modifier.size(Dimensions.Icon.small))
        }
    )
}
