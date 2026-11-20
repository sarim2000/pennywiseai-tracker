package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

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
 * Material's press and focus state layer: the content colour at this opacity over the whole
 * component, its text included (RippleDefaults in material3 1.5). Pass `ink.copy(alpha =
 * STATE_LAYER_ALPHA)` to [readableOn]'s `layers` for text on a clickable card.
 */
const val STATE_LAYER_ALPHA = 0.1f

/**
 * This colour, moved toward black or white just far enough to read at [minimum] on every
 * one of [backgrounds]. Unchanged when it already does, so a palette that passes keeps its
 * exact colours. It moves along Oklab lightness and keeps its hue and as much of its chroma
 * as sRGB allows there, as Material's tone does: red on a teal card turns light pink, not
 * white. Pass [GRAPHIC_CONTRAST] for an icon or chart mark, so it keeps more of its hue
 * than text would. [layers] are translucent colours that may be drawn over both it and the
 * backgrounds, such as a pressed or focused card's [STATE_LAYER_ALPHA] layer: it reads
 * under each of them too. Results are cached, so nudge an animation's target, never its
 * in-between frames.
 *
 * Meant for [backgrounds] of one tone (a surface and washes over it). If they straddle
 * mid-grey so that not even pure black or white reaches [minimum], it gives the better of the two.
 */
fun Color.readableOn(
    backgrounds: List<Color>,
    minimum: Float = TEXT_CONTRAST,
    layers: List<Color> = emptyList(),
): Color {
    fun minContrast(c: Color): Float {
        var lowest = backgrounds.minOf { contrastRatio(c, it) }
        for (layer in layers) {
            val ink = layer.compositeOver(c)
            for (bg in backgrounds) lowest = minOf(lowest, contrastRatio(ink, layer.compositeOver(bg)))
        }
        return lowest
    }
    if (minContrast(this) >= minimum) return this
    // ponytail: unbounded in theory, but keys are (token × scheme) — a few hundred at most.
    val key = listOf(value, minimum.toRawBits().toULong()) + backgrounds.map { it.value } + layers.map { it.value }
    return readableCache.getOrPut(key) {
        val toWhite = minContrast(Color.White) > minContrast(Color.Black)
        val lab = Oklab.of(this)
        var lo = 0f
        var hi = 1f
        repeat(16) {
            val mid = (lo + hi) / 2
            if (minContrast(lab.toward(toWhite, mid, alpha)) >= minimum + NUDGE_MARGIN) hi = mid else lo = mid
        }
        lab.toward(toWhite, hi, alpha)
    }
}

/** An sRGB colour in Oklab polar form, for moving its lightness while its hue holds. */
private class Oklab(val l: Float, val chroma: Float, val hue: Float) {

    /** [t] of the way to white (or black): lightness moves, chroma is kept where sRGB has room. */
    fun toward(toWhite: Boolean, t: Float, alpha: Float): Color {
        val lightness = l + ((if (toWhite) 1f else 0f) - l) * t
        val outAlpha = alpha + (1f - alpha) * t
        if (inGamut(lightness, chroma)) return srgb(lightness, chroma, outAlpha)
        var lo = 0f
        var hi = chroma
        repeat(GAMUT_STEPS) {
            val mid = (lo + hi) / 2
            if (inGamut(lightness, mid)) lo = mid else hi = mid
        }
        return srgb(lightness, lo, outAlpha)
    }

    private fun linear(lightness: Float, c: Float): FloatArray {
        val a = c * cos(hue)
        val b = c * sin(hue)
        val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).cube()
        val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).cube()
        val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).cube()
        return floatArrayOf(
            4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
        )
    }

    private fun inGamut(lightness: Float, c: Float) = linear(lightness, c).all { it in -GAMUT_SLACK..1f + GAMUT_SLACK }

    private fun srgb(lightness: Float, c: Float, alpha: Float): Color {
        val (r, g, b) = linear(lightness, c).map { it.coerceIn(0f, 1f).toSrgb() }
        return Color(r, g, b, alpha)
    }

    companion object {
        private const val GAMUT_STEPS = 14
        private const val GAMUT_SLACK = 1e-4f

        fun of(color: Color): Oklab {
            val r = color.red.toLinear()
            val g = color.green.toLinear()
            val b = color.blue.toLinear()
            val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
            val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
            val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
            val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
            val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
            val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
            return Oklab(lightness, hypot(a, bb), atan2(bb, a))
        }

        private fun Float.cube() = this * this * this
        private fun Float.toLinear() = if (this <= 0.04045f) this / 12.92f else ((this + 0.055f) / 1.055f).pow(2.4f)
        private fun Float.toSrgb() = if (this <= 0.0031308f) this * 12.92f else 1.055f * pow(1f / 2.4f) - 0.055f
    }
}

/**
 * [color] as ink on a pill, chip or circle filled with an [alpha] wash of itself. The
 * wash pulls the background toward the ink, so a colour sitting at 4.5:1 on the bare
 * surface drops to ~4:1 on it; this nudges it back, whatever neutral surface is behind.
 * Pass [GRAPHIC_CONTRAST] as [minimum] for an icon. The ink comes back opaque: contrast is
 * measured on opaque colours, so a translucent [color] is drawn at full strength.
 */
