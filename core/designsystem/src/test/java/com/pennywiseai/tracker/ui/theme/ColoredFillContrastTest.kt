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
 * semantic colour nudged onto the card reads there, at rest and under the card's press layer. Teal Turquoise, whose primary is its
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
    fun heroCard_andItsWash_readInEveryScheme_atRestAndPressed() {
        val failures = schemes.flatMap { (name, outer) ->
            val fill = outer.primaryContainer.clickableFillFor(outer.onPrimaryContainer)
            val card = outer.onColoredFill(fill, outer.onPrimaryContainer)
            val cardLayer = card.onSurface.stateLayer()
            val wash = outer.surfaceWashOn(fill, card.onSurface)
            val onWash = outer.onSurfaceWash(fill, card.onSurface)
            val washLayer = onWash.onSurface.stateLayer()
            buildList {
                // At rest and under each state layer, drawn over both the ink and what's behind it.
                fun check(label: String, ink: Color, bg: Color, floor: Float, layers: List<Color> = listOf(cardLayer)) {
                    val ratio = (listOf(contrastRatio(ink, bg)) + layers.map { contrastRatio(it.compositeOver(ink), it.compositeOver(bg)) }).min()
                    if (ratio < floor) add("$name: $label = ${"%.2f".format(ratio)}")
                }
                check("card onSurface", card.onSurface, fill, TEXT_CONTRAST)
                check("card primary (link)", card.primary, fill, TEXT_CONTRAST, listOf(cardLayer, card.primary.stateLayer()))
                check("card outline", card.outline, fill, GRAPHIC_CONTRAST)
                listOf("expense" to outer.expense, "income" to outer.income, "loan" to outer.loan).forEach { (label, semantic) ->
                    check("$label text on card", semantic.readableOn(listOf(fill), layers = listOf(cardLayer)), fill, TEXT_CONTRAST)
                    check("$label mark on card", semantic.readableOn(listOf(fill), GRAPHIC_CONTRAST, listOf(cardLayer)), fill, GRAPHIC_CONTRAST)
                }
                // The budget meter (WavyMeter nudges its colour on its track and the card) in each state.
                val track = card.onSurface.copy(alpha = Dimensions.Alpha.meterTrack).compositeOver(fill)
                listOf("primary" to card.primary, "warning" to card.warning, "error" to card.error).forEach { (label, c) ->
                    val meter = c.readableOn(listOf(track, fill), GRAPHIC_CONTRAST, listOf(cardLayer))
                    check("$label meter on track", meter, track, GRAPHIC_CONTRAST)
                    check("$label meter on card", meter, fill, GRAPHIC_CONTRAST)
                }
                // The chip and Safe to spend press with their own layer, and take the card's when it's pressed.
                val washLayers = listOf(cardLayer, washLayer)
                check("wash onSurface", onWash.onSurface, wash, TEXT_CONTRAST, washLayers)
                check("wash onSurfaceVariant", onWash.onSurfaceVariant, wash, TEXT_CONTRAST, washLayers)
                check("wash error", onWash.error, wash, TEXT_CONTRAST, washLayers)
                check("wash primary", onWash.primary, wash, TEXT_CONTRAST, washLayers)
                // A menu opened from the chip draws on the scheme's own surfaces.
                outer.neutralSurfaces.forEach { check("menu onSurface", onWash.onSurface, it, TEXT_CONTRAST, emptyList()) }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun readableOn_keepsTheHue_whenItHasToMoveFar() {
        // Red on Teal Turquoise's light card has to go nearly white for text, but a 3:1 mark keeps a clear pink.
        val teal = Color(0xFF008080)
        val mark = Color(0xFFBB2525).readableOn(listOf(teal), GRAPHIC_CONTRAST)
        assertTrue(contrastRatio(mark, teal) >= GRAPHIC_CONTRAST)
        assertTrue("still red: $mark", mark.red - mark.blue > 0.15f)
    }
}
