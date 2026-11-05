package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.ui.icons.categoryDisplayColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.icons.categoryLabel

/**
 * A composable that displays a category with a colored dot indicator.
 * Used in dropdowns, transaction lists, and anywhere categories are displayed.
 */
@Composable
fun CategoryChip(
    category: CategoryEntity,
    modifier: Modifier = Modifier,
    showText: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Emoji if the user picked one (#760), else the colored dot
        val emoji = category.icon?.takeIf { it.isNotBlank() }
        if (emoji != null) {
            Box(modifier = Modifier.padding(end = if (showText) Spacing.xs else 0.dp)) {
                EmojiGlyph(emoji, 16.dp)
            }
        } else {
            CategoryDot(
                color = categoryDisplayColor(category.name, category.color),
                modifier = Modifier.padding(end = if (showText) Spacing.xs else 0.dp)
            )
        }

        // Category name
        if (showText) {
            Text(
                text = categoryLabel(category.name),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A simple colored dot indicator for a category.
 */
@Composable
fun CategoryDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 8
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color = color, shape = CircleShape)
    )
}

/**
 * Overload for displaying category by name and color without entity.
 */
@Composable
fun CategoryChip(
    categoryName: String,
    categoryColor: String,
    modifier: Modifier = Modifier,
    showText: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Colored dot indicator
        CategoryDot(
            color = categoryDisplayColor(categoryName, categoryColor),
            modifier = Modifier.padding(end = if (showText) Spacing.xs else 0.dp)
        )

        // Category name
        if (showText) {
            Text(
                text = categoryLabel(categoryName),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
