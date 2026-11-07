package com.pennywiseai.tracker.ui.components.cards

import com.pennywiseai.tracker.ui.components.DEFAULT_BUDGET_COLOR
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pctUsed = groupSpending.percentageUsed
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        animatedProgress = (pctUsed / 100f).coerceIn(0f, 1f)
    }

    // Stored colours can be too dark for a dark card (a default blue measured 2.2:1).
    val budgetColor = CategoryMapping.visibleOn(
        groupSpending.group.budget.color.takeUnless { it == DEFAULT_BUDGET_COLOR }.orEmpty().toColorOr(MaterialTheme.colorScheme.primary),
        MaterialTheme.colorScheme.surfaceContainerHighest
    )
    val statusColor: Color = when {
        pctUsed >= 90f -> MaterialTheme.colorScheme.error
        pctUsed >= 70f -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.onSurface
    }
    // The bar wears the budget's own color while it's healthy (that's the identity
    // from #763, now without tinting the whole card); the figures stay semantic so a
    // red budget at 36% doesn't read as danger.
    val barColor = if (pctUsed >= 70f) statusColor else budgetColor

    PennyWiseCardV2(
        modifier = modifier,
        onClick = onClick
    ) {
        // Row 1: Cadence pill + budget name + percentage pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f)
            ) {
                CadencePill(periodType = groupSpending.periodType)
                Text(
                    text = groupSpending.group.budget.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                Text(
                    text = stringResource(R.string.budget_card_percent, pctUsed.toInt()),
                    style = PennyWiseText.amountSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Row 2: Custom rounded progress bar
        if (groupSpending.totalBudget > BigDecimal.ZERO) {
            val barShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.Component.progressBarHeight)
                    .clip(barShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedProgressState)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(barColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Row 3: Remaining amount (hero) + what's left to spend per day.
        // Only for spending limits — "per day" means nothing for a savings
        // target or an expected-income group.
        val remainingAbs = groupSpending.remaining.abs()
        val showDaily = groupSpending.group.budget.groupType == BudgetGroupType.LIMIT &&
            groupSpending.dailyAllowance > BigDecimal.ZERO
        // FlowRow: when there isn't room (narrow card, large font) the
        // per-day label wraps below instead of squeezing the hero amount.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = if (isOverBudget) {
                    stringResource(R.string.budget_card_over_budget, CurrencyFormatter.formatCurrency(remainingAbs, currency))
                } else {
                    stringResource(R.string.budget_card_remaining, CurrencyFormatter.formatCurrency(groupSpending.remaining.coerceAtLeast(BigDecimal.ZERO), currency))
                },
                style = PennyWiseText.amountMedium,
                color = statusColor
            )
            if (showDaily) {
                Text(
                    text = stringResource(R.string.budget_card_per_day, CurrencyFormatter.formatEstimate(groupSpending.dailyAllowance, currency)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Row 4: Per-cadence subtitle — always framed as a renewal
        // countdown ("Resets in X days" / "X days remaining") so the
        // user knows when the budget's window ends. The displayed
        // window comes from the per-budget current window (Weekly's
        // Jun 29..Jul 5 even when the user is on the July page), so the
        // number is consistent across month views.
        val dateFormatter = AppDates.dayMonthFormatter()
        val subtitleText = when {
            groupSpending.daysRemaining == 0 && groupSpending.daysElapsed >= groupSpending.windowDays ->
                stringResource(R.string.budget_card_finished)
            groupSpending.isTrackingAllExpenses ->
                stringResource(R.string.budget_card_tracking_all)
            groupSpending.periodType == BudgetPeriodType.WEEKLY -> {
                val weekday = groupSpending.group.budget.weekStartDay?.let { DayOfWeek.of(it.coerceIn(1, 7)) }
                    ?: DayOfWeek.MONDAY
                // "Resets in X days" = "days until the current week ends".
                // Subtract 1 because the budget renews *after* the last
                // day, so the displayed week has (windowDays - daysRemaining)
                // days left after today. For a Wed-on-a-Mon-start week:
                // today=Wed, days remaining=5 (Thu..Mon), renewal in 4d.
                val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
                val weekdayName = weekday.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                when {
                    renewalIn == 0 -> stringResource(R.string.budget_card_resets_today_weekday, weekdayName)
                    else -> pluralStringResource(R.plurals.budget_card_resets_in_weekday, renewalIn, renewalIn, weekdayName)
                }
            }
            groupSpending.periodType == BudgetPeriodType.MONTHLY -> {
                val startDay = groupSpending.group.budget.monthStartDay
                    ?: groupSpending.windowStart.dayOfMonth
                val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
                when {
                    // The usual 1st goes without saying; only a custom start day needs naming.
                    startDay == 1 && renewalIn == 0 -> stringResource(R.string.budgets_resets_today)
                    startDay == 1 -> pluralStringResource(R.plurals.budgets_resets_in, renewalIn, renewalIn)
                    renewalIn == 0 -> stringResource(R.string.budget_card_resets_today_day, startDay)
                    else -> pluralStringResource(R.plurals.budget_card_resets_in_day, renewalIn, renewalIn, startDay)
                }
            }
            groupSpending.periodType == BudgetPeriodType.CUSTOM -> {
                val range = "${groupSpending.windowStart.format(dateFormatter)} – ${groupSpending.windowEnd.format(dateFormatter)}"
                when {
                    isOverBudget -> stringResource(R.string.budget_card_over_by, CurrencyFormatter.formatCurrency(remainingAbs, currency))
                    groupSpending.daysRemaining > 1 -> (groupSpending.daysRemaining - 1).let { pluralStringResource(R.plurals.budget_card_runs_days_remaining, it, range, it) }
                    groupSpending.daysRemaining == 1 -> pluralStringResource(R.plurals.budget_card_runs_days_remaining, 1, range, 1)
                    else -> stringResource(R.string.budget_card_finished)
                }
            }
            else -> pluralStringResource(R.plurals.budget_card_days_remaining, groupSpending.daysRemaining, groupSpending.daysRemaining)
        }
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.xxs))

        // Row 5: Spent X of Y
        Text(
            text = stringResource(
                R.string.budget_card_spent_of,
                CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small neutral pill that names the budget's cadence: Weekly / Monthly / One-time. */
@Composable
fun CadencePill(periodType: BudgetPeriodType) {
    val label = when (periodType) {
        BudgetPeriodType.WEEKLY -> stringResource(R.string.budget_cadence_weekly)
        BudgetPeriodType.MONTHLY -> stringResource(R.string.budget_cadence_monthly)
        BudgetPeriodType.CUSTOM -> stringResource(R.string.budget_cadence_one_time)
    }
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** A whisper of the budget's color over the card surface (#763). */
@Composable
fun Color.tintedSurface(): Color =
    copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
