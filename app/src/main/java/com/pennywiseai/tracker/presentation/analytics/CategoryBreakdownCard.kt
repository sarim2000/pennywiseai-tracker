package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * "Where it went" by category: one segmented row per category, its share bar
 * in the category's own colour and as long as its amount against the largest.
 */
@Composable
fun CategoryBreakdownCard(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {}
) {
    val maxAmount = categories.maxOfOrNull { it.amount } ?: BigDecimal.ZERO
    ExpandableGroupedList(items = categories, modifier = modifier) { category, position ->
        ShareRow(
            position = position,
            title = categoryLabel(category.name),
            amount = CurrencyFormatter.formatCurrency(category.amount, currency),
            percent = category.percentage.toInt(),
            fraction = shareOfMax(category.amount, maxAmount),
            // The user's colour for the category, else the built-in palette (#586).
            barColor = CategoryMapping.colorFor(category.name, category.color),
            onClick = { onCategoryClick(category) },
            leading = { CategoryBadge(category.name, category.color) }
        )
    }
}

/** [amount] as a fraction of the largest row, for a share bar's length. */
internal fun shareOfMax(amount: BigDecimal, max: BigDecimal): Float =
    if (max > BigDecimal.ZERO) (amount.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
