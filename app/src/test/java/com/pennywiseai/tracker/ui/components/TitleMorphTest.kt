package com.pennywiseai.tracker.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexHeadingFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** The app-bar title morph must move in a few cached steps, never per scrolled pixel. */
class TitleMorphTest {

    private val expanded = 32.sp
    private val collapsed = 22.sp
    private val title = TextStyle(fontFamily = GoogleSansFlexHeadingFamily, fontWeight = FontWeight.Bold, fontSize = expanded)

    @Test
    fun theFractionMapsToAFewSteps() {
        val steps = (0..1000).map { TitleMorph.step(it / 1000f) }
        assertEquals((0..TitleMorph.STEPS).toList(), steps.distinct())
        assertEquals(0, TitleMorph.step(-0.2f))
        assertEquals(TitleMorph.STEPS, TitleMorph.step(1.3f))
    }

    @Test
    fun aWholeCollapseReusesAHandfulOfFamilies() {
        val families = (0..1000).map {
            title.morphedTitle(TitleMorph.step(it / 1000f), expanded, collapsed).fontFamily
        }
        // One per step at most (some steps share an optical-size stop).
        assertEquals(TitleMorph.STEPS + 1, families.distinct().size)
        assertSame(families[999], families[1000])
    }

    @Test
    fun theMorphKeepsTheWeightAndSize() {
        val end = title.morphedTitle(TitleMorph.STEPS, expanded, collapsed)
        assertNotSame(title.fontFamily, end.fontFamily)
        assertEquals(FontWeight.Bold, end.fontWeight)
        assertEquals(expanded, end.fontSize)
    }

    @Test
    fun otherFontsAreLeftAlone() {
        val system = TextStyle(fontWeight = FontWeight.Bold, fontSize = expanded)
        assertSame(system, system.morphedTitle(TitleMorph.STEPS, expanded, collapsed))
    }
}
