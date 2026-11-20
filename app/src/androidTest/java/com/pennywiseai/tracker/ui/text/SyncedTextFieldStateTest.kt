package com.pennywiseai.tracker.ui.text

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.pennywiseai.tracker.debug.UiVerificationActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncedTextFieldStateTest {
    @get:Rule val compose = createAndroidComposeRule<UiVerificationActivity>()

    @Test
    fun typingReachesTheOwnerAndAnOutsideResetReachesTheField() {
        var owner by mutableStateOf("")
        compose.setContent {
            OutlinedTextField(
                state = rememberSyncedTextFieldState(owner) { owner = it },
                inputTransformation = InputTransformation {
                    if (!asCharSequence().all { it.isDigit() || it == '.' }) revertAllChanges()
                },
                modifier = Modifier.testTag("f"),
            )
        }
        val field = compose.onNodeWithTag("f")
        listOf("12", "x", ".5").forEach { field.performTextInput(it) } // "x" is refused
        compose.runOnIdle { assertEquals("12.5", owner) }
        field.assertTextEquals("12.5")

        compose.runOnIdle { owner = "" }
        field.assertTextEquals("")
    }
}
