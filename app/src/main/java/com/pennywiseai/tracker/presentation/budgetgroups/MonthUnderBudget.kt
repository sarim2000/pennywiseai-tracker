package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.ui.components.celebration.CelebrationKeys
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import java.time.LocalDate
import java.time.YearMonth

/** How long into the new month the "ended under budget" card stays up. */
const val UNDER_BUDGET_CARD_DAYS = 7

/** Last month ended with every spending limit kept, in [currency]. */
data class MonthUnderBudget(val month: YearMonth, val currency: String) {
    /** Names the moment for both the one-time celebration and the card's dismissal. */
    val key: String get() = CelebrationKeys.budgetMonth(month, currency)
}

/**
 * The win to show on [today]'s Budgets page, or null. [previous] is the month before
 * today's, in one currency (the page's), so nothing is added across currencies. Only
 * spending limits count: a TARGET (savings, investments) or EXPECTED budget isn't one
 * to stay under. Needs at least one limit, some spending against them, and no limit over.
 */
fun monthUnderBudget(today: LocalDate, previous: BudgetOverallSummary, dismissed: String?): MonthUnderBudget? {
    if (today.dayOfMonth > UNDER_BUDGET_CARD_DAYS) return null
    // Monthly limits only: a month's summary shows a Weekly budget's last week and clips a
    // One-time budget to the month, so neither says whether it held.
    // ponytail: weekly and one-time limits neither count nor block; judge their own windows if asked.
    val limits = previous.groups.filter {
        it.group.budget.groupType == BudgetGroupType.LIMIT && it.periodType == BudgetPeriodType.MONTHLY && it.hasLimit
    }
    val kept = limits.isNotEmpty() &&
        limits.any { it.totalActual.signum() > 0 } &&
        limits.all { it.totalActual <= it.totalBudget && !it.hasUnconvertedSpend }
    return MonthUnderBudget(YearMonth.from(today).minusMonths(1), previous.currency)
        .takeIf { kept && it.key != dismissed }
}

/** A small tonal note at the top of the Budgets list. No figures, so it's safe while amounts are hidden. */
@Composable
fun MonthUnderBudgetCard(win: MonthUnderBudget, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(
                size = Dimensions.Icon.extraLarge,
                mood = MascotMood.Happy,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(
                    text = stringResource(R.string.budget_month_kept_title, win.month.format(AppDates.pattern("MMMM"))),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.budget_month_kept_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.budget_month_kept_dismiss),
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}
