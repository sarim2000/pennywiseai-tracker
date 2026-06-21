package com.pennywiseai.tracker.ui.icons

import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.data.database.entity.CategoryEntity

private val defaultCategoryColor: Color
    get() = CategoryMapping.categories["Others"]!!.color

/**
 * Resolves the display color for a category name.
 * Prefers persisted category colors (system + custom), then falls back to [CategoryMapping].
 */
fun categoryColorFor(
    categoryName: String,
    categoriesByName: Map<String, CategoryEntity> = emptyMap(),
): Color {
    categoriesByName[categoryName]?.let { entity ->
        return parseHexColor(entity.color, defaultCategoryColor)
    }
    return CategoryMapping.categories[categoryName]?.color ?: defaultCategoryColor
}

internal fun parseHexColor(colorString: String, fallback: Color): Color {
    return try {
        val hex = colorString.removePrefix("#")
        val colorLong = when (hex.length) {
            6 -> 0xFF000000L or hex.toLong(16)
            8 -> hex.toLong(16)
            else -> throw IllegalArgumentException("Invalid hex color: $colorString")
        }
        Color(colorLong)
    } catch (_: Exception) {
        fallback
    }
}
