package com.pennywiseai.tracker.widget

import kotlinx.serialization.Serializable

/**
 * One slice of the category pie widget. Colors are resolved ARGB ints so
 * rendering needs no theme.
 */
@Serializable
data class CategoryPieSlice(
    val name: String,
    val colorArgb: Long,
    val percent: Float
)

/**
 * Snapshot for the category pie widget (#665). Single-currency by
 * construction: the worker picks the cycle's dominant spend currency and
 * filters to it — a pie mixing ₹ and $ slices would be meaningless.
 * The widget formats [total] and the month itself, so the eye can hide the
 * total and the month follows the app's language.
 */
@Serializable
data class CategoryPieWidgetData(
    /** The cycle's first day, ISO (2026-10-01); blank in snapshots from before it. */
    val cycleStart: String = "",
    val currency: String = "INR",
    /** Plain decimal ("241.198"); blank in snapshots from before it. */
    val total: String = "",
    val slices: List<CategoryPieSlice> = emptyList()
)
