package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.ColumnChart
import ir.ehsannarmani.compose_charts.models.BarProperties
import ir.ehsannarmani.compose_charts.models.Bars
import ir.ehsannarmani.compose_charts.models.DividerProperties
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.LineProperties
import ir.ehsannarmani.compose_charts.models.StrokeStyle
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun SpendingBarChart(
    primaryCurrency: String,
    data: List<BalancePoint>,
    modifier: Modifier = Modifier,
    height: Int = 220
) {
    if (data.isEmpty()) return

    val sortedData = remember(data) { data.sortedBy { it.timestamp } }
    val themeColors = MaterialTheme.colorScheme

    val columnData = remember(sortedData) {
        val isYearly = sortedData.size > 1 && sortedData.all { it.timestamp.dayOfYear == 1 }
        val isMonthly = !isYearly && sortedData.all { it.timestamp.dayOfMonth == 1 }
        val spansMultipleYears = if (sortedData.isNotEmpty()) {
            sortedData.first().timestamp.year != sortedData.last().timestamp.year
        } else false

        sortedData.map { point ->
            val label = when {
                isYearly -> point.timestamp.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> point.timestamp.format(DateTimeFormatter.ofPattern("MMM yy"))
                isMonthly -> point.timestamp.format(DateTimeFormatter.ofPattern("MMM"))
                else -> point.timestamp.format(DateTimeFormatter.ofPattern("dd MMM"))
            }
            Bars(
                label = label,
                values = listOf(
                    Bars.Data(
                        label = "Spending",
                        value = point.balance.toDouble(),
                        color = SolidColor(themeColors.primary.copy(alpha = 0.8f))
                    )
                )
            )
        }
    }

    val maxValue = remember(sortedData) {
        (sortedData.maxOfOrNull { it.balance.toDouble() } ?: 0.0) * 1.2
    }

    ColumnChart(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(vertical = Spacing.md),
        data = columnData,
        maxValue = maxValue,
        barProperties = BarProperties(
            cornerRadius = Bars.Data.Radius.Rectangle(
                topLeft = 6.dp,
                topRight = 6.dp,
                bottomLeft = 6.dp,
                bottomRight = 6.dp
            ),
            spacing = 8.dp,
            thickness = 12.dp
        ),
        dividerProperties = DividerProperties(
            enabled = true,
            xAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            ),
            yAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            )
        ),
        indicatorProperties = HorizontalIndicatorProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurfaceVariant.copy(0.6f),
                textAlign = TextAlign.Center
            ),
            contentBuilder = { value ->
                CurrencyFormatter.formatAbbreviated(abs(value), primaryCurrency)
            }
        ),
        labelHelperProperties = LabelHelperProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurface,
                textAlign = TextAlign.End
            ),
        ),
        labelProperties = LabelProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurface.copy(0.6f),
                textAlign = TextAlign.End
            ),
            padding = 16.dp,
            rotation = LabelProperties.Rotation(
                mode = LabelProperties.Rotation.Mode.Force,
                degree = -45f
            )
        ),
        gridProperties = GridProperties(
            enabled = true,
            xAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            ),
            yAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            )
        ),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
    )
}
