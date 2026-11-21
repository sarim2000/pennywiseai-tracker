package com.pennywiseai.tracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import com.pennywiseai.tracker.ui.theme.md_theme_dark_onTertiaryContainer
import com.pennywiseai.tracker.ui.theme.md_theme_dark_tertiaryContainer
import com.pennywiseai.tracker.ui.theme.md_theme_light_onTertiaryContainer
import com.pennywiseai.tracker.ui.theme.md_theme_light_tertiaryContainer
import com.pennywiseai.tracker.ui.theme.readableOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterContrastTest {

    @Test
    fun readableMeterColors_keepsTheTrackWhenBothBackgroundsPass() {
        val card = Color.White
        val track = Color(0xFFE0E0E0)
        val meter = readableMeterColors(Color(0xFF006E61), track, listOf(card), Color(0xFFEEEEEE))
        assertEquals(track, meter.track)
        assertClears(meter, card)
    }

    @Test
    fun readableMeterColors_takoDarkHero_replacesTheTrackSoTheIndicatorClearsBoth() {
        val card = Color(0xFFF3B375) // Tako dark primaryContainer
        val track = Color(0xFF36364D) // Tako dark surfaceContainerHighest
        val ink = Color(0xFF38294E) // onPrimaryContainer
        val seen = track.compositeOver(card)
        val nudged = ink.readableOn(listOf(seen, card), GRAPHIC_CONTRAST)
        assertTrue(
            "the straddling pair is the case this fixes",
            contrastRatio(nudged, seen) < GRAPHIC_CONTRAST || contrastRatio(nudged, card) < GRAPHIC_CONTRAST,
        )

        val meter = readableMeterColors(ink, track, listOf(card), track)
        assertNotEquals(track, meter.track)
        assertClears(meter, card)
    }

    @Test
    fun readableMeterColors_darkerContainer_arcStillClearsIt() {
        // A selected goal row: secondaryContainer darker than the ring's track.
        val container = Color(0xFF5D3200)
        val track = Color(0xFF8D6E63)
        val meter = readableMeterColors(Color(0xFFFFE082), track, listOf(container), track)
        assertClears(meter, container)
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
