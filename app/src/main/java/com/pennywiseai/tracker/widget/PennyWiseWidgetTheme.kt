package com.pennywiseai.tracker.widget

import android.content.res.Configuration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.theme.*

object PennyWiseWidgetTheme {

    private val LightColors = lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        onError = md_theme_light_onError,
        errorContainer = md_theme_light_errorContainer,
        onErrorContainer = md_theme_light_onErrorContainer,
        outline = md_theme_light_outline,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        inverseSurface = md_theme_light_inverseSurface,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inversePrimary = md_theme_light_inversePrimary,
        surfaceTint = md_theme_light_surfaceTint,
        outlineVariant = md_theme_light_outlineVariant,
        scrim = md_theme_light_scrim,
    )

    private val DarkColors = darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        onError = md_theme_dark_onError,
        errorContainer = md_theme_dark_errorContainer,
        onErrorContainer = md_theme_dark_onErrorContainer,
        outline = md_theme_dark_outline,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        inverseSurface = md_theme_dark_inverseSurface,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

    val colors = ColorProviders(
        light = LightColors,
        dark = DarkColors
    )

    @Composable
    fun budgetStatusColor(percentageUsed: Float): ColorProvider {
        val isDark = isWidgetDarkMode()
        return when {
            percentageUsed > 90f -> ColorProvider(if (isDark) budget_danger_dark else budget_danger_light)
            percentageUsed > 70f -> ColorProvider(if (isDark) budget_warning_dark else budget_warning_light)
            else -> ColorProvider(if (isDark) budget_safe_dark else budget_safe_light)
        }
    }

    @Composable
    fun savingsColor(isPositive: Boolean): ColorProvider {
        val isDark = isWidgetDarkMode()
        return if (isPositive) {
            ColorProvider(if (isDark) budget_safe_dark else budget_safe_light)
        } else {
            ColorProvider(if (isDark) budget_danger_dark else budget_danger_light)
        }
    }

    @Composable
    fun transactionAmountColor(type: TransactionType): ColorProvider {
        val isDark = isWidgetDarkMode()
        return when (type) {
            TransactionType.INCOME ->
                ColorProvider(if (isDark) income_dark else income_light)
            TransactionType.TRANSFER ->
                ColorProvider(if (isDark) transfer_dark else transfer_light)
            else ->
                ColorProvider(if (isDark) expense_dark else expense_light)
        }
    }

    @Composable
    private fun isWidgetDarkMode(): Boolean {
        return (LocalContext.current.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
