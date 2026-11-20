package com.pennywiseai.tracker.ui.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * A [TextFieldState] for a field whose text is owned elsewhere (a ViewModel, a parent's
 * state), for the state-based `TextField(state = …)` overloads: each edit is reported
 * through [onValueChange] before the edit returns, so a Save tapped right after typing
 * reads the new text; a [value] changed from outside (a reset, a loaded draft, a
 * reformatted amount) replaces the text with the cursor at its end.
 *
 * Reject input with an `inputTransformation`, not by ignoring [onValueChange]: an edit the
 * owner drops stays on screen, because the field owns what's typed.
 */
@Composable
fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val report by rememberUpdatedState(onValueChange)
    val echoes = remember { PendingEchoes() }
    // Not saveable: the owner already keeps the text, and a secret typed here stays out of saved state.
    val state = remember {
        TextFieldState(value, onTextChanged = { text ->
            if (!echoes.applying) {
                echoes.sent(text.toString())
                report(text.toString())
            }
        })
    }
    LaunchedEffect(value) {
        if (!echoes.isEcho(value, shown = state.text.toString())) {
            echoes.applying = true
            try {
                state.setTextAndPlaceCursorAtEnd(value)
            } finally {
                echoes.applying = false
            }
        }
    }
    return state
}

/**
 * The edits reported but not yet seen back as `value`. The owner's value can trail the
 * typing by a frame; an echo of an earlier edit must not overwrite newer text.
 *
 * ponytail: matched by text, not by revision. Edits that go away and back within one frame
 * ("x" → "xy" → "x") leave "xy" queued, so an outside reset to exactly "xy" right after is
 * taken for an echo. Clearing the queue there instead would let a slow owner's stale echo
 * overwrite fast typing. Add revisions if an owner ever needs both.
 */
internal class PendingEchoes {
    private val pending = ArrayDeque<String>()

    /** True while the field takes the owner's value: that isn't an edit to report. */
    var applying = false

    fun sent(text: String) = pending.addLast(text)

    /**
     * True if [value] needs no change to a field showing [shown]: it's what the field shows
     * (the owner caught up, so nothing is pending), or one of our own edits coming back late.
     * Anything else is an outside change.
     */
    fun isEcho(value: String, shown: String): Boolean {
        if (value == shown) {
            pending.clear()
            return true
        }
        val i = pending.indexOf(value)
        if (i < 0) {
            pending.clear()
            return false
        }
        repeat(i + 1) { pending.removeFirst() }
        return true
    }
}
