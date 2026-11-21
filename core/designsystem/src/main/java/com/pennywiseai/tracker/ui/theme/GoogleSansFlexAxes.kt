package com.pennywiseai.tracker.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
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
 * unquantized: the typography and [withFlexAxes] snap to this grid, and the
 * fonts come from bounded process-wide caches. An animation therefore walks
 * through a handful of cached variants instead of building a font per frame.
 */
object GoogleSansFlexAxes {
    /** `wght` step. Static styles sit on multiples of 100, so they are on the grid. */
    const val WEIGHT_STEP = 25

    /** `ROND` step: 11 stops over 0–100. */
    const val ROUNDNESS_STEP = 10

    /** `GRAD` step: 11 stops over 0–100, 2–4% ink each at body weights. */
    const val GRADE_STEP = 10

    /**
     * `opsz` stops. The type scale's own sizes, plus 18 (the font's default).
     * A size snaps to the nearest stop, so a role's static style and the end
     * of an animation land on the same variant. Above ~22 the axis barely
     * changes the outlines; below it, it opens counters and sturdies stems.
     */
    val OPTICAL_SIZES: List<Int> = listOf(11, 12, 14, 16, 18, 22, 24, 28, 32, 36, 45, 57)

    /**
     * Label roles' `opsz`, whatever their size. Below 18 the axis widens
     * advances (`opsz` 12 sets "Transactions" ~8% wider), and labels sit in
     * fixed-width single-line slots (nav items, toggles, chips, buttons) that
     * were sized at 18, so a smaller cut wraps them. Body text reflows freely
     * and keeps its own size's cut.
     */
    const val LABEL_OPTICAL_SIZE = 18f

    /** Body, label and money: square terminals, crisp at small sizes and in figures. */
    const val BODY_ROUNDNESS = 0f

    /** Display, headline and title: fully rounded, the Expressive voice. */
    const val HEADING_ROUNDNESS = 100f

    /**
     * The theme's grade. Light text on a dark surface looks heavier than the
     * same weight dark-on-light, so dark mode draws one notch of ink lighter.
     * The font's `GRAD` axis starts at 0 (no negative grade), so the light
     * theme sits slightly above it instead.
     */
    const val LIGHT_GRADE = 20f
    const val DARK_GRADE = 0f

    /**
     * Grade to add for emphasis — a selected tab or chip, a pressed toggle, an
     * amount that just changed. `GRAD` thickens strokes without changing any
     * advance width, so unlike `wght` it never reflows the line.
     */
    const val EMPHASIS_GRADE = 80f

    internal const val MIN_WEIGHT = 100
    internal const val MAX_WEIGHT = 900
    internal const val MAX_AXIS = 100

    /** Families kept: the type scale (~20 per theme) plus whatever is animating. */
    internal const val MAX_FAMILIES = 64

    /** Typefaces kept, one per (axes, weight) actually drawn. */
    internal const val MAX_TYPEFACES = 128
}

/**
 * Whether the theme's typography is Google Sans Flex: the font picked and a
 * Latin-script locale. Axis animation is only meaningful then; with the system
 * font or SN Pro [withFlexAxes] returns its style unchanged anyway, so this is
 * for skipping the animation's own setup.
 */
val LocalGoogleSansFlexActive = staticCompositionLocalOf { false }

/** Whether this style is drawn in Google Sans Flex (one of this file's families). */
val TextStyle.isGoogleSansFlex: Boolean
    get() = flexKeyOrNull() != null

/**
 * This style with some of its Google Sans Flex axes moved, for animating them.
 * Call it every frame with the animated values: they are quantized to
 * [GoogleSansFlexAxes]'s grid and the family comes from a bounded cache, so it
 * never builds a font per frame. Returns this style unchanged when it isn't
 * drawn in Google Sans Flex (system font, SN Pro, a non-Latin locale) or when
 * nothing moved past a grid step.
 *
 * Pass the theme's style (e.g. `MaterialTheme.typography.labelMedium` or a
 * `PennyWiseText` money style), not one this function already returned: the
 * theme's grade is read from it.
 *
 * @param weight absolute `wght`, 100–900. Changes advance widths, so the text
 *   reflows each step; prefer [gradeBoost] for emphasis. Pins one weight, so
 *   the system "bold text" setting no longer applies to this style.
 * @param roundness absolute `ROND`, 0 (square) – 100 (round).
 * @param opticalSize absolute `opsz`, as a size in sp (11–57).
 * @param gradeBoost `GRAD` added to the theme's grade, e.g. a fraction of
 *   [GoogleSansFlexAxes.EMPHASIS_GRADE]. Never changes widths.
 */
fun TextStyle.withFlexAxes(
    weight: Float? = null,
    roundness: Float? = null,
    opticalSize: Float? = null,
    gradeBoost: Float = 0f,
): TextStyle {
    val base = flexKeyOrNull() ?: return this
    val key = FlexKey(
        weight = weight?.let(::quantizeWeight) ?: base.weight,
        roundness = roundness?.let(::quantizeRoundness) ?: base.roundness,
        opticalSize = opticalSize?.let(::quantizeOpticalSize) ?: base.opticalSize,
        grade = quantizeGrade(base.grade + gradeBoost),
    )
    if (key == base) return this
    return if (weight == null) {
        copy(fontFamily = googleSansFlexFamily(key))
    } else {
        // One pinned weight: name it on the style too, so Compose neither
        // picks another instance nor fakes bold on top of it.
        copy(
            fontFamily = googleSansFlexFamily(key),
            fontWeight = FontWeight(key.weight),
            fontSynthesis = FontSynthesis.None,
        )
    }
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
 * so each (roundness, optical size, grade) needs its own family; this returns
 * the cached one.
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

internal fun quantizeWeight(value: Float): Int {
    val step = GoogleSansFlexAxes.WEIGHT_STEP
    return ((value / step).roundToInt() * step).coerceIn(GoogleSansFlexAxes.MIN_WEIGHT, GoogleSansFlexAxes.MAX_WEIGHT)
}

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

private val families = BoundedCache<FlexKey, FontFamily>(GoogleSansFlexAxes.MAX_FAMILIES)

internal fun googleSansFlexFamily(key: FlexKey): FontFamily = families.getOrPut(key) {
    if (key.weight == ALL_WEIGHTS) {
        FontFamily(
            (GoogleSansFlexAxes.MIN_WEIGHT..GoogleSansFlexAxes.MAX_WEIGHT step 100).map {
                GoogleSansFlexFont(key.copy(weight = it))
            }
        )
    } else {
        FontFamily(GoogleSansFlexFont(key))
    }
}

/** Families currently cached; for tests. */
internal val cachedFamilyCount: Int get() = families.size

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

    val size: Int
        @Synchronized get() = map.size
}
