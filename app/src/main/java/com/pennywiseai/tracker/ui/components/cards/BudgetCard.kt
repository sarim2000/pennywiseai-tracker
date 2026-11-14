package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.domain.spending.budgetPace
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
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

    // One animated fraction drives both the bar and the % pill, so the pill
    // never reads 80% while the bar is still at 40%.
    var targetFraction by remember { mutableFloatStateOf(0f) }
    val usedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        targetFraction = (pctUsed / 100f).coerceAtMost(1f)
    }
    val barFraction = usedFraction.coerceAtLeast(0f)
    // Settled — or once the bar is full, where counting on to e.g. 244% would just
    // tick next to "over budget" — read pctUsed itself (fraction * 100 can also
    // land on 79.999 → "79%").
    val pillPercent = (if (usedFraction == pctUsed / 100f || (usedFraction >= 1f && pctUsed >= 100f)) pctUsed else usedFraction * 100f).toInt()

    // The 70/90% thresholds read the percent the pill is showing, so the colour
    // turns as the bar crosses them rather than before the bar gets there.
    val budgetColor = groupSpending.group.budget.color.toColorOr(MaterialTheme.colorScheme.primary)
    val (statusTarget, _) = budgetStatusColors(pillPercent.toFloat())
    val colorSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val statusColor by animateColorAsState(statusTarget, colorSpec, label = "budgetStatusColor")
    // Text snaps to whichever on-colour reads best on the fill as it is right now.
    // Crossfading light→dark text while the fill goes dark→light meets in an
    // unreadable middle (e.g. Pine dark: primary → tertiary).
    val onStatusColor = with(MaterialTheme.colorScheme) { listOf(onPrimary, onTertiary, onError) }
        .maxBy { contrastRatio(it, statusColor) }
    // The bar wears the budget's own color while it's healthy; the pill and
    // hero text stay semantic so a red budget at 36% doesn't read as danger.
    val barColor by animateColorAsState(
        if (pillPercent >= BUDGET_WARN_PERCENT) statusTarget else budgetColor,
        colorSpec,
        label = "budgetBarColor"
    )

    PennyWiseCardV2(
        modifier = modifier,
        onClick = onClick,
        containerColor = budgetColor.tintedSurface()
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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                Text(
                    text = stringResource(R.string.budget_card_percent, pillPercent),
                    // Tabular figures: the counting percent doesn't jitter in width.
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = onStatusColor,
                    modifier = Modifier
                        .background(
                            color = statusColor,
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Pace only means something for a spending limit whose window is
        // running: started (dayCounts clamps a future window's elapsed to 1)
        // and not yet over (daysRemaining is 0 for past windows).
        val pace = if (
            groupSpending.group.budget.groupType == BudgetGroupType.LIMIT &&
            groupSpending.daysRemaining > 0 &&
            !groupSpending.windowStart.isAfter(LocalDate.now())
        ) {
            budgetPace(groupSpending.totalActual, groupSpending.totalBudget, groupSpending.daysElapsed, groupSpending.windowDays)
        } else null

        // Row 2: Custom rounded progress bar. Stays flat (a data meter); a
        // thin "today" tick marks where an even spend would be.
        if (groupSpending.totalBudget > BigDecimal.ZERO) {
            val barShape = RoundedCornerShape(50)
            val tickColor = MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.Component.paceTickHeight)
                    .drawWithContent {
                        drawContent()
                        if (pace != null) {
                            val f = if (layoutDirection == LayoutDirection.Rtl) 1f - pace.todayFraction else pace.todayFraction
                            val w = Dimensions.Component.paceTickWidth.toPx()
                            val x = (size.width * f).coerceIn(w / 2, size.width - w / 2)
                            drawLine(tickColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = w, cap = StrokeCap.Round)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.Component.progressBarHeight)
                        .clip(barShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = barFraction)
                            .fillMaxHeight()
                            .clip(barShape)
                            .background(barColor)
                    )
                }
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
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ).money(),
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showDaily) {
                Text(
                    text = stringResource(R.string.budget_card_per_day, CurrencyFormatter.formatCurrency(groupSpending.dailyAllowance, currency)),
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
                val rawStart = groupSpending.group.budget.monthStartDay
                val startDay = if (rawStart == com.pennywiseai.tracker.domain.model.BudgetCycle.LAST_WORKING_DAY) {
                    groupSpending.windowEnd.plusDays(1).dayOfMonth
                } else {
                    rawStart ?: groupSpending.windowStart.dayOfMonth
                }
                val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
                when {
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
        // Pace is told in days, not money, so it shows no more than the
        // percentage pill does and needs no AmountPrivacy masking. Over
        // budget it's dropped: the hero line already says so.
        var showPaceInfo by rememberSaveable { mutableStateOf(false) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimensions.Alpha.subtitle)
            )
            if (pace != null && !pace.isOverBudget) {
                Text(
                    text = when {
                        pace.daysOver > 0 -> pluralStringResource(R.plurals.budget_card_days_over_pace, pace.daysOver, pace.daysOver)
                        pace.daysOver < 0 -> (-pace.daysOver).let { pluralStringResource(R.plurals.budget_card_days_under_pace, it, it) }
                        else -> stringResource(R.string.budget_card_on_pace)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pace.daysOver > 0) MaterialTheme.colorScheme.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.paceInfoToggle { showPaceInfo = !showPaceInfo }.wrapContentHeight()
                )
            }
        }
        PaceInfo(
            visible = showPaceInfo && pace != null && !pace.isOverBudget,
            text = stringResource(R.string.budget_pace_explainer) + " " + stringResource(R.string.budget_pace_explainer_days)
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Row 5: Spent X of Y
        Text(
            text = stringResource(
                R.string.budget_card_spent_of,
                CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Small pill that names the budget's cadence at a glance: 🔁 Weekly /
 * 🗓 Monthly / 📌 One-time. Colour-coded so a user can spot which type
 * of budget they're looking at without reading the subtitle.
 */
@Composable
fun CadencePill(periodType: BudgetPeriodType) {
    val (label, bg, fg) = when (periodType) {
        BudgetPeriodType.WEEKLY -> Triple(
            stringResource(R.string.budget_cadence_weekly),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        BudgetPeriodType.MONTHLY -> Triple(
            stringResource(R.string.budget_cadence_monthly),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        BudgetPeriodType.CUSTOM -> Triple(
            stringResource(R.string.budget_cadence_one_time),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private const val BUDGET_WARN_PERCENT = 70
private const val BUDGET_DANGER_PERCENT = 90

/**
 * A budget's status colour at [percent] used — primary, tertiary from 70%, error
 * from 90% — paired with the colour to draw on it, so pill text keeps its contrast.
 */
@Composable
@ReadOnlyComposable
fun budgetStatusColors(percent: Float): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when {
        percent >= BUDGET_DANGER_PERCENT -> error to onError
        percent >= BUDGET_WARN_PERCENT -> tertiary to onTertiary
        else -> primary to onPrimary
    }
}

/** WCAG contrast ratio between two opaque colours (1..21). */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** A whisper of the budget's color over the card surface (#763). */
@Composable
fun Color.tintedSurface(): Color =
    copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

/** Makes a pace label a 48dp button that toggles [PaceInfo] below it. */
@Composable
internal fun Modifier.paceInfoToggle(onToggle: () -> Unit): Modifier = this
    .defaultMinSize(Dimensions.Component.minTouchTarget, Dimensions.Component.minTouchTarget)
    .clickable(onClickLabel = stringResource(R.string.budget_pace_what_is), role = Role.Button, onClick = onToggle)

/** Inline explanation of pace; a polite live region so TalkBack reads it when it opens. */
@Composable
internal fun PaceInfo(visible: Boolean, text: String) {
    AnimatedVisibility(visible = visible, enter = pwExpand(), exit = pwCollapse()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}
