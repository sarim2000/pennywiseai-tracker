package com.pennywiseai.tracker.ui.components.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.CarryGap
import com.pennywiseai.tracker.domain.spending.BudgetRollover
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.readableOn
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val color = groupSpending.group.budget.color.toColorOr(MaterialTheme.colorScheme.primary)
    GroupedRow(
        position = ListItemPosition.Single,
        modifier = modifier,
        containerColor = color.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow),
        onClick = onClick
    ) {
        BudgetStatusContent(groupSpending, currency)
    }
}

@Composable
internal fun BudgetStatusContent(
    spending: BudgetGroupSpending,
    currency: String,
    hero: Boolean = false,
    dense: Boolean = false,
    /** One line and a dense meter: name, status and the one amount (Home's rows). */
    compact: Boolean = false,
    headingAction: @Composable () -> Unit = {}
) {
    val budget = spending.group.budget
    val isLimit = budget.groupType == BudgetGroupType.LIMIT
    val tracksSpending = budget.groupType != BudgetGroupType.TARGET
    val isOver = spending.hasLimit && spending.remaining.signum() < 0
    val scheme = MaterialTheme.colorScheme
    val color = budget.color.toColorOr(scheme.primary)
    val statusColor = when {
        !tracksSpending -> scheme.success
        isOver || spending.percentageUsed >= 90f -> scheme.error
        spending.percentageUsed >= 70f -> scheme.warning
        else -> scheme.success
    }
    val actual = CurrencyFormatter.formatCurrency(spending.totalActual, currency)
    val limit = CurrencyFormatter.formatCurrency(spending.totalBudget, currency)
    val today = LocalDate.now()
    val days = spending.daysRemaining
    val timing = when {
        today.isAfter(spending.windowEnd) -> stringResource(R.string.budgets_finished)
        today.isBefore(spending.windowStart) -> stringResource(
            R.string.plan_budget_starts,
            spending.windowStart.format(AppDates.dayMonthFormatter())
        )
        days > 0 -> pluralStringResource(R.plurals.plan_budget_days_left, days, days)
        else -> null
    }

    val amountText = when (budget.groupType) {
        BudgetGroupType.LIMIT -> if (spending.hasLimit) {
            stringResource(
                if (isOver) R.string.budget_card_over_budget else R.string.plan_budget_remaining,
                CurrencyFormatter.formatCurrency(spending.remaining.abs(), currency)
            )
        } else {
            stringResource(R.string.budgets_spent, actual)
        }
        BudgetGroupType.TARGET -> stringResource(R.string.goals_card_saved, actual)
        BudgetGroupType.EXPECTED -> if (spending.hasLimit) {
            stringResource(R.string.plan_budget_expected, limit)
        } else {
            stringResource(R.string.budgets_spent, actual)
        }
    }
    val amountColor = when {
        hero -> scheme.onPrimaryContainer
        tracksSpending && isOver && !AmountPrivacy.hidden -> scheme.error
        else -> scheme.onSurface
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier.weight(1f),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(budget.name, style = MaterialTheme.typography.titleMedium)
                if (spending.hasLimit && !AmountPrivacy.hidden) {
                    BudgetStatusChip(
                        text = stringResource(
                            when {
                                !tracksSpending && spending.remaining.signum() <= 0 -> R.string.goals_reached
                                isOver && isLimit -> R.string.plan_budget_status_over
                                isOver && tracksSpending -> R.string.plan_budget_over_estimate
                                tracksSpending && spending.percentageUsed >= 70f -> R.string.plan_budget_status_close
                                else -> R.string.plan_budget_status_on_track
                            }
                        ),
                        color = statusColor
                    )
                }
            }
            if (compact) Text(amountText, style = PennyWiseText.amountRow, color = amountColor, maxLines = 1)
            headingAction()
        }

        if (!compact) {
            Text(
                text = amountText,
                style = if (hero) PennyWiseText.heroAmount else PennyWiseText.amountLarge,
                color = amountColor
            )
        }

        if (spending.hasLimit && !AmountPrivacy.hidden) {
            WavyMeter(
                fraction = spending.percentageUsed / 100f,
                color = when {
                    hero -> scheme.onPrimaryContainer
                    tracksSpending && (isOver || spending.percentageUsed >= 70f) -> statusColor
                    else -> color
                },
                trackColor = scheme.surfaceContainerHighest,
                dense = dense || compact
            )
        }
        if (compact) {
            if (spending.needsExchangeRate(currency)) {
                BudgetStatusChip(stringResource(R.string.plan_budget_missing_rate), scheme.warning)
            }
            return@Column
        }

        val summary = when {
            !spending.hasLimit && isLimit -> stringResource(
                if (spending.isTrackingAllExpenses) R.string.budgets_tracking_all else R.string.plan_budget_tracking
            )
            !spending.hasLimit -> null
            isLimit -> stringResource(R.string.budgets_spent_of, actual, limit)
            budget.groupType == BudgetGroupType.TARGET -> stringResource(R.string.plan_budget_target, limit)
            else -> stringResource(R.string.budgets_spent, actual)
        }
        val supporting = listOfNotNull(summary, timing).joinToString(" · ")
        if (supporting.isNotEmpty()) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hero) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            )
        }
        if (isLimit && days > 0 && !today.isBefore(spending.windowStart) && spending.dailyAllowance.signum() > 0 && !hero) {
            Text(
                stringResource(R.string.budget_card_per_day, CurrencyFormatter.formatCurrency(spending.dailyAllowance, currency)),
                style = PennyWiseText.amountSmall,
                color = scheme.onSurfaceVariant
            )
        }
        if (spending.needsExchangeRate(currency)) {
            BudgetStatusChip(stringResource(R.string.plan_budget_missing_rate), scheme.warning)
        }
    }
}

