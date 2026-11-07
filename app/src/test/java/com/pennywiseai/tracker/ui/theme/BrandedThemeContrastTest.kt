package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pennywiseai.tracker.data.preferences.AccentColor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The default branded accent (Surface, #403D52) was once used as `primary` directly:
 * the selected tab, "View All" and budget figures all but disappeared on dark cards.
 */
class BrandedThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertReadable(accent: AccentColor, scheme: ColorScheme) {
        val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainerHigh)
        for (fg in listOf(scheme.primary, scheme.secondary, scheme.tertiary)) {
            for (bg in surfaces) {
                val ratio = contrast(fg, bg)
                assertTrue("$accent: $fg on $bg is only ${"%.2f".format(ratio)}:1", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `every accent stays readable on every dark surface`() {
        AccentColor.entries.forEach { assertReadable(it, getCustomDarkColorScheme(it)) }
    }

    @Test
    fun `every accent stays readable on every light surface`() {
        AccentColor.entries.forEach { assertReadable(it, getCustomLightColorScheme(it)) }
    }
}
