package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's hero is a primaryContainer card with a surface wash (Safe to spend, the currency
 * chip) on it. In every scheme, light, dark and AMOLED: the card's roles from
 * [onColoredFill] read on the card, the wash's from [onSurfaceWash] read on the wash, and a
 * semantic colour nudged onto the card reads there. Teal Turquoise, whose primary is its
 * primaryContainer, hid the hero's links and drew white on pale teal before these.
 */
class ColoredFillContrastTest {

    private val schemes: List<Pair<String, ColorScheme>> = buildList {
        fun addAll(name: String, light: ColorScheme, dark: ColorScheme) {
            add("$name/light" to light)
            add("$name/dark" to dark)
            add("$name/amoled" to dark.withAmoledSurfaces())
        }
        addAll("default", LightColorScheme, DarkColorScheme)
        AccentColor.entries.forEach { addAll("rosePine-${it.name}", getCustomLightColorScheme(it), getCustomDarkColorScheme(it)) }
        ThemeStyle.entries.forEach { style -> style.palette()?.let { addAll(style.name, it.lightScheme, it.darkScheme) } }
    }

    @Test
    fun heroCard_andItsWash_readInEveryScheme() {
        val failures = schemes.flatMap { (name, outer) ->
            val fill = outer.primaryContainer
            val card = outer.onColoredFill(fill, outer.onPrimaryContainer)
            val wash = outer.surfaceWashOn(fill)
            val onWash = outer.onSurfaceWash(fill)
            buildList {
                fun check(label: String, ink: Color, bg: Color, floor: Float) {
                    val ratio = contrastRatio(ink, bg)
                    if (ratio < floor) add("$name: $label = ${"%.2f".format(ratio)}")
                }
                check("card onSurface", card.onSurface, fill, TEXT_CONTRAST)
                check("card primary", card.primary, fill, TEXT_CONTRAST)
                check("card outline", card.outline, fill, GRAPHIC_CONTRAST)
                listOf("expense" to outer.expense, "income" to outer.income).forEach { (label, semantic) ->
                    check("$label text on card", semantic.readableOn(listOf(fill)), fill, TEXT_CONTRAST)
                    check("$label mark on card", semantic.readableOn(listOf(fill), GRAPHIC_CONTRAST), fill, GRAPHIC_CONTRAST)
                }
                // The budget meter (WavyMeter nudges its colour on its track and the card) in each state.
                val track = card.onSurface.copy(alpha = Dimensions.Alpha.meterTrack).compositeOver(fill)
                listOf("primary" to card.primary, "warning" to card.warning, "error" to card.error).forEach { (label, c) ->
                    val meter = c.readableOn(listOf(track, fill), GRAPHIC_CONTRAST)
                    check("$label meter on track", meter, track, GRAPHIC_CONTRAST)
                    check("$label meter on card", meter, fill, GRAPHIC_CONTRAST)
                }
                check("wash onSurface", onWash.onSurface, wash, TEXT_CONTRAST)
                check("wash onSurfaceVariant", onWash.onSurfaceVariant, wash, TEXT_CONTRAST)
                check("wash error", onWash.error, wash, TEXT_CONTRAST)
                // A menu opened from the chip draws on the scheme's own surfaces.
                outer.neutralSurfaces.forEach { check("menu onSurface", onWash.onSurface, it, TEXT_CONTRAST) }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
