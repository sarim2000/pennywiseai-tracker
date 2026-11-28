package com.pennywiseai.tracker.presentation.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.trip.TripStatus
import com.pennywiseai.tracker.data.trip.TripSummaryFigures
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter

/**
 * A trip's spending at a glance: total, days, average per day and top categories, each per
 * currency (never summed across). Titled "so far" while running and "summary" once ended.
 */
@Composable
fun TripSummaryCard(figures: TripSummaryFigures, status: TripStatus, modifier: Modifier = Modifier) {
    PennyWiseCardV2(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(if (status == TripStatus.ENDED) R.string.trip_summary_title_ended else R.string.trip_summary_title_running),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            FigureRow(stringResource(R.string.trip_summary_total), CurrencyFormatter.formatByCurrency(figures.totalByCurrency))
            FigureRow(
                stringResource(R.string.trip_summary_days),
                figures.totalDays?.let { stringResource(R.string.trip_summary_days_of, figures.daysSoFar, it) }
                    ?: figures.daysSoFar.toString()
            )
            if (figures.averagePerDay.isNotEmpty()) {
                FigureRow(stringResource(R.string.trip_summary_average), CurrencyFormatter.formatByCurrency(figures.averagePerDay))
            }
            // One block per currency, so a mixed trip never ranks rupees against dollars.
            figures.topCategories.forEach { (currency, spends) ->
                if (spends.isEmpty()) return@forEach
                Text(
                    if (figures.topCategories.size > 1) {
                        "${stringResource(R.string.trip_summary_categories)} · $currency"
                    } else {
                        stringResource(R.string.trip_summary_categories)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                spends.forEach { spend ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        CategoryIcon(
                            category = spend.category,
                            size = Dimensions.Icon.inline,
                            tint = CategoryMapping.colorFor(spend.category)
                        )
                        Text(
                            categoryLabel(spend.category),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(spend.amount.format(), style = PennyWiseText.amountRow)
                    }
                }
            }
        }
    }
}

@Composable
private fun FigureRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
