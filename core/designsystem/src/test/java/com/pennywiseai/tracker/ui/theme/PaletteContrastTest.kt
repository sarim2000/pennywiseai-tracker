package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixed palette must keep text readable (WCAG AA, 4.5:1) on the pairs the app
 * draws with, in light, dark and AMOLED. Mihon's originals failed several of these
 * (e.g. Tidal Wave's onTertiaryContainer at 1.05:1), so this guards any future port.
 */
class PaletteContrastTest {

    @Test
    fun fixedPalettes_meetTextContrast_inLightDarkAndAmoled() {
        val failures = ThemeStyle.entries.flatMap { style ->
            val palette = style.palette() ?: return@flatMap emptyList()
            listOf(
                "light" to palette.lightScheme,
                "dark" to palette.darkScheme,
                "amoled" to palette.darkScheme.withAmoledSurfaces(),
            ).flatMap { (mode, scheme) -> scheme.textPairFailures().map { "${style.name}/$mode: $it" } }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

private const val TEXT_FLOOR = 4.5

/** Foreground/background pairs the UI renders as text, with their contrast if under [TEXT_FLOOR]. */
internal fun ColorScheme.textPairFailures(): List<String> {
    val neutrals = listOf(
        "background" to background,
        "surface" to surface,
        "surfaceVariant" to surfaceVariant,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
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
    ) + neutrals.flatMap { (name, bg) ->
        listOf(
            "onSurface/$name" to (onSurface to bg),
            "onSurfaceVariant/$name" to (onSurfaceVariant to bg),
            "primary/$name" to (primary to bg),
            "error/$name" to (error to bg),
        )
    }
    return pairs.mapNotNull { (name, pair) ->
        val ratio = contrast(pair.first, pair.second)
        if (ratio < TEXT_FLOOR) "$name = ${"%.2f".format(ratio)}" else null
    }
}

internal fun contrast(a: Color, b: Color): Double {
    val la = a.luminance() + 0.05
    val lb = b.luminance() + 0.05
    return maxOf(la, lb) / minOf(la, lb)
}
