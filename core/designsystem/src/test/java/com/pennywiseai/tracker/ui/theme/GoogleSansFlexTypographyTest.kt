package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import com.pennywiseai.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Google Sans Flex ships as one variable font, so each weight is a Font entry
 * pinning `wght`. Compose resolves a style's weight to the closest *declared*
 * entry: a missing 700 would render every `*Emphasized` style at 600, identical
 * to its base. Every weight the scale uses must have its own matching instance.
 */
class GoogleSansFlexTypographyTest {

    private val typography = getTypography(GoogleSansFlexFamily, GoogleSansFlexHeadingFamily)

    private val headingStyles: Map<String, TextStyle> = with(typography) {
        mapOf(
            "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "displayLargeEmphasized" to displayLargeEmphasized,
            "displayMediumEmphasized" to displayMediumEmphasized,
            "displaySmallEmphasized" to displaySmallEmphasized,
            "headlineLargeEmphasized" to headlineLargeEmphasized,
            "headlineMediumEmphasized" to headlineMediumEmphasized,
            "headlineSmallEmphasized" to headlineSmallEmphasized,
            "titleLargeEmphasized" to titleLargeEmphasized,
            "titleMediumEmphasized" to titleMediumEmphasized,
            "titleSmallEmphasized" to titleSmallEmphasized,
        )
    }

    private val bodyStyles: Map<String, TextStyle> = with(typography) {
        mapOf(
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
            "bodyLargeEmphasized" to bodyLargeEmphasized,
            "bodyMediumEmphasized" to bodyMediumEmphasized,
            "bodySmallEmphasized" to bodySmallEmphasized,
            "labelLargeEmphasized" to labelLargeEmphasized,
            "labelMediumEmphasized" to labelMediumEmphasized,
            "labelSmallEmphasized" to labelSmallEmphasized,
        )
    }

    @Test
    fun everyStyleWeightHasItsOwnInstance() {
        headingStyles.forEach { (name, style) ->
            assertDeclared(name, style.fontFamily, style.fontWeight!!, GOOGLE_SANS_FLEX_HEADING_ROUNDNESS)
        }
        bodyStyles.forEach { (name, style) ->
            assertDeclared(name, style.fontFamily, style.fontWeight!!, 0f)
        }
    }

    @Test
    fun emphasizedIsHeavierThanItsBase() {
        (headingStyles + bodyStyles).filterKeys { it.endsWith("Emphasized") }.forEach { (name, style) ->
            val base = (headingStyles + bodyStyles).getValue(name.removeSuffix("Emphasized"))
            assertEquals(name, base.fontWeight!!.weight + 100, style.fontWeight!!.weight)
        }
    }

    @Test
    fun everyWeightFromThinToBlackIsDeclared() {
        // Covers weights set outside the scale: PennyWiseText's Bold money, a stray Black.
        (100..900 step 100).map { FontWeight(it) }.forEach { weight ->
            assertDeclared("body ${weight.weight}", GoogleSansFlexFamily, weight, 0f)
            assertDeclared("heading ${weight.weight}", GoogleSansFlexHeadingFamily, weight, GOOGLE_SANS_FLEX_HEADING_ROUNDNESS)
        }
    }

    private fun assertDeclared(label: String, family: FontFamily?, weight: FontWeight, roundness: Float) {
        val fonts = (family as FontListFontFamily).fonts.filterIsInstance<ResourceFont>()
        val font = fonts.singleOrNull { it.weight == weight }
        assertNotNull("$label: no Font declared for weight ${weight.weight}", font)
        font!!
        assertEquals("$label: font file", R.font.google_sans_flex, font.resId)
        assertEquals("$label: wght", weight.weight.toFloat(), font.axis("wght"))
        assertEquals("$label: ROND", roundness, font.axis("ROND"))
    }

    private fun ResourceFont.axis(tag: String): Float? =
        variationSettings.settings.singleOrNull { it.axisName == tag }?.toVariationValue(null)
}
