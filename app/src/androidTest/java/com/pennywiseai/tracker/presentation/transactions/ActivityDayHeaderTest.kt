package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.debug.UiVerificationActivity
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class ActivityDayHeaderTest {
    @get:Rule val compose = createAndroidComposeRule<UiVerificationActivity>()

    private fun inOutLine(moneyIn: String, moneyOut: String) = compose.activity.getString(
        R.string.txn_list_day_in_out,
        CurrencyFormatter.formatCurrency(BigDecimal(moneyIn), "USD"),
        CurrencyFormatter.formatCurrency(BigDecimal(moneyOut), "USD")
    )

    @After
    fun showAmounts() {
        AmountPrivacy.hidden = false
    }

    @Test
    fun balancedDayShowsItsInAndOut() {
        val inOut = listOf(DayInOut("USD", BigDecimal("100"), BigDecimal("100")))
        compose.setContent {
            MaterialTheme { ActivityDayInOut(inOut = inOut) }
        }
        compose.onNodeWithText(inOutLine("100", "100")).assertIsDisplayed()
    }

    @Test
    fun hiddenAmountsShowOneMaskedLineHoweverManyCurrencies() {
        AmountPrivacy.hidden = true
        val inOut = listOf(
            DayInOut("USD", BigDecimal("100"), BigDecimal("40")),
            DayInOut("EUR", BigDecimal("20"), BigDecimal("10"))
        )
        compose.setContent {
            MaterialTheme { ActivityDayInOut(inOut = inOut) }
        }
        val masked = compose.activity.getString(R.string.txn_list_day_in_out, AmountPrivacy.MASK, AmountPrivacy.MASK)
        compose.onAllNodesWithText(masked, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theDateAndItsNetAreOneHeading() {
        val net = listOf(Money(BigDecimal("60"), "USD"))
        compose.setContent {
            MaterialTheme { ActivityDayHeader(title = "Mon, Nov 9", net = net) }
        }
        compose.onNode(isHeading() and hasText("Mon, Nov 9") and hasText(formatDayNet(net)!!))
            .assertIsDisplayed()
    }

    @Test
    fun inAnRtlUiAnEnglishLineKeepsItsOrderAndSitsOnTheLeft() {
        val inOut = listOf(DayInOut("USD", BigDecimal("100"), BigDecimal("40")))
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { ActivityDayInOut(inOut = inOut) }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(inOutLine("100", "40"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        assertEquals(TextAlign.Left, layout.layoutInput.style.textAlign)
    }
}
