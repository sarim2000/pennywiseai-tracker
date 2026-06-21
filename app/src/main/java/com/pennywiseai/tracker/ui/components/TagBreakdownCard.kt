package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.screens.analytics.TagData
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import kotlin.math.absoluteValue

/**
 * Stable palette for tag visualisations. Tags are free-text and have no
 * predefined colors (unlike categories), so we deterministically pick a color
 * from this palette based on the tag name. The same tag therefore always gets
 * the same color across the chart and list.
 */
private val TagPalette = listOf(
    Color(0xFF4285F4),
    Color(0xFFEA4335),
    Color(0xFFFBBC05),
    Color(0xFF34A853),
    Color(0xFFAB47BC),
    Color(0xFF00ACC1),
    Color(0xFFFF7043),
    Color(0xFF9E9D24),
    Color(0xFF5C6BC0),
    Color(0xFFEC407A)
)

fun tagColor(name: String): Color {
    if (TagPalette.isEmpty()) return Color.Gray
    val index = name.hashCode().absoluteValue % TagPalette.size
    return TagPalette[index]
}

@Composable
fun TagBreakdownCard(
    tags: List<TagData>,
    currency: String,
    modifier: Modifier = Modifier,
    onTagClick: (TagData) -> Unit = {}
) {
    val maxAmount = tags.map { it.amount }.maxOrNull() ?: BigDecimal.ZERO

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Spending by Tag",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            ExpandableList(
                items = tags,
                visibleItemCount = 5
            ) { tag ->
                TagBar(
                    tag = tag,
                    maxAmount = maxAmount,
                    currency = currency,
                    onClick = { onTagClick(tag) }
                )
            }
        }
    }
}

@Composable
private fun TagBar(
    tag: TagData,
    maxAmount: BigDecimal,
    currency: String,
    onClick: () -> Unit = {}
) {
    val targetFraction = if (maxAmount > BigDecimal.ZERO) {
        (tag.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animatedFraction by animateFloatAsState(
        targetValue = if (visible) targetFraction else 0f,
        animationSpec = tween(800),
        label = "tag_bar_${tag.name}"
    )

    val barColor = tagColor(tag.name)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tag.percentage.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(tag.amount, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

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
                    .background(barColor)
            )
        }
    }
}
