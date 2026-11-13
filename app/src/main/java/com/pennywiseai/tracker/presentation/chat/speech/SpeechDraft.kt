package com.pennywiseai.tracker.presentation.chat.speech

/**
 * Text already in the field ([base]) plus the phrase the recognizer is still hearing ([heard]).
 * Partials replace [heard]; they are not appended, because each partial is the full phrase so far.
 */
data class SpeechDraft(val base: String, val heard: String) {
    fun withHeard(text: String): SpeechDraft = copy(heard = text.trim())

    fun display(): String = when {
        heard.isBlank() -> base
        base.isBlank() -> heard.trim()
        else -> base.trimEnd() + " " + heard.trim()
    }

    /** Fold the phrase into [base] once the recognizer finishes or the user stops. */
    fun commit(): SpeechDraft = SpeechDraft(display(), "")
}
