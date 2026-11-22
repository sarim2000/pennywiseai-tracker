package com.pennywiseai.tracker.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.res.ResourcesCompat
import com.pennywiseai.core.designsystem.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Google Sans Flex's axes as the app uses them, and the grid every axis value
 * is snapped to.
 *
 * Every distinct axis setting is a separate Android `Typeface`, and Compose
 * keeps only 16 of them in its own caches. So no value reaches a font
 * unquantized: the typography snaps to this grid, and the typefaces come from
 * a bounded process-wide cache.
 */
object GoogleSansFlexAxes {
    /** `ROND` step: 11 stops over 0–100. */
    const val ROUNDNESS_STEP = 10

    /** `GRAD` step: 11 stops over 0–100, 2–4% ink each at body weights. */
    const val GRADE_STEP = 10

    /**
     * `opsz` stops: the font's default (18) and the type scale's sizes above
     * it. A size snaps to the nearest stop, so nothing draws under 18.
     *
     * Below 18 the axis widens advances (`opsz` 12 sets text ~8% wider), which
     * would reflow every small line in the app and wrapped fixed-width labels
     * ("Transactions" in the nav bar). So text at 18sp and under keeps the cut
     * and widths it had, and only larger text gets its own size's cut: finer
     * details and slightly tighter spacing at display sizes (~1% narrower).
     * A large font scale never stacks the wide small cut on top of scaling.
     */
    val OPTICAL_SIZES: List<Int> = listOf(18, 22, 24, 28, 32, 36, 45, 57)

    /** Body, label and money: square terminals, crisp at small sizes and in figures. */
    const val BODY_ROUNDNESS = 0f

    /** Display, headline and title: fully rounded, the Expressive voice. */
    const val HEADING_ROUNDNESS = 100f

    /**
     * The theme's grade. Light text on a dark surface looks heavier than the
     * same weight dark-on-light, so the two themes differ by a notch of ink.
     * The font's `GRAD` axis starts at 0 (no negative grade), so it is the
     * light theme that moves: GRAD 20 there, the default 0 in dark mode. It
     * follows the theme, not the surface: light-on-dark text inside the light
     * theme (a snackbar, a filled button) draws at 20 too.
     */
    const val LIGHT_GRADE = 20f
    const val DARK_GRADE = 0f

    internal const val MIN_WEIGHT = 100
    internal const val MAX_WEIGHT = 900
    internal const val MAX_AXIS = 100

    /** Typefaces kept, one per (axes, weight) actually drawn. */
    internal const val MAX_TYPEFACES = 128
}

/**
 * The style drawn as money: square (`ROND` 0) figures at the optical size of
 * the style's own font size, the same grade, with `tnum`. Styles that aren't
 * Google Sans Flex take [fallbackFamily] (the theme's body family).
 */
internal fun TextStyle.asMoney(fallbackFamily: FontFamily?): TextStyle {
    val key = flexKeyOrNull()
    val family = if (key == null) {
        fallbackFamily
    } else {
        googleSansFlexFamily(
            key.copy(
                weight = ALL_WEIGHTS,
                roundness = GoogleSansFlexAxes.BODY_ROUNDNESS.toInt(),
                opticalSize = opticalSizeOf(fontSize, key.opticalSize),
            )
        )
    }
    return copy(fontFamily = family, fontFeatureSettings = TABULAR)
}

/**
 * A Google Sans Flex family with every weight 100–900, each its own pinned
 * instance at these axes. Compose resolves a style only by weight and style,
 * so each (roundness, optical size, grade) needs its own family.
 */
internal fun googleSansFlexFamily(roundness: Float, opticalSize: Float, grade: Float): FontFamily =
    googleSansFlexFamily(
        FlexKey(ALL_WEIGHTS, quantizeRoundness(roundness), quantizeOpticalSize(opticalSize), quantizeGrade(grade))
    )

/** [googleSansFlexFamily] at the optical size of [fontSize]. */
internal fun googleSansFlexFamily(roundness: Float, fontSize: TextUnit, grade: Float): FontFamily =
    googleSansFlexFamily(roundness, opticalSizeOf(fontSize, 18).toFloat(), grade)

