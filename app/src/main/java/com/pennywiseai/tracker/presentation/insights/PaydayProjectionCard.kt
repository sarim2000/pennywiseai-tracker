package com.pennywiseai.tracker.presentation.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.PaydayProjection
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter

/**
 * "Will my balance cover my bills until payday?": the lowest the balance gets once the
 * bills due before the next income are paid, when, and what the figure counts. While
 * amounts are hidden, whether it dips below zero is hidden too.
 */
@Composable
internal fun PaydayProjectionCard(projection: PaydayProjection, modifier: Modifier = Modifier) {
    val dates = AppDates.dayMonthFormatter()
    val hidden = AmountPrivacy.hidden
    val short = projection.belowZeroFrom
    Column(modifier) {
        SectionHeaderV2(title = stringResource(R.string.payday_projection_title))
        Spacer(Modifier.height(Spacing.Layout.headerToContent))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.Padding.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val income = projection.income
                Text(
                    text = if (income != null) {
                        stringResource(R.string.payday_projection_until_income, income.name, income.date.format(dates))
                    } else {
                        stringResource(R.string.payday_projection_until_cycle_end, projection.until.minusDays(1).format(dates))
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.payday_projection_lowest, projection.lowestOn.format(dates)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(projection.lowest, projection.currency),
                    style = PennyWiseText.amountMedium,
                    color = if (short != null && !hidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (!hidden) {
                    when {
                        short != null -> StatusLine(
                            Icons.Default.WarningAmber,
                            MaterialTheme.colorScheme.error,
                            stringResource(R.string.payday_projection_below_zero, short.format(dates)),
                        )
                        projection.bills.isNotEmpty() -> StatusLine(
                            Icons.Default.CheckCircle,
                            MaterialTheme.colorScheme.success,
                            stringResource(R.string.payday_projection_covered),
                        )
                    }
                }
                val assumptions = buildList {
                    val bills = projection.bills.size
                    add(
                        if (bills == 0) stringResource(R.string.payday_projection_no_bills)
                        else pluralStringResource(R.plurals.payday_projection_bills, bills, bills)
                    )
                    add(
                        stringResource(
                            if (income != null) R.string.payday_projection_one_income else R.string.payday_projection_no_income
                        )
                    )
                    add(stringResource(R.string.payday_projection_spending_excluded))
                    if (projection.partial) add(stringResource(R.string.payday_projection_partial))
                }
                Text(
                    text = assumptions.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimensions.Icon.small))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}
