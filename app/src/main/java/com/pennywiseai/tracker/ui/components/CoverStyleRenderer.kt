package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.ui.effects.bottomFade
import com.pennywiseai.tracker.ui.theme.isAppInDarkTheme
import com.pennywiseai.tracker.ui.theme.isDark
import com.pennywiseai.tracker.ui.theme.neutralSurfaces
import com.pennywiseai.tracker.ui.theme.readableOn
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** How strongly the banner shows through behind the Home header. */
private const val COVER_ALPHA = 0.45f

/**
 * This scheme with the header's inks (onBackground, onSurface, onSurfaceVariant) nudged
 * to read on [style]'s banner as Home draws it, and still on every neutral surface, so a
 * menu opened from the header reads too. Unchanged for [CoverStyle.NONE] or a banner the
 * inks already clear.
 *
 * ponytail: best effort. A mid-tone stop (Forest's lime in dark) can sit where even pure
 * white misses 4.5:1; the ink then ends at white. Fix by dimming that style's stops.
 */
fun ColorScheme.withInkOverCover(style: CoverStyle): ColorScheme {
    if (style == CoverStyle.NONE) return this
    val stops = getCoverGradientColors(style, isDark)
    // Midpoints too: a blend of two stops can be lighter or darker than either end.
    val behind = (stops.zipWithNext { a, b -> listOf(a, lerp(a, b, 0.5f)) }.flatten() + stops.last())
        .map { it.copy(alpha = COVER_ALPHA).compositeOver(background) } + neutralSurfaces
    return copy(
        onBackground = onBackground.readableOn(behind),
        onSurface = onSurface.readableOn(behind),
        onSurfaceVariant = onSurfaceVariant.readableOn(behind),
    )
}

@Composable
fun CoverGradientBanner(
    coverStyle: CoverStyle,
    modifier: Modifier = Modifier,
    isDark: Boolean = isAppInDarkTheme(),
    hazeStateBanner: HazeState? = null
) {
    if (coverStyle == CoverStyle.NONE) return

    val colors = getCoverGradientColors(coverStyle, isDark, forPreview = false)
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
    val previewAlpha = if (isDark) 0.70f else 0.75f
    val alpha = if (forPreview) previewAlpha else 1.0f

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
