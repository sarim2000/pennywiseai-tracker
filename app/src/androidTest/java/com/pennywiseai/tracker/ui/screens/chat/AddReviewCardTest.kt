package com.pennywiseai.tracker.ui.screens.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ReviewRow
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.debug.UiVerificationActivity
import com.pennywiseai.tracker.utils.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AddReviewCardTest {
    @get:Rule val compose = createAndroidComposeRule<UiVerificationActivity>()

    private val categories = listOf(
        CategoryEntity(name = "Food & Dining", color = "#000"),
        CategoryEntity(name = "Others", color = "#000"),
        CategoryEntity(name = "Salary", color = "#000", isIncome = true)
    )

    private fun draft(amount: String, merchant: String, currency: String = "INR") = TransactionDraft(
        amount = BigDecimal(amount), currency = currency, date = LocalDate.now(), merchant = merchant, category = "Food & Dining",
        type = TransactionType.EXPENSE, bankName = null, accountLast4 = null, accountLabel = "Cash / manual",
        sourceText = "synthetic", amountInMessage = true
    )

    private fun show(initial: List<ReviewRow>, dark: Boolean = false, confirmed: () -> Unit = {}): () -> List<ReviewRow> {
        var rows by mutableStateOf(initial)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                AddReviewCard(
                    rows = rows,
                    categories = categories,
                    enabled = true,
                    onToggle = { id -> rows = rows.map { if (it.id == id) it.copy(selected = !it.selected) else it } },
                    onEdit = { id, d -> rows = rows.map { if (it.id == id) it.copy(draft = d, check = null, selected = true) else it } },
                    onConfirm = confirmed,
                    onDismiss = {}
                )
            }
        }
        return { rows }
    }

    @Test fun theButtonCountsTheTickedRowsAndStopsAtNone() {
        show(listOf(ReviewRow(0, draft("120", "Coffee")), ReviewRow(1, draft("300", "Lunch"))))
        compose.onNodeWithText("Add 2").assertIsEnabled()
        val boxes = compose.onAllNodes(isToggleable())
        boxes[0].performClick()
        compose.onNodeWithText("Add 1").assertIsEnabled()
        boxes[1].performClick()
        compose.onNodeWithText("Add 0").assertIsNotEnabled()
    }

    @Test fun aDoubtfulRowWarnsAndStartsUnticked() {
        show(listOf(ReviewRow(0, draft("420", "Coffee"), ReviewRow.Check.AMOUNT_NOT_IN_MESSAGE), ReviewRow(1, draft("300", "Lunch"))), dark = true)
        compose.onNodeWithText("Check the amount", substring = true).assertExists()
        compose.onNodeWithText("Add 1").assertIsEnabled()
        // Ticking is the user's answer to the warning.
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText("Add 2").assertIsEnabled()
    }

    @Test fun editingARowChangesItAndAnswersItsCheck() {
        var confirmed = false
        val rows = show(listOf(ReviewRow(0, draft("420", "Coffee"), ReviewRow.Check.AMOUNT_NOT_IN_MESSAGE))) { confirmed = true }
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit Coffee").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("420").performTextClearance()
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("Amount *").performTextInput("120")
        compose.onNodeWithText("Income").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        val edited = rows().single()
        assertEquals(0, BigDecimal("120").compareTo(edited.draft.amount))
        assertEquals(TransactionType.INCOME, edited.draft.type)
        // The old category isn't an income one, so it moved to one that is.
        assertEquals("Salary", edited.draft.category)
        assertEquals(null, edited.check)
        compose.onAllNodesWithText("Check the amount", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Add").performClick()
        compose.runOnIdle { assertEquals(true, confirmed) }
    }

    @Test fun rowsInTwoCurrenciesKeepTheirOwnAndNoTotalIsShown() {
        show(listOf(ReviewRow(0, draft("120", "Coffee", "INR")), ReviewRow(1, draft("12", "Lunch", "USD"))))
        compose.onNodeWithText(CurrencyFormatter.formatCurrency(BigDecimal("120"), "INR"), substring = true).assertExists()
        compose.onNodeWithText(CurrencyFormatter.formatCurrency(BigDecimal("12"), "USD"), substring = true).assertExists()
        compose.onAllNodesWithText("132", substring = true).assertCountEquals(0)
    }
}
