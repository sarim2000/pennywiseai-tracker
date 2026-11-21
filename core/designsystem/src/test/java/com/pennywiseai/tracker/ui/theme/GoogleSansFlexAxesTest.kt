package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.data.preferences.AppFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Axis animation must never build a font per frame: values snap to a coarse
 * grid and families come from a bounded cache.
 */
class GoogleSansFlexAxesTest {

    private val labelMedium = typographyFor(AppFont.GOOGLE_SANS_FLEX, latinScript = true).labelMedium

    @Test
    fun valuesSnapToTheGrid() {
        assertEquals(700, quantizeWeight(712f))
        assertEquals(725, quantizeWeight(713f))
        assertEquals(100, quantizeWeight(-40f))
        assertEquals(900, quantizeWeight(1200f))
        assertEquals(40, quantizeRoundness(36f))
        assertEquals(100, quantizeRoundness(130f))
        assertEquals(0, quantizeGrade(-20f))
        assertEquals(60, quantizeGrade(57f))
        assertEquals(11, quantizeOpticalSize(8f))
        assertEquals(22, quantizeOpticalSize(21f))
        assertEquals(36, quantizeOpticalSize(40f))
        assertEquals(57, quantizeOpticalSize(200f))
    }

    @Test
    fun typeScaleSizesAreGridStops() {
        listOf(11, 12, 14, 16, 22, 24, 28, 32, 36, 45, 57).forEach {
            assertEquals(it, quantizeOpticalSize(it.toFloat()))
        }
    }

    @Test
    fun aSweepOfFramesReusesAHandfulOfFamilies() {
        val families = (0..1000).map { i ->
            labelMedium.withFlexAxes(gradeBoost = GoogleSansFlexAxes.EMPHASIS_GRADE * i / 1000f).fontFamily
        }
        // LIGHT_GRADE (20) to 100 in steps of 10.
        assertEquals(9, families.distinct().size)
        // Equal frames get the same cached instance, not a fresh equal one.
        assertSame(families[999], families[1000])
    }

    @Test
    fun theCacheIsBounded() {
        repeat(5) {
            for (w in 100..900 step 5) {
                for (r in 0..100 step 5) {
                    labelMedium.withFlexAxes(weight = w.toFloat(), roundness = r.toFloat())
                }
            }
        }
        assertTrue("$cachedFamilyCount", cachedFamilyCount <= GoogleSansFlexAxes.MAX_FAMILIES)
    }

    @Test
    fun nothingMovedReturnsTheSameStyle() {
        assertSame(labelMedium, labelMedium.withFlexAxes())
        assertSame(labelMedium, labelMedium.withFlexAxes(gradeBoost = 4f))
    }

    @Test
    fun otherFontsAreLeftAlone() {
        val system = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp)
        assertSame(system, system.withFlexAxes(weight = 700f, gradeBoost = 80f))
        val sn = typographyFor(AppFont.SN_PRO, latinScript = true).bodyLarge
        assertSame(sn, sn.withFlexAxes(roundness = 100f))
    }

    @Test
    fun gradeKeepsTheWeightAndAnimatedWeightPinsIt() {
        val graded = labelMedium.withFlexAxes(gradeBoost = 40f)
        assertEquals(labelMedium.fontWeight, graded.fontWeight)
        assertEquals(60, graded.flexKeyOrNull()!!.grade)

        val weighted = labelMedium.withFlexAxes(weight = 640f)
        assertEquals(FontWeight(650), weighted.fontWeight)
        assertEquals(FontSynthesis.None, weighted.fontSynthesis)
        assertEquals(650, weighted.flexKeyOrNull()!!.weight)
    }
}
