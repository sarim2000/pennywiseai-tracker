package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
 * palest mix of [accent] that shows, [accent] taken on toward 7:1, and two evenly between.
 * The strongest step moves the way the palest one sits from the [empty] cell (darker or
 * lighter), so the steps between never cross the cell's tone.
 */
internal fun heatmapSteps(accent: Color, empty: Color, cards: List<Color>): List<Color> {
    val backgrounds = listOf(empty) + cards
    val lightest = accent.palestMark(empty, backgrounds)
    val darker = lightest.luminance() < empty.luminance()
    val nudged = accent.readableOn(backgrounds, HEATMAP_STRONGEST)
    // On a mid-tone card readableOn can pick the far side for 7:1 (white past a dark accent),
    // and the steps mixed between then cross the card's own tone, under 3:1. Then the
    // strongest step heads the palest one's way instead, as far as 7:1 or black (white).
    val strongest = if ((nudged.luminance() < empty.luminance()) == darker) {
        nudged
    } else {
        lightest.toward(if (darker) Color.Black else Color.White, HEATMAP_STRONGEST, backgrounds)
    }
    return listOf(lightest, lerp(lightest, strongest, 1 / 3f), lerp(lightest, strongest, 2 / 3f), strongest)
}

/** This colour mixed toward [extreme] until it reaches [minimum] on every one of [backgrounds], or [extreme] itself. */
private fun Color.toward(extreme: Color, minimum: Float, backgrounds: List<Color>): Color {
    fun minContrast(c: Color) = backgrounds.minOf { contrastRatio(c, it) }
    var lo = 0f
    var hi = 1f
    repeat(16) {
        val mid = (lo + hi) / 2
        if (minContrast(lerp(this, extreme, mid)) >= minimum) hi = mid else lo = mid
    }
    return lerp(this, extreme, hi)
}

/** [heatmapSteps] in this theme's primary, worked out once per scheme rather than per cell. */
@Composable
internal fun rememberHeatmapSteps(empty: Color, cards: List<Color>): List<Color> {
    val accent = MaterialTheme.colorScheme.primary
    return remember(accent, empty, cards) { heatmapSteps(accent, empty, cards) }
}
