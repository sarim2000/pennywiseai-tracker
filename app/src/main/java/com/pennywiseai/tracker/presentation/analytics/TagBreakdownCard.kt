package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * A share breakdown in segmented rows: tags, and (#422) the day-of-week view,
 * which passes one colour, an icon of its own and all seven rows.
 */
@Composable
fun TagBreakdownCard(
    tags: List<TagData>,
    currency: String,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 6,
    barColor: (String) -> Color = ::tagColor,
    icon: ImageVector? = Icons.Default.Sell,
    // Null, or the "No tag" row: the row isn't tappable (no Transactions filter for it).
    onTagClick: ((TagData) -> Unit)? = {}
) {
    val maxAmount = tags.maxOfOrNull { it.amount } ?: BigDecimal.ZERO
    ExpandableGroupedList(items = tags, modifier = modifier, visibleCount = visibleItemCount) { tag, position ->
        val color = barColor(tag.name)
        ShareRow(
            position = position,
            title = tag.name,
            amount = CurrencyFormatter.formatCurrency(tag.amount, currency),
            percent = tag.percentage.toInt(),
            fraction = shareOfMax(tag.amount, maxAmount),
            barColor = color,
            onClick = if (onTagClick == null || tag.isUntagged) null else ({ onTagClick(tag) }),
            leading = icon?.let { { TintBadge(it, color) } }
        )
    }
}
