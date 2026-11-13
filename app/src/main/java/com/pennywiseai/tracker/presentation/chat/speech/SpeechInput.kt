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

/**
 * On-device recognizer. [stopListening] ends capture and still delivers a final
 * result. [cancel] drops any callback still in flight.
 */
interface SpeechEngine {
    fun available(): Boolean
    fun start(callbacks: SpeechCallbacks)

    /** @return false when there is no live recognizer to end, or ending it threw. */
    fun stopListening(): Boolean
    fun cancel()
}

/**
 * One dictation at a time. [finish] keeps the session until the recognizer's final
 * result, so the last words are not thrown away. [start], [stop], [discard] and
 * [keepTyped] retire the session, and a late partial cannot overwrite what the user
 * just typed.
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

    /** User tapped Stop and the recognizer has not answered yet. */
    private var awaitingFinal = false

    fun available(): Boolean = engine.available()

    fun start(currentText: String) {
        if (!engine.available()) {
            failure = SpeechFailure.UNAVAILABLE
            return
        }
        val mine = ++session
        awaitingFinal = false
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
                    awaitingFinal = false
                    val next = if (text.isBlank()) draft.commit() else draft.withHeard(text).commit()
                    publish(next)
                    listening = false
                },
                onError = { code ->
                    if (session != mine) return@SpeechCallbacks
                    val askedToStop = awaitingFinal
                    session++
                    awaitingFinal = false
                    val kind = speechFailure(code)
                    val kept = draft.heard.isNotBlank()
                    if (kept) publish(draft.commit())
                    listening = false
                    // A timeout after we already have words is a normal end, not a failure.
                    // stopListening() before the recognizer is ready reports CLIENT, which
                    // is the Stop tap itself, not a failure the user needs to read.
                    failure = when {
                        kept && kind.isBenignEnd() -> null
                        askedToStop && kind == SpeechFailure.UNKNOWN -> null
                        else -> kind
                    }
                },
            ),
        )
    }

    /**
     * User tapped Stop. Ask the recognizer to finish so audio it has not reported
     * yet is still transcribed. A second tap gives up and keeps the last partial.
     * The heard phrase is not folded into the base yet: the final result is the
     * whole phrase, and committing first would append it twice.
     */
    fun finish() {
        if (!listening) return
        if (awaitingFinal) {
            stop()
            return
        }
        awaitingFinal = true
        if (!engine.stopListening()) stop()
    }

    /**
     * Drop the recognizer now and keep the last partial. Leaving the screen,
     * covering the composer, and a Stop that cannot finish use this.
     */
    fun stop() {
        if (!listening) return
        session++
        awaitingFinal = false
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
        awaitingFinal = false
        engine.cancel()
        draft = SpeechDraft("", "")
        published = ""
        listening = false
    }

    /** User edited the field while we were listening, so the field wins. */
    fun keepTyped(text: String) {
        if (!listening) return
        session++
        awaitingFinal = false
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
        awaitingFinal = false
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
