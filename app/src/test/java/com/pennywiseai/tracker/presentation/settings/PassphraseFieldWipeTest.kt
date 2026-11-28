package com.pennywiseai.tracker.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A cleared passphrase field can't give the passphrase back through undo. */
@OptIn(ExperimentalFoundationApi::class)
class PassphraseFieldWipeTest {

    @Test
    fun `wiping a field leaves nothing to undo`() {
        val state = TextFieldState()
        state.edit { append("synthetic") }

        state.wipe()
        state.undoState.undo()

        assertEquals("", state.text.toString())
        assertFalse(state.undoState.canUndo)
        assertFalse(state.undoState.canRedo)
    }
}
