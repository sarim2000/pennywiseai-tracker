package com.pennywiseai.tracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.clickableFillFor
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.md_theme_dark_onTertiaryContainer
import com.pennywiseai.tracker.ui.theme.md_theme_dark_tertiaryContainer
import com.pennywiseai.tracker.ui.theme.md_theme_light_onTertiaryContainer
import com.pennywiseai.tracker.ui.theme.md_theme_light_tertiaryContainer
import com.pennywiseai.tracker.ui.theme.neutralSurfaces
import com.pennywiseai.tracker.ui.theme.onColoredFill
import com.pennywiseai.tracker.ui.theme.readableOn
import com.pennywiseai.tracker.ui.theme.warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterContrastTest {

    @Test
    fun readableMeterColors_keepsTheTrackWhenBothBackgroundsPass() {
        val card = Color.White
        val track = Color(0xFFE0E0E0)
        val indicator = Color(0xFF006E61)
        val meter = readableMeterColors(indicator, track, listOf(card), Color(0xFFEEEEEE), keepColor = false)
        assertEquals(ProgressColors(indicator, track), meter)
    }

    @Test
    fun readableMeterColors_colourThatReadsOnTheCard_staysAndTheTrackMoves() {
        // The Budgets hero: a white wave on a purple card over a pale grey track. White already
        // reads on the card, so the track gives way, not the wave.
        val card = Color(0xFF8839EF)
        val track = Color(0xFFCDD0DA)
        val meter = readableMeterColors(Color.White, track, listOf(card), track, keepColor = false)
        assertEquals(Color.White, meter.indicator)
        assertNotEquals(track, meter.track)
        assertClears(meter, card)
    }

    @Test
    fun readableMeterColors_pickedColour_staysEvenWhereItMissesTheCard() {
        // Preset yellow on a white card: the user's colour stays, the track darkens until it shows.
        val yellow = Color(0xFFFDD835)
        val track = Color(0xFFE6E0E9)
        val meter = readableMeterColors(yellow, track, listOf(Color.White, Color(0xFFF7F2FA)), track, keepColor = true)
        assertEquals(yellow, meter.indicator)
        assertTrue(contrastRatio(yellow, meter.track) >= GRAPHIC_CONTRAST)
    }

    @Test
    fun readableMeterColors_takoDarkHero_replacesTheTrackSoTheIndicatorClearsBoth() {
        val card = Color(0xFFF3B375) // Tako dark primaryContainer, and its primary
        val track = Color(0xFF36364D) // Tako dark surfaceContainerHighest
        val seen = track.compositeOver(card)
        val nudged = card.readableOn(listOf(seen, card), GRAPHIC_CONTRAST)
        assertTrue(
            "the straddling pair is the case this fixes",
            contrastRatio(nudged, seen) < GRAPHIC_CONTRAST || contrastRatio(nudged, card) < GRAPHIC_CONTRAST,
        )

        val meter = readableMeterColors(card, track, listOf(card), track, keepColor = false)
        assertNotEquals(track, meter.track)
        assertClears(meter, card)
    }

    @Test
    fun readableMeterColors_darkerContainer_arcStillClearsIt() {
        // A selected goal row: secondaryContainer darker than the ring's track.
        val container = Color(0xFF5D3200)
        val track = Color(0xFF8D6E63)
        val meter = readableMeterColors(Color(0xFFFFE082), track, listOf(container), track, keepColor = true)
        assertClears(meter, container)
    }

    @Test
    fun heroBudgetMeter_readsInEveryScheme_andKeepsColoursThatAlreadyDo() {
        val failures = builtInSchemes.flatMap { (name, outer) ->
            val fill = outer.primaryContainer.clickableFillFor(outer.onPrimaryContainer)
            val card = outer.onColoredFill(fill, outer.onPrimaryContainer)
            listOf("primary" to outer.primary, "warning" to outer.warning, "error" to outer.error).mapNotNull { (label, c) ->
                // As BalanceCard's BudgetMeter draws it through WavyMeter.
                val meter = readableMeterColors(c, outer.surfaceContainerHighest, listOf(fill), card.surfaceContainerHighest, keepColor = false)
                val seen = meter.track.compositeOver(fill)
                val kept = contrastRatio(c, fill) < GRAPHIC_CONTRAST || meter.indicator == c
                when {
                    contrastRatio(meter.indicator, seen) < GRAPHIC_CONTRAST -> "$name: $label on its track"
                    contrastRatio(meter.indicator, fill) < GRAPHIC_CONTRAST -> "$name: $label on the card"
                    !kept -> "$name: $label moved although it read on the card"
                    else -> null
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun goalAndBudgetMeters_keepTheirColour_onEveryNeutralSurface() {
        // The swatch row's palest preset, in every scheme: kept as picked, with a track it reads on.
        val yellow = Color(0xFFFDD835)
        val failures = builtInSchemes.mapNotNull { (name, scheme) ->
            val cards = scheme.neutralSurfaces
            val meter = readableMeterColors(yellow, scheme.surfaceContainerHighest, cards, scheme.surfaceContainerHighest, keepColor = true)
            val worst = cards.minOf { contrastRatio(meter.indicator, meter.track.compositeOver(it)) }
            when {
                meter.indicator != yellow -> "$name: yellow moved to ${meter.indicator}"
                worst < GRAPHIC_CONTRAST -> "$name: yellow on its track at $worst"
                else -> null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun visibleTrack_reaches3To1_andLeavesTheIndicatorAlone() {
        listOf(
            md_theme_light_onTertiaryContainer to md_theme_light_tertiaryContainer,
            md_theme_dark_onTertiaryContainer to md_theme_dark_tertiaryContainer,
        ).forEach { (ink, fill) ->
            val faint = ink.copy(alpha = 0.2f).compositeOver(fill)
            assertTrue(contrastRatio(faint, fill) < GRAPHIC_CONTRAST)
            val track = ink.visibleTrack(fill)
            assertTrue(contrastRatio(track, fill) >= GRAPHIC_CONTRAST)
            assertNotEquals(ink, track)
        }
    }

    private fun assertClears(meter: ProgressColors, card: Color) {
        val seen = meter.track.compositeOver(card)
        assertTrue(contrastRatio(meter.indicator, seen) >= GRAPHIC_CONTRAST)
        assertTrue(contrastRatio(meter.indicator, card) >= GRAPHIC_CONTRAST)
    }
}