fun ColorScheme.inkOnWash(color: Color, alpha: Float, minimum: Float = TEXT_CONTRAST): Color {
    val ink = color.copy(alpha = 1f)
    return ink.readableOn(neutralSurfaces.map { ink.copy(alpha = alpha).compositeOver(it) }, minimum)
}

/** This colour as a press or focus state layer: itself at [STATE_LAYER_ALPHA]. */
fun Color.stateLayer(): Color = copy(alpha = STATE_LAYER_ALPHA)

/**
 * This fill (a clickable card's) nudged, hue kept, until [ink] reads on it at rest and
 * under ink's own press and focus layer. Any layer pulls text and fill together, so on a
 * mid-tone fill, where even white ink sits near 4.5:1 (Teal Turquoise's light card), no
 * ink can survive it: the fill has to make the room. Unchanged when it already does.
 * [otherLayers]: layers of an enclosing clickable that also draw over it.
 */
fun Color.clickableFillFor(ink: Color, otherLayers: List<Color> = emptyList()): Color {
    val restingInk = ink.readableOn(listOf(this))
    return readableOn(listOf(restingInk), layers = otherLayers + restingInk.stateLayer())
}

/**
 * This ink nudged to read at [minimum] on [fill], at rest and under its own state layer
 * (a pressed or focused card tints everything on it with its ink). Two passes: the layer
 * is the nudged ink's own.
 */
fun Color.inkUnderOwnLayer(fill: Color, minimum: Float = TEXT_CONTRAST): Color {
    val first = readableOn(listOf(fill), minimum, listOf(stateLayer()))
    return first.readableOn(listOf(fill), minimum, listOf(first.stateLayer()))
}

/**
 * This scheme for content drawn straight on a coloured, clickable [fill] whose ink is
 * [ink] (Home's hero on primaryContainer, through [clickableFillFor]). Text roles take the ink at full strength, and
 * primary is nudged until it reads on the fill: a hand-built palette can paint primary in
 * the fill's own colour, which hid the hero's links. outline becomes a 3:1 mark,
 * outlineVariant a faint ink line, and surfaceContainerHighest [surfaceWashOn]. Everything
 * also reads under the card's press and focus layer (onSurface's [stateLayer]; draw the
 * card with onSurface as its content colour). Semantic colours (expense, income…) aren't
 * roles and still only read on neutral surfaces: pass them through
 * `readableOn(listOf(fill), layers = listOf(onSurface.stateLayer()))`.
 */
fun ColorScheme.onColoredFill(fill: Color, ink: Color): ColorScheme {
    val cardInk = ink.inkUnderOwnLayer(fill)
    val layers = listOf(cardInk.stateLayer())
    // A text button in primary presses with a primary layer of its own.
    val link = primary.readableOn(listOf(fill), layers = layers + primary.stateLayer())
    return copy(
        primary = link.readableOn(listOf(fill), layers = layers + link.stateLayer()),
        onSurface = cardInk,
        onSurfaceVariant = cardInk,
        surfaceContainerHighest = surfaceWashOn(fill, cardInk),
        outline = cardInk.copy(alpha = Dimensions.Alpha.medium).compositeOver(fill)
            .readableOn(listOf(fill), GRAPHIC_CONTRAST, layers),
        outlineVariant = cardInk.copy(alpha = Dimensions.Alpha.divider),
    )
}

/**
 * This scheme's surface at [Dimensions.Alpha.surface] over [fill], as the opaque colour you
 * see, made a [clickableFillFor] the surface's ink under its own and the card's ([cardInk])
 * press layers: on a white card in a dark theme the wash comes out mid-grey.
 */
fun ColorScheme.surfaceWashOn(fill: Color, cardInk: Color): Color =
    surface.copy(alpha = Dimensions.Alpha.surface).compositeOver(fill).clickableFillFor(onSurface, listOf(cardInk.stateLayer()))

/**
 * This scheme for a chip or row on [surfaceWashOn] of [fill]: the wash is its container,
 * and its text roles are this scheme's own, nudged to read on the wash too. The wash
 * takes the surface's tone, so the surface's ink reads on it, not the fill's: white ink
 * on a teal card turned to white on pale teal there. Neutral surfaces stay, so a menu
 * opened from the chip still reads. It reads under its own press layer and under the
 * card's ([cardInk], the card's onSurface from [onColoredFill]) too.
 */
fun ColorScheme.onSurfaceWash(fill: Color, cardInk: Color): ColorScheme {
    val wash = surfaceWashOn(fill, cardInk)
    val backgrounds = neutralSurfaces + wash
    val first = onSurface.readableOn(backgrounds, layers = listOf(cardInk.stateLayer(), onSurface.stateLayer()))
    val washInk = first.readableOn(backgrounds, layers = listOf(cardInk.stateLayer(), first.stateLayer()))
    val layers = listOf(cardInk.stateLayer(), washInk.stateLayer())
    return copy(
        surfaceContainerHighest = wash,
        onSurface = washInk,
        onSurfaceVariant = onSurfaceVariant.readableOn(backgrounds, layers = layers),
        primary = primary.readableOn(backgrounds, layers = layers),
        error = error.readableOn(backgrounds, layers = layers),
    )
}

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
