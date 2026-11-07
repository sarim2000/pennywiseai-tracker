package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.ui.components.cards.StatementLine
import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.domain.spending.SpendForecast
import java.math.BigDecimal
import com.pennywiseai.tracker.ui.icons.categoryDisplayColor
import com.pennywiseai.tracker.ui.icons.categoryLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsSummaryCard(
    /** The period this total covers, so the figure can't be misread. */
    scopeLabel: String,
    totalAmount: BigDecimal,
    transactionCount: Int,
    averageAmount: BigDecimal,
    topCategory: String?,
    topCategoryPercentage: Float,
    currency: String,
    isLoading: Boolean = false,
    forecast: SpendForecast? = null,
    modifier: Modifier = Modifier
) {
    // Animate alpha on load: 0 → 1
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val loadAlpha by animateFloatAsState(
        targetValue = if (visible && !isLoading) 1f else if (isLoading) 0.5f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "summary_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(loadAlpha)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "${stringResource(R.string.analytics_summary_total)} · $scopeLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = pluralStringResource(R.plurals.analytics_summary_txns, transactionCount, transactionCount),
                style = PennyWiseText.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = PennyWiseText.heroFigure(CurrencyFormatter.formatCurrency(totalAmount, currency), MaterialTheme.colorScheme.onSurfaceVariant),
            style = PennyWiseText.heroAmount,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            autoSize = PennyWiseText.fit(PennyWiseText.heroAmount),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        StatementLine(
            label = stringResource(R.string.analytics_summary_average) + " " +
                stringResource(R.string.analytics_summary_per_transaction).trim(),
            value = CurrencyFormatter.formatCurrency(
                if (transactionCount > 0) averageAmount else BigDecimal.ZERO,
                currency
            ),
        )
        if (topCategory != null && topCategoryPercentage > 0) {
            StatementLine(
                label = if (AmountPrivacy.hidden) stringResource(R.string.analytics_summary_top_category)
                else stringResource(R.string.analytics_summary_percent_of_total, topCategoryPercentage.toInt()),
                value = categoryLabel(topCategory),
                leading = {
                    CategoryIcon(
                        category = topCategory,
                        size = Dimensions.Icon.small,
                        tint = categoryDisplayColor(topCategory)
                    )
                },
            )
        }
        forecast?.let { ForecastSection(it) }
    }
}

/** Month-end forecast under the summary, with the "above your usual" note when it applies. */
@Composable
private fun ForecastSection(forecast: SpendForecast) {
    StatementLine(
        label = stringResource(
            R.string.analytics_forecast_for,
            forecast.periodEnd.format(AppDates.dayMonthFormatter())
        ),
        value = CurrencyFormatter.formatEstimate(forecast.projected, forecast.currency),
    )
    val over = forecast.overUsual ?: return
    Spacer(modifier = Modifier.height(Spacing.sm))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = Spacing.smd, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = stringResource(
                R.string.analytics_forecast_above_usual,
                CurrencyFormatter.formatCurrency(over, forecast.currency)
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.expense
        )
        forecast.dailyToStayAtUsual?.let { daily ->
            Text(
                text = stringResource(
                    R.string.analytics_forecast_stay_under,
                    CurrencyFormatter.formatEstimate(daily, forecast.currency),
                    CurrencyFormatter.formatCurrency(forecast.usual!!, forecast.currency)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
