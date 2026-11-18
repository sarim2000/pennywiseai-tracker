package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.ui.effects.bottomFade
import com.pennywiseai.tracker.ui.theme.TEXT_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.isDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** How strongly the banner shows through behind the Home header. */
internal const val COVER_ALPHA = 0.45f

/** Past the 4.5:1 floor, so 8-bit rounding of the drawn gradient never lands under it. */
internal const val COVER_TEXT_TARGET = TEXT_CONTRAST + 0.1f

/**
 * [style]'s gradient stops for Home, each moved toward black or white (in Oklab, so the
 * hue holds) just far enough that the header's inks read on it at 4.5:1 once drawn at
 * [COVER_ALPHA] over [ColorScheme.background]. Stops that already pass keep their exact
 * colour, and a gradient that passes everywhere comes back as is. Otherwise midpoints become
 * stops too, since a blend of two stops can dip under either; they're taken in sRGB, as the
 * brush draws.
 *
 * The banner adapts rather than the inks: a pale stop on a dark theme (Lavender Mist,
 * Rose Gold) is too light for even pure white, but darkening the stop always works.
 *
 * [forPreview] gives the same stops at the Appearance tile's alpha, so a tile shows the
 * banner Home will draw.
 */
fun ColorScheme.readableCoverStops(style: CoverStyle, forPreview: Boolean = false): List<Color> {
    val raw = getCoverGradientColors(style, isDark)
    if (raw.isEmpty()) return raw
    val inks = listOf(onBackground, onSurface, onSurfaceVariant)
    // Light inks need a darker banner and dark inks a lighter one; the end colour itself always
    // passes, as it composites to something further from the inks than the background is.
    val end = if (inks.minOf { it.luminance() } > background.luminance()) Color.Black else Color.White
    fun drawn(c: Color) = c.copy(alpha = COVER_ALPHA).compositeOver(background)
    fun reads(c: Color) = inks.minOf { contrastRatio(it, drawn(c)) } >= COVER_TEXT_TARGET
    val withMids = raw.zipWithNext { a, b -> listOf(a, srgbMidpoint(a, b)) }.flatten() + raw.last()
    // A gradient that reads everywhere keeps its own stops: a midpoint stored in 8 bits can sit
    // half a step off the brush's blend.
    val stops = if (withMids.all(::reads)) raw else withMids.map { stop ->
        if (reads(stop)) return@map stop
        var lo = 0f
        var hi = 1f
        repeat(16) {
            val mid = (lo + hi) / 2
            if (reads(lerp(stop, end, mid))) hi = mid else lo = mid
        }
        lerp(stop, end, hi)
    }
    return if (forPreview) stops.map { it.copy(alpha = coverPreviewAlpha(isDark)) } else stops
}

/** How opaque a cover's gradient is on its Appearance tile. */
private fun coverPreviewAlpha(isDark: Boolean): Float = if (isDark) 0.70f else 0.75f

/** Halfway between two colours as a linear-gradient brush blends them (per sRGB channel). */
internal fun srgbMidpoint(a: Color, b: Color): Color =
    Color((a.red + b.red) / 2, (a.green + b.green) / 2, (a.blue + b.blue) / 2, (a.alpha + b.alpha) / 2)

@Composable
fun CoverGradientBanner(
    coverStyle: CoverStyle,
    modifier: Modifier = Modifier,
    hazeStateBanner: HazeState? = null
) {
    if (coverStyle == CoverStyle.NONE) return

    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme, coverStyle) { scheme.readableCoverStops(coverStyle) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .alpha(COVER_ALPHA)
            .bottomFade(0.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeStateBanner != null) Modifier.hazeSource(hazeStateBanner) else Modifier)
                .background(
                    Brush.horizontalGradient(colors = colors)
                )
        )
    }
}

fun getCoverGradientColors(
    style: CoverStyle,
    isDark: Boolean,
    forPreview: Boolean = false
): List<Color> {
    val alpha = if (forPreview) coverPreviewAlpha(isDark) else 1.0f

    return when (style) {
        CoverStyle.NONE -> emptyList()
        CoverStyle.AURORA -> listOf(
            Color(0xFF7B2FBE).copy(alpha = alpha),
            Color(0xFF2DD4BF).copy(alpha = alpha),
            Color(0xFFEC4899).copy(alpha = alpha)
        )
        CoverStyle.SUNSET -> listOf(
            Color(0xFFF97316).copy(alpha = alpha),
            Color(0xFFEC4899).copy(alpha = alpha),
            Color(0xFF8B5CF6).copy(alpha = alpha)
        )
        CoverStyle.OCEAN -> listOf(
            Color(0xFF1E3A5F).copy(alpha = alpha),
            Color(0xFF06B6D4).copy(alpha = alpha),
            Color(0xFF14B8A6).copy(alpha = alpha)
        )
        CoverStyle.FOREST -> listOf(
            Color(0xFF064E3B).copy(alpha = alpha),
            Color(0xFF059669).copy(alpha = alpha),
            Color(0xFF84CC16).copy(alpha = alpha)
        )
        CoverStyle.LAVENDER_MIST -> listOf(
            Color(0xFFA78BFA).copy(alpha = alpha),
            Color(0xFFF9A8D4).copy(alpha = alpha),
            Color(0xFFF3E8FF).copy(alpha = alpha)
        )
        CoverStyle.MIDNIGHT -> listOf(
            Color(0xFF1E1B4B).copy(alpha = alpha),
            Color(0xFF3730A3).copy(alpha = alpha),
            Color(0xFF7C3AED).copy(alpha = alpha)
        )
        CoverStyle.ROSE_GOLD -> listOf(
            Color(0xFFFDA4AF).copy(alpha = alpha),
            Color(0xFFFBBF24).copy(alpha = alpha),
            Color(0xFFFED7AA).copy(alpha = alpha)
        )
        CoverStyle.NORTHERN_LIGHTS -> listOf(
            Color(0xFF10B981).copy(alpha = alpha),
            Color(0xFF06B6D4).copy(alpha = alpha),
            Color(0xFF3B82F6).copy(alpha = alpha),
            Color(0xFF8B5CF6).copy(alpha = alpha)
        )
    }
}
