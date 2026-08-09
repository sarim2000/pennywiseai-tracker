package com.pennywiseai.tracker.widget

import kotlinx.serialization.Serializable

/**
 * One slice of the category pie widget. Amounts are pre-formatted display
 * strings (already currency-tagged by the worker) so the widget never touches
 * money math; colors are resolved ARGB ints so rendering needs no theme.
 */
@Serializable
data class CategoryPieSlice(
    val name: String,
    val amountFormatted: String,
    val colorArgb: Long,
    val percent: Float
)

/**
 * Snapshot for the category pie widget (#665). Single-currency by
 * construction: the worker picks the cycle's dominant spend currency and
 * filters to it — a pie mixing ₹ and $ slices would be meaningless.
 */
@Serializable
data class CategoryPieWidgetData(
    val monthLabel: String = "",
    val currency: String = "INR",
    val totalFormatted: String = "",
    val slices: List<CategoryPieSlice> = emptyList()
)
