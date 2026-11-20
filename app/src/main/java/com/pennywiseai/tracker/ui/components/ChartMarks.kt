package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.readableOn

/** How far past 3:1 the palest mark aims, so 8-bit rounding never lands it under. */
private const val MARK_MARGIN = 0.1f

/** The strongest heatmap step: far enough past the lightest one that four steps read apart. */
private const val HEATMAP_STRONGEST = 7f

/**
 * [color] mixed as far into [background] as it can go while still reaching 3:1 on every
 * one of [backgrounds]: the softest mark that still shows, keeping the colour's own hue.
 * A colour that can't reach 3:1 is nudged there first.
 */
internal fun Color.palestMark(background: Color, backgrounds: List<Color>): Color {
    fun minContrast(c: Color) = backgrounds.minOf { contrastRatio(c, it) }
    val base = readableOn(backgrounds, GRAPHIC_CONTRAST)
    var lo = 0f
    var hi = 1f
    repeat(16) {
        val mid = (lo + hi) / 2
        if (minContrast(lerp(base, background, mid)) >= GRAPHIC_CONTRAST + MARK_MARGIN) lo = mid else hi = mid
    }
    return lerp(base, background, lo)
}

/**
 * Four opaque heatmap steps, quietest first, for days with activity. Translucent primary
 * left the quiet steps at ~1.4:1 on an empty day; now every step reaches 3:1 on the
 * [empty] cell and the [cards] behind the grid, and each is stronger than the last: the
 * palest mix of [accent] that shows, [accent] pushed to 7:1, and two evenly between.
 */
internal fun heatmapSteps(accent: Color, empty: Color, cards: List<Color>): List<Color> {
    val backgrounds = listOf(empty) + cards
    val lightest = accent.palestMark(empty, backgrounds)
    val strongest = accent.readableOn(backgrounds, HEATMAP_STRONGEST)
    return listOf(lightest, lerp(lightest, strongest, 1 / 3f), lerp(lightest, strongest, 2 / 3f), strongest)
}

/** [heatmapSteps] in this theme's primary, worked out once per scheme rather than per cell. */
@Composable
internal fun rememberHeatmapSteps(empty: Color, cards: List<Color>): List<Color> {
    val accent = MaterialTheme.colorScheme.primary
    return remember(accent, empty, cards) { heatmapSteps(accent, empty, cards) }
}
