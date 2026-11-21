package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.bestInk
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
 * null. Where the track and the card sit either side of mid-grey, no colour reads on both:
 * the track becomes surfaceContainerHighest, or a wash of ink on the card when that track
 * still straddles (Tako dark's pale primaryContainer). The requested track stays when the
 * indicator already clears both. Don't pass an animating [color].
 */
@Composable
fun readableProgressColors(
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    containerColor: Color? = null,
): ProgressColors {
    val scheme = MaterialTheme.colorScheme
    val cards = containerColor?.let { listOf(it) } ?: scheme.neutralSurfaces
    return readableMeterColors(color, trackColor, cards, scheme.surfaceContainerHighest)
}

/**
 * [color] at 3:1 on [trackColor] as seen over [cards], and on the cards themselves.
 * Keeps [trackColor] when that indicator clears every background. Otherwise tries
 * [neutralTrack], then a wash of the card's own ink, and recomputes.
 */
internal fun readableMeterColors(
    color: Color,
    trackColor: Color,
    cards: List<Color>,
    neutralTrack: Color,
): ProgressColors {
    fun backgrounds(track: Color) = cards.map { track.compositeOver(it) } + cards
    fun ink(track: Color) = color.readableOn(backgrounds(track), GRAPHIC_CONTRAST)
    fun passes(track: Color) = backgrounds(track).all { contrastRatio(ink(track), it) >= GRAPHIC_CONTRAST }

    if (passes(trackColor)) return ProgressColors(ink(trackColor), trackColor)

    val card = cards.first()
    val nearCard = card.bestInk().copy(alpha = Dimensions.Alpha.meterTrack).compositeOver(card)
    val fallback = if (neutralTrack != trackColor && passes(neutralTrack)) neutralTrack else nearCard
    return ProgressColors(ink(fallback), fallback)
}

/**
 * A track for an indicator that must stay this exact colour. The 20% wash when that
 * already reaches 3:1 on [fill]; otherwise the palest mix of this ink that does.
 */
internal fun Color.visibleTrack(fill: Color): Color {
    val faint = copy(alpha = 0.2f).compositeOver(fill)
    if (contrastRatio(faint, fill) >= GRAPHIC_CONTRAST) return faint
    return palestMark(fill, listOf(fill))
}
