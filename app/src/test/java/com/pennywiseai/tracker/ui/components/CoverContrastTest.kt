package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.ui.theme.DarkColorScheme
import com.pennywiseai.tracker.ui.theme.LightColorScheme
import com.pennywiseai.tracker.ui.theme.TEXT_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.fixedColorScheme
import com.pennywiseai.tracker.ui.theme.getCustomDarkColorScheme
import com.pennywiseai.tracker.ui.theme.getCustomLightColorScheme
import com.pennywiseai.tracker.ui.theme.isDark
import com.pennywiseai.tracker.ui.theme.withAmoledSurfaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home header's text reads on every cover, in every theme, in light, dark and AMOLED:
 * each banner stop as drawn (at the banner's alpha over the background) clears 4.5:1 for
 * onBackground, onSurface and onSurfaceVariant. Dynamic colour can't be unit-tested.
 */
class CoverContrastTest {

    private val schemes: List<Pair<String, ColorScheme>> = buildList {
        add("default/light" to LightColorScheme)
        add("default/dark" to DarkColorScheme)
        AccentColor.entries.forEach {
            add("rosePine-$it/light" to getCustomLightColorScheme(it))
            add("rosePine-$it/dark" to getCustomDarkColorScheme(it))
        }
        ThemeStyle.entries.forEach { style ->
            fixedColorScheme(style, dark = false)?.let { add("$style/light" to it) }
            fixedColorScheme(style, dark = true)?.let { add("$style/dark" to it) }
        }
        filter { it.first.endsWith("/dark") }.forEach { (name, s) ->
            add(name.replace("/dark", "/amoled") to s.withAmoledSurfaces())
        }
    }

    @Test
    fun headerInks_readOnEveryCoverStop_inEveryTheme() {
        val failures = schemes.flatMap { (name, scheme) ->
            CoverStyle.entries.flatMap { cover ->
                scheme.readableCoverStops(cover).flatMap { stop ->
                    val drawn = stop.copy(alpha = COVER_ALPHA).compositeOver(scheme.background)
                    listOf(scheme.onBackground, scheme.onSurface, scheme.onSurfaceVariant).mapNotNull { ink ->
                        val ratio = contrastRatio(ink, drawn)
                        if (ratio < TEXT_CONTRAST) "$name/$cover: ${"%.2f".format(ratio)}" else null
                    }
                }
            }
        }
        assertTrue(failures.distinct().joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun passingStops_keepTheirExactColour() {
        // Ocean's deep navy already reads under white text on the default dark scheme.
        val raw = getCoverGradientColors(CoverStyle.OCEAN, isDark = true).first()
        assertEquals(raw, DarkColorScheme.readableCoverStops(CoverStyle.OCEAN).first())
    }

    @Test
    fun srgbMidpoint_isThePerChannelAverage() {
        // A reference for the sweep below, which takes its midpoints from srgbMidpoint.
        assertEquals(Color(0xFF808080), srgbMidpoint(Color.Black, Color.White))
        assertEquals(Color(0xFF808000), srgbMidpoint(Color(0xFFFF0000), Color(0xFF00FF00)))
    }

    @Test
    fun aGradientThatAlreadyPasses_rendersExactlyAsBefore() {
        // A gradient whose stops and midpoints all pass comes back as the original stops, byte
        // for byte; in one that doesn't, every stop that already passed keeps its colour.
        // Which stops pass is decided from the input, independently of readableCoverStops.
        var untouchedGradients = 0
        schemes.forEach { (name, scheme) ->
            val inks = listOf(scheme.onBackground, scheme.onSurface, scheme.onSurfaceVariant)
            fun reads(stop: Color): Boolean {
                val drawn = stop.copy(alpha = COVER_ALPHA).compositeOver(scheme.background)
                return inks.all { contrastRatio(it, drawn) >= COVER_TEXT_TARGET }
            }
            CoverStyle.entries.filter { it != CoverStyle.NONE }.forEach { cover ->
                val raw = getCoverGradientColors(cover, scheme.isDark)
                val asDrawn = raw.zipWithNext { a, b -> listOf(a, srgbMidpoint(a, b)) }.flatten() + raw.last()
                val result = scheme.readableCoverStops(cover)
                if (asDrawn.all(::reads)) {
                    assertEquals("$name/$cover already passed", raw, result)
                    untouchedGradients++
                    return@forEach
                }
                assertEquals("$name/$cover", asDrawn.size, result.size)
                asDrawn.zip(result).forEachIndexed { i, (before, after) ->
                    if (reads(before)) assertEquals("$name/$cover stop $i already passed", before, after)
                }
            }
        }
        assertTrue("some theme x cover should pass untouched", untouchedGradients > 0)
    }
}
