package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Pie

@Composable
fun CategoryPieChart(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    val total = categories.sumOf { it.amount }.toDouble()
    if (total == 0.0) return

    val pieData = remember(categories) {
        categories.map { category ->
            Pie(
                label = category.name,
                data = category.amount.toDouble(),
                color = CategoryMapping.categories[category.name]?.color ?: Color.Gray,
                selectedColor = (CategoryMapping.categories[category.name]?.color
                    ?: Color.Gray).copy(alpha = 0.8f),
                selected = false,
                scaleAnimEnterSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                colorAnimEnterSpec = tween(500)
            )
        }
    }

    var chartData by remember(pieData) { mutableStateOf(pieData) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            PieChart(
                modifier = Modifier.size(160.dp),
                data = chartData,
                onPieClick = { clickedPie ->
                    val pieIndex = chartData.indexOf(clickedPie)
                    chartData = chartData.mapIndexed { index, pie ->
                        pie.copy(selected = index == pieIndex)
                    }
                },
                selectedScale = 1.1f,
                scaleAnimEnterSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                colorAnimEnterSpec = tween(500),
                style = Pie.Style.Stroke(width = 12.dp)
            )

            val selectedPie = chartData.find { it.selected }
            BlurredAnimatedVisibility(
                visible = selectedPie != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (selectedPie != null && selectedPie.label != null) {
                    CategoryIcon(
                        category = selectedPie.label!!,
                        size = 32.dp,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(selectedPie.color.copy(alpha = 0.2f))
                            .padding(12.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = Spacing.md)
        ) {
            items(chartData.sortedByDescending { it.data }) { pie ->
                PieLegendItem(
                    label = pie.label ?: "Unknown",
                    value = pie.data,
                    color = pie.color,
                    isSelected = pie.selected,
                    currency = currency,
                    totalAmount = total,
                    onClick = {
                        val pieIndex = chartData.indexOf(pie)
                        chartData = chartData.mapIndexed { index, p ->
                            p.copy(selected = index == pieIndex)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PieLegendItem(
    label: String,
    value: Double,
    color: Color,
    currency: String,
    isSelected: Boolean,
    totalAmount: Double,
    onClick: () -> Unit
) {
    val percentage = (value / totalAmount * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.formatAbbreviated(value, currency),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "($percentage%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
