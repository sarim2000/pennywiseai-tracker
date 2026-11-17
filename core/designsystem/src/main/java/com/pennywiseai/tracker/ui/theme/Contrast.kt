package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.concurrent.ConcurrentHashMap

// Kept out of Theme.kt on purpose: LightColorScheme/DarkColorScheme call withReadableInk()
// while that file initialises, so the cache below must live in its own file class.

/** WCAG AA floor for normal-size text. */
const val TEXT_CONTRAST = 4.5f

/** WCAG AA floor (1.4.11) for icons and chart marks you need to see. */
const val GRAPHIC_CONTRAST = 3f

/** How far past its floor a nudged colour aims, so 8-bit rounding never lands it under. */
private const val NUDGE_MARGIN = 0.1f

/** WCAG 2.x contrast ratio between two opaque colours, from 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** Black or white, whichever reads better on this fill. One of them always clears 4.58:1. */
fun Color.bestInk(): Color =
    if (contrastRatio(Color.Black, this) >= contrastRatio(Color.White, this)) Color.Black else Color.White

/** This colour if it reads at [TEXT_CONTRAST] on [fill], else [bestInk] of the fill. */
fun Color.orBestInkOn(fill: Color): Color =
    if (contrastRatio(this, fill) >= TEXT_CONTRAST) this else fill.bestInk()

/**
 * The surfaces text and semantic ink get drawn straight onto. A colour that reads on all
 * of these reads anywhere neutral; on a coloured container use its `on*` role instead.
 */
val ColorScheme.neutralSurfaces: List<Color>
    get() = listOf(
        background, surface, surfaceVariant,
        surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest,
    )

private val readableCache = ConcurrentHashMap<List<ULong>, Color>()

/**
 * This colour, moved toward black or white (in Oklab, so the hue holds) just far enough
 * to read at [minimum] on every one of [backgrounds]. Unchanged when it already does,
 * so a palette that passes keeps its exact colours. Pass [GRAPHIC_CONTRAST] for an icon
 * or chart mark, so it keeps more of its hue than text would.
 */
fun Color.readableOn(backgrounds: List<Color>, minimum: Float = TEXT_CONTRAST): Color {
    fun minContrast(c: Color) = backgrounds.minOf { contrastRatio(c, it) }
    if (minContrast(this) >= minimum) return this
    // ponytail: unbounded in theory, but keys are (token × scheme) — a few hundred at most.
    return readableCache.getOrPut(listOf(value, minimum.toRawBits().toULong()) + backgrounds.map { it.value }) {
        val end = if (minContrast(Color.Black) >= minContrast(Color.White)) Color.Black else Color.White
        var lo = 0f
        var hi = 1f
        repeat(16) {
            val mid = (lo + hi) / 2
            if (minContrast(lerp(this, end, mid)) >= minimum + NUDGE_MARGIN) hi = mid else lo = mid
        }
        lerp(this, end, hi)
    }
}

/**
 * [color] as ink on a pill, chip or circle filled with an [alpha] wash of itself. The
 * wash pulls the background toward the ink, so a colour sitting at 4.5:1 on the bare
 * surface drops to ~4:1 on it; this nudges it back, whatever neutral surface is behind.
 * Pass [GRAPHIC_CONTRAST] as [minimum] for an icon.
 */
fun ColorScheme.inkOnWash(color: Color, alpha: Float, minimum: Float = TEXT_CONTRAST): Color =
    color.readableOn(neutralSurfaces.map { color.copy(alpha = alpha).compositeOver(it) }, minimum)

/**
 * Makes a hand-built scheme's text roles readable: primary, error and onSurfaceVariant
 * on every neutral surface, inversePrimary on inverseSurface, and every on-colour on its
 * fill. Fills (containers, secondary, tertiary) keep their colours, so the theme's look holds.
 */
fun ColorScheme.withReadableInk(): ColorScheme {
    val neutrals = neutralSurfaces
    val primary = primary.readableOn(neutrals)
    val error = error.readableOn(neutrals)
    return copy(
        primary = primary,
        onPrimary = onPrimary.orBestInkOn(primary),
        onPrimaryContainer = onPrimaryContainer.orBestInkOn(primaryContainer),
        onSecondary = onSecondary.orBestInkOn(secondary),
        onSecondaryContainer = onSecondaryContainer.orBestInkOn(secondaryContainer),
        onTertiary = onTertiary.orBestInkOn(tertiary),
        onTertiaryContainer = onTertiaryContainer.orBestInkOn(tertiaryContainer),
        error = error,
        onError = onError.orBestInkOn(error),
        onErrorContainer = onErrorContainer.orBestInkOn(errorContainer),
        onSurfaceVariant = onSurfaceVariant.readableOn(neutrals),
        inversePrimary = inversePrimary.readableOn(listOf(inverseSurface)),
    )
}
