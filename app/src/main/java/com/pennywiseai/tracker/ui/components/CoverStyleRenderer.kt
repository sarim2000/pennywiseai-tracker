package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.ui.effects.bottomFade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun CoverGradientBanner(
    coverStyle: CoverStyle,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    hazeStateBanner: HazeState? = null
) {
    if (coverStyle == CoverStyle.NONE) return

    val colors = getCoverGradientColors(coverStyle, isDark, forPreview = false)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .alpha(0.45f)
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
