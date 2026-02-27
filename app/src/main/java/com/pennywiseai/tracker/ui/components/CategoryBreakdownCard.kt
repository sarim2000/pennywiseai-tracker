package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.screens.analytics.CategoryData
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter

@Composable
fun CategoryBreakdownCard(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {}
) {
    val maxAmount = categories.maxOfOrNull { it.amount } ?: java.math.BigDecimal.ZERO

    PennyWiseCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            ExpandableList(
                items = categories,
                visibleItemCount = 5
            ) { category ->
                CategoryBar(
                    category = category,
                    maxAmount = maxAmount,
                    currency = currency,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

@Composable
private fun CategoryBar(
    category: CategoryData,
    maxAmount: java.math.BigDecimal,
    currency: String,
    onClick: () -> Unit = {}
) {
    val targetFraction = if (maxAmount > java.math.BigDecimal.ZERO) {
        (category.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Animated progress bar fill
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animatedFraction by animateFloatAsState(
        targetValue = if (visible) targetFraction else 0f,
        animationSpec = tween(800),
        label = "category_bar_${category.name}"
    )

    // Get category-specific color
    val categoryInfo = CategoryMapping.categories[category.name]
        ?: CategoryMapping.categories["Others"]!!
    val categoryColor = categoryInfo.color

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${category.percentage.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(category.amount, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Animated progress bar with rounded corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(categoryColor)
            )
        }
    }
}
