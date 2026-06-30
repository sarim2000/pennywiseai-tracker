package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.PastWindowSpending
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.CadencePill
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Per-budget history drill-down. Opened from the Budgets page via the
 * 3-dots menu "View this period history" item. Lists every window for
 * the selected (year, month) — for a Weekly budget that's the per-week
 * sub-list; for Monthly / One-time it's a single entry.
 *
 * Each row carries a "Live" or "Frozen as of …" badge so the user can
 * see which weeks are still accumulating spend (current week in the
 * current month) vs which are frozen snapshots.
 */
@Composable
fun BudgetHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PennyWiseScaffold(
        title = state.budget?.name ?: "Budget History",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@PennyWiseScaffold
        }
        val budget = state.budget
        if (budget == null) {
            Box(modifier = Modifier.padding(padding).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Budget not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@PennyWiseScaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content,
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Summary card — period, total spent, displayed window range
            item {
                PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            CadencePill(periodType = budget.periodType)
                            Text(
                                text = monthLabel(state.yearMonth),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "${CurrencyFormatter.formatCurrency(state.totalSpent, state.currency)} of ${CurrencyFormatter.formatCurrency(state.budgetAmount, state.currency)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val longFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
                        Text(
                            text = "Window: ${state.displayedWindowStart.format(longFormatter)} – ${state.displayedWindowEnd.format(longFormatter)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.displayedIsLive) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            LiveBadge()
                        } else {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            FrozenBadge(state.displayedCapDate)
                        }
                    }
                }
            }

            // Per-window list
            item {
                Text(
                    text = when (budget.periodType) {
                        BudgetPeriodType.WEEKLY -> "Per-week breakdown"
                        BudgetPeriodType.MONTHLY -> "Cycle"
                        BudgetPeriodType.CUSTOM -> "Range"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm)
                )
            }
            items(state.windowHistory) { window ->
                HistoryRow(
                    window = window,
                    currency = state.currency,
                    isDisplayed = window.window.start == state.displayedWindowStart &&
                        window.window.end == state.displayedWindowEnd,
                    isCurrentPeriod = state.yearMonth == YearMonth.now()
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    window: PastWindowSpending,
    currency: String,
    isDisplayed: Boolean,
    isCurrentPeriod: Boolean
) {
    val shortFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${window.window.start.format(shortFormatter)} – ${window.window.end.format(shortFormatter)}",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (isDisplayed) {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = CurrencyFormatter.formatCurrency(window.spent, currency),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            if (window.isLive && isCurrentPeriod) {
                LiveBadge()
            } else {
                FrozenBadge(window.capDate)
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Text(
            text = "Live · still accumulating",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun FrozenBadge(capDate: LocalDate) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
            text = "Frozen as of ${capDate.format(formatter)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun monthLabel(yearMonth: YearMonth): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    return yearMonth.format(formatter)
}