/** A point on the grid. [weight] is [ALL_WEIGHTS] for a family declaring all nine. */
internal data class FlexKey(val weight: Int, val roundness: Int, val opticalSize: Int, val grade: Int) {
    fun variationSettings() = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", roundness.toFloat()),
        FontVariation.Setting("opsz", opticalSize.toFloat()),
        FontVariation.Setting("GRAD", grade.toFloat()),
    )
}

internal const val ALL_WEIGHTS = 0

internal fun quantizeRoundness(value: Float): Int = quantize(value, GoogleSansFlexAxes.ROUNDNESS_STEP)

internal fun quantizeGrade(value: Float): Int = quantize(value, GoogleSansFlexAxes.GRADE_STEP)

private fun quantize(value: Float, step: Int): Int =
    ((value / step).roundToInt() * step).coerceIn(0, GoogleSansFlexAxes.MAX_AXIS)

internal fun quantizeOpticalSize(value: Float): Int =
    GoogleSansFlexAxes.OPTICAL_SIZES.minBy { abs(it - value) }

private fun opticalSizeOf(fontSize: TextUnit, fallback: Int): Int =
    if (fontSize.isSp) quantizeOpticalSize(fontSize.value) else fallback

/** The axes of this style's family, or null when it isn't Google Sans Flex. */
internal fun TextStyle.flexKeyOrNull(): FlexKey? {
    val fonts = (fontFamily as? FontListFontFamily)?.fonts ?: return null
    val font = fonts.firstOrNull() as? GoogleSansFlexFont ?: return null
    return if (fonts.size > 1) font.key.copy(weight = ALL_WEIGHTS) else font.key
}

// ponytail: no family cache; GoogleSansFlexFont is equal by its axes, so equal families
// compare equal and the typeface cache below does the real work. Add one with axis animation.
internal fun googleSansFlexFamily(key: FlexKey): FontFamily =
    if (key.weight == ALL_WEIGHTS) {
        FontFamily(
            (GoogleSansFlexAxes.MIN_WEIGHT..GoogleSansFlexAxes.MAX_WEIGHT step 100).map {
                GoogleSansFlexFont(key.copy(weight = it))
            }
        )
    } else {
        FontFamily(GoogleSansFlexFont(key))
    }

/**
 * One pinned instance of the bundled variable font. Equal by its axes, so
 * Compose's own caches treat equal families from anywhere as one.
 */
internal class GoogleSansFlexFont(val key: FlexKey) :
    AndroidFont(FontLoadingStrategy.Blocking, GoogleSansFlexTypefaceLoader, key.variationSettings()) {
    override val weight: FontWeight = FontWeight(key.weight)
    override val style: FontStyle = FontStyle.Normal

    override fun equals(other: Any?) = other is GoogleSansFlexFont && other.key == key
    override fun hashCode() = key.hashCode()
    override fun toString() = "GoogleSansFlexFont($key)"
}

/**
 * Builds each variant's `Typeface` once and keeps it. Compose's per-font cache
 * holds 16; the type scale alone uses more variants than that, and a miss
 * through a plain resource font would build a fresh native typeface each time.
 */
private object GoogleSansFlexTypefaceLoader : AndroidFont.TypefaceLoader {
    private val typefaces = BoundedCache<FlexKey, Typeface>(GoogleSansFlexAxes.MAX_TYPEFACES)

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val key = (font as GoogleSansFlexFont).key
        return typefaces.getOrPut(key) {
            val base = checkNotNull(ResourcesCompat.getFont(context, R.font.google_sans_flex)) {
                "Google Sans Flex failed to load"
            }
            // The same route Compose takes for a variable resource font.
            Paint().apply {
                typeface = base
                fontVariationSettings = "'wght' ${key.weight}, 'ROND' ${key.roundness}, " +
                    "'opsz' ${key.opticalSize}, 'GRAD' ${key.grade}"
            }.typeface
        }
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}

/** A synchronized LRU map holding at most [maxSize] entries. */
internal class BoundedCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
    }

    @Synchronized
    fun getOrPut(key: K, create: () -> V): V = map[key] ?: create().also { map[key] = it }
}
