package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.data.preferences.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's own schemes (default + every Rosé Pine accent) in light, dark and AMOLED keep
 * every text role, and every semantic colour, at WCAG AA (4.5:1) on every neutral surface.
 * See "Colour → Contrast" in docs/design.md for why these pairs.
 */
class SchemeContrastTest {

    private val schemes: List<Pair<String, ColorScheme>> = buildList {
        add("default/light" to LightColorScheme)
        add("default/dark" to DarkColorScheme)
        add("default/amoled" to DarkColorScheme.withAmoledSurfaces())
        AccentColor.entries.forEach { accent ->
            add("rosePine-${accent.name}/light" to getCustomLightColorScheme(accent))
            add("rosePine-${accent.name}/dark" to getCustomDarkColorScheme(accent))
            add("rosePine-${accent.name}/amoled" to getCustomDarkColorScheme(accent).withAmoledSurfaces())
        }
    }

    @Test
    fun everyScheme_meetsTheTextContract() {
        val failures = schemes.flatMap { (name, scheme) -> scheme.contractFailures().map { "$name: $it" } }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun readableOn_leavesPassingColoursAlone_andOnlyNudgesFailingOnes() {
        val white = listOf(Color.White)
        assertEquals(Color(0xFF006E61), Color(0xFF006E61).readableOn(white))
        val nudged = Color(0xFFFFB74D).readableOn(white) // amber is 1.8:1 on white
        assertTrue(contrastRatio(nudged, Color.White) >= TEXT_CONTRAST)
        assertTrue("moved toward black, not to it", nudged != Color.Black)
    }

    @Test
    fun readableOn_graphicFloor_stopsAt3To1_keepingMoreHue() {
        val white = listOf(Color.White)
        val grey = Color(0xFF9E9E9E) // a category colour, ~2.7:1 on white
        val icon = grey.readableOn(white, GRAPHIC_CONTRAST)
        assertTrue(contrastRatio(icon, Color.White) >= GRAPHIC_CONTRAST)
        assertTrue("an icon needs less nudging than text", contrastRatio(icon, Color.White) < TEXT_CONTRAST)
    }

    @Test
    fun inkOnWash_readsOnAWashOfItself_inEveryScheme() {
        val failures = schemes.flatMap { (name, scheme) ->
            listOf(scheme.income, scheme.loan, scheme.transfer).flatMap { semantic ->
                val ink = scheme.inkOnWash(semantic, 0.25f)
                scheme.neutralSurfaces.mapNotNull { bg ->
                    val ratio = contrastRatio(ink, semantic.copy(alpha = 0.25f).compositeOver(bg))
                    if (ratio < TEXT_CONTRAST) "$name: ${"%.2f".format(ratio)}" else null
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun bestInk_alwaysClearsTheTextFloor() {
        // The worst case for black-or-white is a mid-luminance fill (~0.18).
        (0..255 step 5).forEach { v ->
            val fill = Color(v, v, v)
            assertTrue("grey $v", contrastRatio(fill.bestInk(), fill) >= TEXT_CONTRAST)
        }
    }
}

internal fun ColorScheme.contractFailures(): List<String> {
    val neutrals = listOf(
        "background", "surface", "surfaceVariant", "surfaceContainerLowest",
        "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    ).zip(neutralSurfaces)
    val inks = listOf(
        "onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "primary" to primary, "error" to error,
        "income" to income, "expense" to expense, "success" to success, "warning" to warning,
        "credit" to credit, "transfer" to transfer, "investment" to investment, "loan" to loan,
    )
    val pairs = listOf(
        "onPrimary/primary" to (onPrimary to primary),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondary/secondary" to (onSecondary to secondary),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onTertiary/tertiary" to (onTertiary to tertiary),
        "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "onError/error" to (onError to error),
        "onErrorContainer/errorContainer" to (onErrorContainer to errorContainer),
        "onBackground/background" to (onBackground to background),
        "inverseOnSurface/inverseSurface" to (inverseOnSurface to inverseSurface),
        "inversePrimary/inverseSurface" to (inversePrimary to inverseSurface),
    ) + inks.flatMap { (inkName, ink) -> neutrals.map { (bgName, bg) -> "$inkName/$bgName" to (ink to bg) } }
    return pairs.mapNotNull { (name, pair) ->
        val ratio = contrastRatio(pair.first, pair.second)
        if (ratio < TEXT_CONTRAST) "$name = ${"%.2f".format(ratio)}" else null
    }
}
