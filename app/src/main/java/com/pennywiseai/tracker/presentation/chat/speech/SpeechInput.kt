package com.pennywiseai.tracker.presentation.chat.speech

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the recognizer reports. Invoked on the main thread. */
class SpeechCallbacks(
    val onPartial: (String) -> Unit,
    val onFinal: (String) -> Unit,
    val onError: (Int) -> Unit,
)

/** On-device recognizer. [cancel] drops any callback still in flight. */
interface SpeechEngine {
    fun available(): Boolean
    fun start(callbacks: SpeechCallbacks)
    fun cancel()
}

/**
 * One dictation at a time. A newer [start], [stop] or [keepTyped] ignores callbacks
 * from the recognizer session it replaced, so a late partial cannot overwrite what
 * the user just typed.
 */
class SpeechInput(private val engine: SpeechEngine) {
    var draft by mutableStateOf(SpeechDraft("", ""))
        private set
    var listening by mutableStateOf(false)
        private set
    var failure by mutableStateOf<SpeechFailure?>(null)
        private set

    /** Bumped only when dictation changes the text, so the field can follow it. */
    var revision by mutableIntStateOf(0)
        private set

    private var session = 0
    private var published = ""

    fun available(): Boolean = engine.available()

    fun start(currentText: String) {
        if (!engine.available()) {
            failure = SpeechFailure.UNAVAILABLE
            return
        }
        val mine = ++session
        failure = null
        draft = SpeechDraft(currentText, "")
        listening = true
        engine.start(
            SpeechCallbacks(
                onPartial = { text ->
                    if (session != mine || text.isBlank()) return@SpeechCallbacks
                    publish(draft.withHeard(text))
                },
                onFinal = { text ->
                    if (session != mine) return@SpeechCallbacks
                    // Retire the session before applying, so a callback queued behind
                    // this result cannot append the phrase or replace the failure.
                    session++
                    val next = if (text.isBlank()) draft.commit() else draft.withHeard(text).commit()
                    publish(next)
                    listening = false
                },
                onError = { code ->
                    if (session != mine) return@SpeechCallbacks
                    session++
                    val kind = speechFailure(code)
                    val kept = draft.heard.isNotBlank()
                    if (kept) publish(draft.commit())
                    listening = false
                    // A timeout after we already have words is a normal end, not a failure.
                    failure = if (kept && kind.isBenignEnd()) null else kind
                },
            ),
        )
    }

    /** User tapped stop. Keep whatever was heard. */
    fun stop() {
        if (!listening) return
        session++
        engine.cancel()
        if (draft.heard.isNotBlank()) {
            val next = draft.commit()
            // The field already shows the last partial. Don't write it again.
            if (next.display() != published) publish(next) else draft = next
        }
        listening = false
    }

    /** Send is taking the text. Drop the recognizer and do not write the field again. */
    fun discard() {
        session++
        engine.cancel()
        draft = SpeechDraft("", "")
        published = ""
        listening = false
    }

    /** User edited the field while we were listening, so the field wins. */
    fun keepTyped(text: String) {
        if (!listening) return
        session++
        engine.cancel()
        draft = SpeechDraft(text, "")
        listening = false
    }

    fun fail(kind: SpeechFailure) {
        failure = kind
    }

    fun clearFailure() {
        failure = null
    }

    fun release() {
        session++
        engine.cancel()
        listening = false
    }

    private fun publish(next: SpeechDraft) {
        draft = next
        published = next.display()
        revision++
    }
}

/** Errors that just mean "the phrase ended", once some text was heard. */
private fun SpeechFailure.isBenignEnd(): Boolean =
    this == SpeechFailure.NO_MATCH || this == SpeechFailure.TIMEOUT || this == SpeechFailure.UNKNOWN

/**
 * Text Send should pass on. While listening, the draft holds a partial the field
 * may not show yet. After listening, the field wins, so an edit or a cleared
 * draft is what gets sent.
 */
internal fun textToSend(field: String, draft: String, listening: Boolean): String =
    (if (listening) draft else field).trim()
