package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.domain.spending.SpendForecast
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

/**
 * The Insights hero: what the period cost, how it compares with the one before,
 * and the last [HISTORY_PERIODS] periods as bars. A flat, roomy card on the
 * screen's lowest container so the big figure carries the hierarchy.
 */
@Composable
fun AnalyticsSummaryCard(
    label: String,
    totalAmount: BigDecimal,
    currency: String,
    history: PeriodHistory?,
    typeFilter: TransactionTypeFilter,
    forecast: SpendForecast?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Settles on a period change, and shrinks to fit rather than clipping:
            // a clipped total reads as a smaller, wrong figure.
            val totalStyle = MaterialTheme.typography.displayMediumEmphasized.money()
            key(currency) {
                AnimatedCurrencyText(
                    text = CurrencyFormatter.formatCurrency(totalAmount, currency),
                    style = totalStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = PennyWiseText.autoSizeFloor,
                        maxFontSize = totalStyle.fontSize
                    )
                )
            }
            if (!AmountPrivacy.hidden) {
                history?.change?.let { change ->
                    PeriodChangeChip(change = change, kind = history.kind, typeFilter = typeFilter)
                }
                if (history != null && history.periods.size > 1) {
                    PeriodBarChart(
                        bars = history.periods.map { period ->
                            PeriodBar(
                                key = period.start,
                                label = period.shortLabel(history.kind),
                                spokenLabel = period.fullLabel(history.kind),
                                amount = period.amount,
                                hasData = period.hasData
                            )
                        },
                        currency = history.currency,
                        modifier = Modifier.padding(top = Spacing.smd)
                    )
                }
            }
            forecast?.let { ForecastDisclosure(it) }
        }
    }
}

/**
 * "12% less than October": green when the change is good for this view (less
 * spending, more income), error-tinted when it's bad, neutral otherwise.
 */
@Composable
private fun PeriodChangeChip(change: PeriodChange, kind: PeriodKind, typeFilter: TransactionTypeFilter) {
    val previous = change.previous.fullLabel(kind)
    val text = when {
        change.percent == 0 && change.toDate -> stringResource(R.string.analytics_change_same_so_far, previous)
        change.percent == 0 -> stringResource(R.string.analytics_change_same, previous)
        change.isUp && change.toDate -> stringResource(R.string.analytics_change_more_so_far, change.percent, previous)
        change.isUp -> stringResource(R.string.analytics_change_more, change.percent, previous)
        change.toDate -> stringResource(R.string.analytics_change_less_so_far, change.percent, previous)
        else -> stringResource(R.string.analytics_change_less, change.percent, previous)
    }
    val spendView = typeFilter == TransactionTypeFilter.EXPENSE ||
        typeFilter == TransactionTypeFilter.SPENDING ||
        typeFilter == TransactionTypeFilter.CREDIT
    val good = when {
        change.percent == 0 -> null
        spendView -> !change.isUp
        typeFilter == TransactionTypeFilter.INCOME -> change.isUp
        else -> null
    }
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (good) {
        true -> colors.success.copy(alpha = Dimensions.Alpha.ghost) to colors.success
        false -> colors.errorContainer to colors.onErrorContainer
        null -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    StatusPill(
        text = text,
        container = container,
        content = content,
        icon = when {
            change.percent == 0 -> Icons.AutoMirrored.Filled.TrendingFlat
            change.isUp -> Icons.AutoMirrored.Filled.TrendingUp
            else -> Icons.AutoMirrored.Filled.TrendingDown
        }
    )
}

@Composable
private fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimensions.Component.statusChipHeight)
                .padding(start = Spacing.sm, end = Spacing.smd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The month a budget cycle mostly falls in: a 25 Oct – 24 Nov cycle is November. */
private fun PeriodSpend.midpoint() = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2)

/** A financial year by the year it ends in ("FY26" for Apr 2025 – Mar 2026). */
@Composable
private fun PeriodSpend.fyLabel(): String =
    stringResource(R.string.analytics_fy_short, "%02d".format((start.year + 1) % 100))

/** A bar's caption: "Nov", or "FY26". */
@Composable
internal fun PeriodSpend.shortLabel(kind: PeriodKind): String = when (kind) {
    PeriodKind.CYCLE -> midpoint().format(AppDates.pattern("MMM"))
    PeriodKind.FINANCIAL_YEAR -> fyLabel()
}

/** A period in a sentence: "November", or "FY26". */
@Composable
internal fun PeriodSpend.fullLabel(kind: PeriodKind): String = when (kind) {
    PeriodKind.CYCLE -> midpoint().format(AppDates.pattern("MMMM"))
    PeriodKind.FINANCIAL_YEAR -> fyLabel()
}

/** Month-end projection with the pace guidance available on demand. */
@Composable
private fun ForecastDisclosure(forecast: SpendForecast) {
    val aboveUsual = forecast.overUsual.takeUnless { AmountPrivacy.hidden }
    val daily = forecast.dailyToStayAtUsual
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = if (expanded) stringResource(R.string.insights_expanded) else stringResource(R.string.insights_collapsed)
    val label = stringResource(
        R.string.analytics_forecast_for,
        forecast.periodEnd.format(AppDates.dayMonthFormatter())
    )
    val summary = stringResource(
        R.string.insights_forecast_amount,
        label,
        CurrencyFormatter.formatCurrency(forecast.projected, forecast.currency)
    )
    GroupedRow(
        position = ListItemPosition.Single,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = if (aboveUsual != null) ({ expanded = !expanded }) else null,
        modifier = if (aboveUsual != null) Modifier.semantics { stateDescription = state } else Modifier
    ) {
        Text(summary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (aboveUsual != null) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
    if (expanded && aboveUsual != null) {
        Text(
            text = stringResource(
                R.string.analytics_forecast_above_usual,
                CurrencyFormatter.formatCurrency(aboveUsual, forecast.currency)
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.expense
        )
        if (daily != null && forecast.usual != null) {
            Text(
                text = stringResource(
                    R.string.analytics_forecast_stay_under,
                    CurrencyFormatter.formatCurrency(daily, forecast.currency),
                    CurrencyFormatter.formatCurrency(forecast.usual, forecast.currency)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
