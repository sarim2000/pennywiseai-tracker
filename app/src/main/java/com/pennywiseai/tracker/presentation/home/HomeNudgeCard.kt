package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.SpendForecast
import com.pennywiseai.tracker.ui.components.cards.HomeHeroBudget
import com.pennywiseai.tracker.ui.components.cards.SHORT_DATE
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter

/** The owl's one line under the hero, and whether it's bad news. */
internal data class HomeNudge(val message: String, val worried: Boolean)

/**
 * What the owl says: with a budget, where this window should end at the pace so far
 * (in the budget's currency); without one, the month's forecast (in its own currency).
 * Null when there's too little of the period behind to say anything.
 */
@Composable
internal fun homeNudge(budget: HomeHeroBudget?, forecast: SpendForecast?, isBalanceHidden: Boolean): HomeNudge? {
    fun format(amount: java.math.BigDecimal, currency: String) =
        if (isBalanceHidden) MASK else CurrencyFormatter.formatCurrency(amount, currency)
    val pace = budget?.pace
    if (budget != null) {
        if (pace == null) return null
        return if (pace.signum() >= 0) {
            HomeNudge(stringResource(R.string.home_nudge_under_budget, format(pace, budget.currency)), worried = false)
        } else {
            HomeNudge(stringResource(R.string.home_nudge_over_budget, format(pace.abs(), budget.currency)), worried = true)
        }
    }
    if (forecast == null) return null
    val onTrack = stringResource(
        R.string.forecast_on_track,
        format(forecast.projected, forecast.currency),
        forecast.periodEnd.format(SHORT_DATE)
    )
    val over = forecast.overUsual
    return if (over == null) {
        HomeNudge(onTrack, worried = false)
    } else {
        HomeNudge(
            "$onTrack · " + stringResource(R.string.forecast_over_usual, format(over, forecast.currency)),
            worried = true
        )
    }
}

/**
 * The owl's nudge under the hero. With the Ask tab on, the card is a way in to it
 * ("Ask me anything →"); without, it only reads.
 */
@Composable
internal fun HomeNudgeCard(
    nudge: HomeNudge,
    onAsk: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(start = Spacing.smd, top = Spacing.smd, end = Spacing.md, bottom = Spacing.smd),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Mascot(
                size = Dimensions.Icon.hero,
                mood = if (nudge.worried) MascotMood.Worried else MascotMood.Happy,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = nudge.message,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onAsk != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_nudge_ask),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    }
                }
            }
        }
    }
    if (onAsk != null) {
        Surface(
            onClick = onAsk,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content
        )
    }
}

private const val MASK = "••••"
