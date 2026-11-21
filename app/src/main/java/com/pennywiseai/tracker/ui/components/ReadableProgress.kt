package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.neutralSurfaces
import com.pennywiseai.tracker.ui.theme.readableOn

/** An indicator colour and the track it's drawn on. */
@Immutable
data class ProgressColors(val indicator: Color, val track: Color)

/**
 * Colours for an M3 progress indicator, linear or circular, wavy or not. M3's defaults
 * (primary on a secondaryContainer track; every variant shares that track token) fall to
 * 1.7:1 in Strawberry light, so [color] is nudged to 3:1 on [trackColor] and on the card it
 * sits on, as [WavyMeter] does. That card is [containerColor], or any neutral surface when
 * null. Where the track and the surfaces sit either side of mid-grey (Rosé Pine dark's pale
 * secondaryContainer) no colour reads on both, so the track becomes the meters'
 * surfaceContainerHighest. Don't pass an animating [color].
 */
@Composable
fun readableProgressColors(
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    containerColor: Color? = null,
): ProgressColors {
    val scheme = MaterialTheme.colorScheme
    val cards = containerColor?.let { listOf(it) } ?: scheme.neutralSurfaces
    fun backgrounds(track: Color) = cards.map { track.compositeOver(it) } + cards
    val indicator = color.readableOn(backgrounds(trackColor), GRAPHIC_CONTRAST)
    if (backgrounds(trackColor).all { contrastRatio(indicator, it) >= GRAPHIC_CONTRAST }) {
        return ProgressColors(indicator, trackColor)
    }
    val track = scheme.surfaceContainerHighest
    return ProgressColors(color.readableOn(backgrounds(track), GRAPHIC_CONTRAST), track)
}
