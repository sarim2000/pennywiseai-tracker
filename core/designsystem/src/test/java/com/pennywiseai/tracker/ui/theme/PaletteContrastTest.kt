package com.pennywiseai.tracker.ui.theme

import com.pennywiseai.tracker.data.preferences.ThemeStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixed palette meets the same text contract as the app's own schemes
 * ([contractFailures], in SchemeContrastTest): each on-colour on its fill, and every text
 * and semantic colour (income, expense, warning…) on all eight neutral surfaces, in light,
 * dark and AMOLED. Mihon's originals failed many of these (e.g. Tidal Wave's
 * onTertiaryContainer at 1.05:1), so this guards any future port.
 */
class PaletteContrastTest {

    @Test
    fun fixedPalettes_meetTheTextContract_inLightDarkAndAmoled() {
        val failures = ThemeStyle.entries.flatMap { style ->
            val palette = style.palette() ?: return@flatMap emptyList()
            listOf(
                "light" to palette.lightScheme,
                "dark" to palette.darkScheme,
                "amoled" to palette.darkScheme.withAmoledSurfaces(),
            ).flatMap { (mode, scheme) -> scheme.contractFailures().map { "${style.name}/$mode: $it" } }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
