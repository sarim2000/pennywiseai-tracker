package com.pennywiseai.tracker.ui.theme

import com.pennywiseai.tracker.data.preferences.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The semantic colours (income, expense, …) pick their variant from the scheme the
 * app is drawing with, not the system setting, because Appearance can override it.
 */
class ColorSchemeIsDarkTest {

    @Test
    fun lightScheme_isNotDark() {
        assertFalse(LightColorScheme.isDark)
    }

    @Test
    fun darkScheme_isDark() {
        assertTrue(DarkColorScheme.isDark)
    }

    @Test
    fun amoledScheme_isDark() {
        assertTrue(DarkColorScheme.withAmoledSurfaces().isDark)
        AccentColor.entries.forEach { accent ->
            assertTrue(accent.name, getCustomDarkColorScheme(accent).withAmoledSurfaces().isDark)
        }
    }

    @Test
    fun brandedSchemes_followLightAndDark_forEveryAccent() {
        AccentColor.entries.forEach { accent ->
            assertFalse(accent.name, getCustomLightColorScheme(accent).isDark)
            assertTrue(accent.name, getCustomDarkColorScheme(accent).isDark)
        }
    }

    @Test
    fun semanticTokens_useLightVariants_onLightScheme() {
        with(LightColorScheme) {
            assertEquals(income_light, income)
            assertEquals(expense_light, expense)
            assertEquals(credit_light, credit)
            assertEquals(transfer_light, transfer)
            assertEquals(investment_light, investment)
            assertEquals(loan_light, loan)
            assertEquals(success_light, success)
            assertEquals(warning_light, warning)
        }
    }

    @Test
    fun semanticTokens_useDarkVariants_onDarkScheme() {
        with(DarkColorScheme) {
            assertEquals(income_dark, income)
            assertEquals(expense_dark, expense)
            assertEquals(credit_dark, credit)
            assertEquals(transfer_dark, transfer)
            assertEquals(investment_dark, investment)
            assertEquals(loan_dark, loan)
            assertEquals(success_dark, success)
            assertEquals(warning_dark, warning)
        }
    }
}