/** Small tinted pill naming a status: "On track", "Getting close", "Reached". */
@Composable
private fun BudgetStatusChip(text: String, color: Color) {
    val fill = color.copy(alpha = Dimensions.Alpha.categoryTint).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        // The wash pulls the fill toward the ink, so re-check the ink against it.
        color = color.readableOn(listOf(fill)),
        modifier = Modifier
            .background(fill, CircleShape)
            .padding(horizontal = Spacing.smd, vertical = Spacing.xxs),
    )
}

internal fun BudgetGroupSpending.needsExchangeRate(currency: String): Boolean =
    carryIncomplete || (!currency.equals(group.budget.currency, ignoreCase = true) &&
        group.budget.limitAmount.signum() > 0 && !hasLimit)

/**
 * How a rolled-over budget's limit is made up: "Base X + Y carried over = Z", or
 * why nothing carried, or, in the period rollover was turned on in, when it starts.
 * Nothing at all when rollover is off, or carried exactly 0 after its first period.
 * [color]: the "Spent X of Y" line's above it. [onAddRate] opens Exchange Rates, offered when a missing
 * rate stopped the carry.
 */
@Composable
fun RolloverLine(
    groupSpending: BudgetGroupSpending,
    currency: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onAddRate: (() -> Unit)? = null
) {
    val budget = groupSpending.group.budget
    if (!budget.rolloverEnabled) return
    RolloverLine(
        limit = groupSpending.totalBudget,
        carry = groupSpending.carry,
        gap = groupSpending.carryGap,
        currency = currency,
        startsNext = budget.periodType.takeIf {
            BudgetRollover.isFirstPeriod(budget, groupSpending.windowStart, groupSpending.windowEnd)
        },
        modifier = modifier,
        color = color,
        onAddRate = onAddRate
    )
}

/**
 * [RolloverLine] for a [limit] (in [currency]) that already has [carry] in it.
 * [startsNext]: this is rollover's first period, of that cadence.
 */
@Composable
fun RolloverLine(
    limit: BigDecimal,
    carry: BigDecimal,
    gap: CarryGap?,
    currency: String,
    startsNext: BudgetPeriodType? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onAddRate: (() -> Unit)? = null
) {
    val text = when {
        gap != null -> stringResource(gap.message)
        carry.signum() != 0 -> stringResource(
            if (carry.signum() > 0) R.string.budget_rollover_carried_in else R.string.budget_rollover_carried_out,
            CurrencyFormatter.formatCurrency(limit - carry, currency),
            CurrencyFormatter.formatCurrency(carry.abs(), currency),
            CurrencyFormatter.formatCurrency(limit, currency)
        )
        startsNext == BudgetPeriodType.WEEKLY -> stringResource(R.string.budget_rollover_starts_next_week)
        startsNext == BudgetPeriodType.MONTHLY -> stringResource(R.string.budget_rollover_starts_next_month)
        else -> return
    }
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
        if (gap == CarryGap.NO_RATE && onAddRate != null) {
            TextButton(onClick = onAddRate, contentPadding = PaddingValues(horizontal = Spacing.none)) {
                Text(stringResource(R.string.budget_rollover_add_rate))
            }
        }
    }
}

/** Why nothing carried over. */
@get:StringRes
internal val CarryGap.message: Int
    get() = when (this) {
        CarryGap.NO_RATE -> R.string.budget_rollover_incomplete
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
            .background(color = bg, shape = CircleShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    )
}
