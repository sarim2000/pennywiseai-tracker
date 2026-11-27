package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.debug.UiVerificationActivity
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.CurrencyFormatter
import org.junit.After
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
    fun balancedDayShowsItsInAndOutWithoutANet() {
        val subtotals = DaySubtotals(
            net = emptyList(),
            inOut = listOf(DayInOut("USD", BigDecimal("100"), BigDecimal("100")))
        )
        compose.setContent {
            MaterialTheme { ActivityDayHeader(title = "Mon, Nov 9", subtotals = subtotals) }
        }
        compose.onNodeWithText(inOutLine("100", "100")).assertIsDisplayed()
    }

    @Test
    fun hiddenAmountsShowOneMaskedLineHoweverManyCurrencies() {
        AmountPrivacy.hidden = true
        val subtotals = DaySubtotals(
            net = emptyList(),
            inOut = listOf(
                DayInOut("USD", BigDecimal("100"), BigDecimal("40")),
                DayInOut("EUR", BigDecimal("20"), BigDecimal("10"))
            )
        )
        compose.setContent {
            MaterialTheme { ActivityDayHeader(title = "Mon, Nov 9", subtotals = subtotals) }
        }
        val masked = compose.activity.getString(R.string.txn_list_day_in_out, AmountPrivacy.MASK, AmountPrivacy.MASK)
        compose.onAllNodesWithText(masked, useUnmergedTree = true).assertCountEquals(1)
    }
}
