package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
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
 * 1.7:1 in Strawberry light. [color] stays as it is where it reads on the card it sits on
 * ([containerColor], or any neutral surface when null); the track moves instead, as in
 * [readableMeterColors]. Don't pass an animating [color].
 */
@Composable
fun readableProgressColors(
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    containerColor: Color? = null,
): ProgressColors {
    val scheme = MaterialTheme.colorScheme
    val cards = containerColor?.let { listOf(it) } ?: scheme.neutralSurfaces
    return readableMeterColors(color, trackColor, cards, scheme.surfaceContainerHighest, keepColor = false)
}

/**
 * A meter's indicator and track on [cards]. The indicator keeps [color] wherever it reads at
 * 3:1 on the cards, and always when [keepColor] (a colour the user picked, such as a pale
 * goal yellow): then only the track moves, to [trackColor] when the indicator reads on it,
 * else [neutralTrack], else [contrastingTrack]. A theme colour lost on its own card (Tako
 * dark's primary on primaryContainer) is nudged instead, to 3:1 on the track and the cards,
 * on [trackColor], [neutralTrack] or a wash of the card's own ink, whichever lets it.
 */
internal fun readableMeterColors(
    color: Color,
    trackColor: Color,
    cards: List<Color>,
    neutralTrack: Color,
    keepColor: Boolean,
): ProgressColors {
    fun seen(track: Color) = cards.map { track.compositeOver(it) }
    if (keepColor || cards.all { contrastRatio(color, it) >= GRAPHIC_CONTRAST }) {
        val track = listOf(trackColor, neutralTrack).firstOrNull { track ->
            seen(track).all { contrastRatio(color, it) >= GRAPHIC_CONTRAST }
        } ?: color.contrastingTrack(cards.first())
        return ProgressColors(color, track)
    }

    fun backgrounds(track: Color) = seen(track) + cards
    fun ink(track: Color) = color.readableOn(backgrounds(track), GRAPHIC_CONTRAST)
    fun passes(track: Color) = backgrounds(track).all { contrastRatio(ink(track), it) >= GRAPHIC_CONTRAST }

    if (passes(trackColor)) return ProgressColors(ink(trackColor), trackColor)

    val card = cards.first()
    val nearCard = card.bestInk().copy(alpha = Dimensions.Alpha.meterTrack).compositeOver(card)
    val fallback = if (neutralTrack != trackColor && passes(neutralTrack)) neutralTrack else nearCard
    return ProgressColors(ink(fallback), fallback)
}

/**
 * An opaque track on [card] that this indicator reads on at 3:1: a [Dimensions.Alpha.meterTrack]
 * wash of the ink furthest from the indicator, taken further toward that ink only as far as it
 * needs. That ink itself always clears 4.5:1, so there is always one.
 */
internal fun Color.contrastingTrack(card: Color): Color {
    val ink = copy(alpha = 1f).bestInk()
    val wash = ink.copy(alpha = Dimensions.Alpha.meterTrack).compositeOver(card)
    fun shows(track: Color) = contrastRatio(this, track) >= GRAPHIC_CONTRAST + TRACK_MARGIN
    if (shows(wash)) return wash
    var lo = 0f
    var hi = 1f
    repeat(16) {
        val mid = (lo + hi) / 2
        if (shows(lerp(wash, ink, mid))) hi = mid else lo = mid
    }
    return lerp(wash, ink, hi)
}

/** How far past 3:1 a moved track aims, so 8-bit rounding never lands it under. */
private const val TRACK_MARGIN = 0.1f

/**
 * A track for an indicator that must stay this exact colour. The 20% wash when that
 * already reaches 3:1 on [fill]; otherwise the palest mix of this ink that does.
 */
internal fun Color.visibleTrack(fill: Color): Color {
    val faint = copy(alpha = 0.2f).compositeOver(fill)
    if (contrastRatio(faint, fill) >= GRAPHIC_CONTRAST) return faint
    return palestMark(fill, listOf(fill))
}
