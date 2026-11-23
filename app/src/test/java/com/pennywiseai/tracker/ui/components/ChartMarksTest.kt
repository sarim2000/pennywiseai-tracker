package com.pennywiseai.tracker.ui.components

import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.presentation.analytics.tonalBarColors
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.neutralSurfaces
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Insights chart rules, in every built-in scheme: heatmap steps and the bar chart's older vs latest bar. */
class ChartMarksTest {

    private val schemes = builtInSchemes

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
        // As the two charts call it: the Insights card on every neutral surface, and the
        // period chart on surfaceContainerLow alone (readableOn picks its direction from these).
        val failures = schemes.flatMap { (name, scheme) ->
            listOf(scheme.neutralSurfaces, listOf(scheme.surfaceContainerLow)).flatMap { cards ->
                val (older, latest) = tonalBarColors(scheme.primary, scheme.primaryContainer, cards)
                buildList {
                    cards.forEach { card ->
                        if (contrastRatio(older, card) < GRAPHIC_CONTRAST) add("$name ${cards.size} cards: older bar on $card")
                        if (contrastRatio(latest, card) < GRAPHIC_CONTRAST) add("$name ${cards.size} cards: latest bar on $card")
                    }
                    val gap = contrastRatio(older, latest)
                    if (gap < 1.5f) add("$name ${cards.size} cards: bars $gap apart")
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun heatmapSteps_onAMidToneCard_stayOnTheAccentsSide() {
        // White reads a little better than black on this grey, so 7:1 alone would send the
        // strongest step to white past a dark accent, and the steps between through the grey.
        val empty = Color(0xFF707070)
        val steps = heatmapSteps(Color(0xFF101040), empty, listOf(empty))
        steps.forEachIndexed { i, step ->
            assertTrue("step $i at ${contrastRatio(step, empty)}", contrastRatio(step, empty) >= GRAPHIC_CONTRAST)
        }
        val onEmpty = steps.map { contrastRatio(it, empty) }
        assertTrue("steps not increasing $onEmpty", onEmpty.zipWithNext().all { (a, b) -> b > a })
    }
}
