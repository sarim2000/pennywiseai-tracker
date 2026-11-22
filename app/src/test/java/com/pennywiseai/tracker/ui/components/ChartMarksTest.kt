package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.ColorScheme
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.presentation.analytics.tonalBarColors
import com.pennywiseai.tracker.ui.theme.DarkColorScheme
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.LightColorScheme
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.getCustomDarkColorScheme
import com.pennywiseai.tracker.ui.theme.getCustomLightColorScheme
import com.pennywiseai.tracker.ui.theme.neutralSurfaces
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Insights chart rules: heatmap steps and the bar chart's older vs latest bar. */
class ChartMarksTest {

    private val schemes: List<Pair<String, ColorScheme>> = buildList {
        add("default/light" to LightColorScheme)
        add("default/dark" to DarkColorScheme)
        AccentColor.entries.forEach {
            add("${it.name}/light" to getCustomLightColorScheme(it))
            add("${it.name}/dark" to getCustomDarkColorScheme(it))
        }
    }

    @Test
    fun heatmapSteps_eachReaches3To1_andGetsStronger() {
        val failures = schemes.flatMap { (name, scheme) ->
            val empty = scheme.surfaceContainerHighest
            val cards = listOf(scheme.surfaceContainerLow)
            val steps = heatmapSteps(scheme.primary, empty, cards)
            buildList {
                steps.forEachIndexed { i, step ->
                    (listOf(empty) + cards).forEach { bg ->
                        if (contrastRatio(step, bg) < GRAPHIC_CONTRAST) add("$name: step $i on $bg")
                    }
                }
                val onEmpty = steps.map { contrastRatio(it, empty) }
                if (onEmpty.zipWithNext().any { (a, b) -> b <= a }) add("$name: steps not increasing $onEmpty")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun tonalBars_reach3To1_andStayApartFromTheLatest() {
        val failures = schemes.flatMap { (name, scheme) ->
            val cards = scheme.neutralSurfaces
            val (older, latest) = tonalBarColors(scheme.primary, scheme.primaryContainer, cards)
            buildList {
                cards.forEach { card ->
                    if (contrastRatio(older, card) < GRAPHIC_CONTRAST) add("$name: older bar on $card")
                    if (contrastRatio(latest, card) < GRAPHIC_CONTRAST) add("$name: latest bar on $card")
                }
                if (contrastRatio(older, latest) < 1.5f) add("$name: bars ${contrastRatio(older, latest)} apart")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
