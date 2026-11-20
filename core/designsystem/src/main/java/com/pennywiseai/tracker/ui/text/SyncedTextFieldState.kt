package com.pennywiseai.tracker.ui.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * A [TextFieldState] for a field whose text is owned elsewhere (a ViewModel, a parent's
 * state), for the state-based `TextField(state = …)` overloads: each edit is reported
 * through [onValueChange], and a [value] changed from outside (a reset, a loaded draft,
 * a reformatted amount) replaces the text with the cursor at its end.
 *
 * Reject input with an `inputTransformation`, not by ignoring [onValueChange]: an edit the
 * owner drops stays on screen, because the field owns what's typed.
 */
@Composable
fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    // Not saveable: the owner already keeps the text, and a secret typed here stays out of saved state.
    val state = remember { TextFieldState(value) }
    val echoes = remember { PendingEchoes() }
    val current by rememberUpdatedState(value)
    val report by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (!echoes.isEcho(value) && value != state.text.toString()) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != echoes.last(current)) {
                echoes.sent(text)
                report(text)
            }
        }
    }
    return state
}

/**
 * The edits reported but not yet seen back as `value`. The owner's value can trail the
 * typing by a frame or two; an echo of an earlier edit must not overwrite newer text.
 */
internal class PendingEchoes {
    private val pending = ArrayDeque<String>()

    fun sent(text: String) = pending.addLast(text)

    /** The newest text the owner should hold: the last edit sent, else its [value]. */
    fun last(value: String): String = pending.lastOrNull() ?: value

    /** True if [value] is one of our own edits coming back; anything else is an outside change. */
    fun isEcho(value: String): Boolean {
        val i = pending.indexOf(value)
        if (i < 0) {
            pending.clear()
            return false
        }
        repeat(i + 1) { pending.removeFirst() }
        return true
    }
}
