package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.pennywiseai.tracker.debug.UiVerificationActivity
import com.pennywiseai.tracker.ui.text.TextFieldCommitScope
import com.pennywiseai.tracker.ui.text.rememberSyncedTextFieldState
import com.pennywiseai.tracker.ui.text.withCommittedText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AmountEntryFieldTest {
    @get:Rule val compose = createAndroidComposeRule<UiVerificationActivity>()

    @Test
    fun submitReadsTextAndAmountEditedInTheSameFrame() {
        var name by mutableStateOf("Budget")
        var amount by mutableStateOf("120")
        lateinit var nameField: TextFieldState
        lateinit var amountField: TextFieldState
        lateinit var submit: () -> Unit
        var saved: Pair<String, String>? = null
        compose.setContent {
            TextFieldCommitScope {
                nameField = rememberSyncedTextFieldState(name) { name = it }
                amountField = rememberAmountEntry(amount, { amount = it }, "INR").state
                submit = withCommittedText { saved = name to amount }
            }
        }
        compose.runOnIdle {
            nameField.setTextAndPlaceCursorAtEnd("New budget")
            amountField.setTextAndPlaceCursorAtEnd("120+35")
            submit()
            assertEquals("New budget" to "155.00", saved)
        }
    }

    @Test
    fun aSumHandsOnItsResultAndAnOutsideAmountReplacesIt() {
        var owner by mutableStateOf("")
        compose.setContent {
            val entry = rememberAmountEntry(owner, { owner = it }, "INR")
            OutlinedTextField(
                state = entry.state,
                inputTransformation = entry.inputTransformation,
                modifier = Modifier.testTag("amount")
            )
        }
        val field = compose.onNodeWithTag("amount")
        listOf("120", "+", "35", "x").forEach { field.performTextInput(it) } // "x" is refused
        compose.runOnIdle { assertEquals("155.00", owner) }
        field.assertTextEquals("120+35")

        compose.runOnIdle { owner = "40" }
        field.assertTextEquals("40")
    }

    @Test
    fun afterARestoreTheFieldShowsWhatTheCallerHolds() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            // A plain remember, like the loan sheet's: it comes back with its first amount.
            var owner by remember { mutableStateOf("5500") }
            val entry = rememberAmountEntry(owner, { owner = it }, "INR")
            OutlinedTextField(state = entry.state, modifier = Modifier.testTag("amount"))
        }
        val field = compose.onNodeWithTag("amount")
        field.performTextInput("1")
        field.assertTextEquals("55001")
        restore.emulateSavedInstanceStateRestore()
        field.assertTextEquals("5500")
    }
}
