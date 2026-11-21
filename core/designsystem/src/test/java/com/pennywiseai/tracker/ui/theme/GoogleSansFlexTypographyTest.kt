package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.data.preferences.AppFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google Sans Flex ships as one variable font, so each weight is a Font entry
 * pinning `wght`. Compose resolves a style's weight to the closest *declared*
 * entry: a missing 700 would render every `*Emphasized` style at 600, identical
 * to its base. Every weight the scale uses must have its own matching instance,
 * at its role's roundness and optical size and the theme's grade.
 */
class GoogleSansFlexTypographyTest {

    private val typography = typographyFor(AppFont.GOOGLE_SANS_FLEX, latinScript = true, darkTheme = false)

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
            assertDeclared(name, style, GoogleSansFlexAxes.HEADING_ROUNDNESS, GoogleSansFlexAxes.LIGHT_GRADE)
        }
        bodyStyles.forEach { (name, style) ->
            assertDeclared(name, style, GoogleSansFlexAxes.BODY_ROUNDNESS, GoogleSansFlexAxes.LIGHT_GRADE)
        }
    }

    @Test
    fun opticalSizeFollowsEachRolesFontSize() {
        (headingStyles + bodyStyles).filterKeys { !it.isLabel() }.forEach { (name, style) ->
            assertEquals(name, style.fontSize.value.toInt(), style.flexKeyOrNull()!!.opticalSize)
        }
    }

    @Test
    fun labelsKeepTheWidthsTheirSlotsWereSizedFor() {
        // A smaller opsz widens advances and wrapped "Transactions" in the nav bar.
        bodyStyles.filterKeys { it.isLabel() }.forEach { (name, style) ->
            assertEquals(name, GoogleSansFlexAxes.LABEL_OPTICAL_SIZE.toInt(), style.flexKeyOrNull()!!.opticalSize)
        }
    }

    @Test
    fun darkThemeDrawsALowerGrade() {
        val dark = typographyFor(AppFont.GOOGLE_SANS_FLEX, latinScript = true, darkTheme = true)
        assertDeclared("dark bodyLarge", dark.bodyLarge, GoogleSansFlexAxes.BODY_ROUNDNESS, GoogleSansFlexAxes.DARK_GRADE)
        assertDeclared("dark titleLarge", dark.titleLarge, GoogleSansFlexAxes.HEADING_ROUNDNESS, GoogleSansFlexAxes.DARK_GRADE)
        assertTrue(GoogleSansFlexAxes.DARK_GRADE < GoogleSansFlexAxes.LIGHT_GRADE)
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
            listOf(GoogleSansFlexFamily, typography.bodyLarge.fontFamily, typography.headlineSmall.fontFamily)
                .forEach { assertNotNull("${weight.weight} in $it", fontFor(it, weight)) }
        }
    }

    @Test
    fun moneyIsSquareAtItsOwnOpticalSize() {
        // heroAmount is displaySmallEmphasized (36sp, a rounded heading) drawn as money.
        val hero = typography.displaySmallEmphasized.asMoney(typography.bodyLarge.fontFamily)
        val key = hero.flexKeyOrNull()!!
        assertEquals(GoogleSansFlexAxes.BODY_ROUNDNESS.toInt(), key.roundness)
        assertEquals(36, key.opticalSize)
        assertEquals(GoogleSansFlexAxes.LIGHT_GRADE.toInt(), key.grade)
        assertEquals("tnum", hero.fontFeatureSettings)
        assertNotNull(fontFor(hero.fontFamily, FontWeight.Bold))
    }

    @Test
    fun otherFontsKeepTheBodyFamilyForMoney() {
        val sn = typographyFor(AppFont.SN_PRO, latinScript = true)
        assertSame(SNProFontFamily, sn.displaySmall.asMoney(sn.bodyLarge.fontFamily).fontFamily)
    }

    private fun assertDeclared(label: String, style: TextStyle, roundness: Float, grade: Float) {
        val weight = style.fontWeight!!
        val font = fontFor(style.fontFamily, weight)
        assertNotNull("$label: no Font declared for weight ${weight.weight}", font)
        font!!
        assertEquals("$label: wght", weight.weight, font.key.weight)
        assertEquals("$label: ROND", roundness.toInt(), font.key.roundness)
        assertEquals("$label: GRAD", grade.toInt(), font.key.grade)
        val opticalSize = if (label.isLabel()) GoogleSansFlexAxes.LABEL_OPTICAL_SIZE else style.fontSize.value
        assertEquals("$label: opsz", quantizeOpticalSize(opticalSize), font.key.opticalSize)
        assertEquals("$label: wght setting", weight.weight.toFloat(), font.axis("wght"))
        assertEquals("$label: opsz setting", font.key.opticalSize.toFloat(), font.axis("opsz"))
    }

    private fun String.isLabel() = startsWith("label")

    private fun fontFor(family: FontFamily?, weight: FontWeight): GoogleSansFlexFont? =
        (family as FontListFontFamily).fonts.filterIsInstance<GoogleSansFlexFont>().singleOrNull { it.weight == weight }

    private fun GoogleSansFlexFont.axis(tag: String): Float? =
        variationSettings.settings.singleOrNull { it.axisName == tag }?.toVariationValue(null)
}
