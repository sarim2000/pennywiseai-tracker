package com.pennywiseai.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.ui.effects.LocalBlurEffects

private val LightColorScheme = lightColorScheme(
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

private val DarkColorScheme = darkColorScheme(
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

// Custom color extensions
val ColorScheme.success: Color
    @Composable
    get() = if (isSystemInDarkTheme()) success_dark else success_light

val ColorScheme.warning: Color
    @Composable
    get() = if (isSystemInDarkTheme()) warning_dark else warning_light

val ColorScheme.income: Color
    @Composable
    get() = if (isSystemInDarkTheme()) income_dark else income_light

val ColorScheme.expense: Color
    @Composable
    get() = if (isSystemInDarkTheme()) expense_dark else expense_light

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PennyWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    accentColor: AccentColor = AccentColor.SURFACE,
    isAmoledMode: Boolean = false,
    appFont: AppFont = AppFont.SYSTEM,
    blurEffects: Boolean = true,
    content: @Composable () -> Unit
) {
    var colorScheme = when {
        themeStyle == ThemeStyle.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeStyle == ThemeStyle.BRANDED -> {
            if (darkTheme) getCustomDarkColorScheme(accentColor) else getCustomLightColorScheme(accentColor)
        }
        // Fallback for older Android without dynamic color support
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Apply AMOLED black if enabled in dark mode
    if (darkTheme && isAmoledMode) {
        colorScheme = colorScheme.copy(
            background = amoled_background,
            surface = amoled_surface,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window

        SideEffect {
            // Enable edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Control whether status bar icons should be dark or light
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val fontFamily = when (appFont) {
        AppFont.SYSTEM -> FontFamily.Default
        AppFont.SN_PRO -> SNProFontFamily
    }

    CompositionLocalProvider(LocalBlurEffects provides blurEffects) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = getTypography(fontFamily = fontFamily),
            shapes = Shapes,
            content = content
        )
    }
}

fun getCustomLightColorScheme(accent: AccentColor): ColorScheme {
    val primaryColor = when (accent) {
        AccentColor.ROSE -> Dawn_Rose
        AccentColor.IRIS -> Dawn_Iris
        AccentColor.PINE -> Dawn_Pine
        AccentColor.GOLD -> Dawn_Gold
        AccentColor.LOVE -> Dawn_Love
        AccentColor.FOAM -> Dawn_Foam
        AccentColor.MUTED -> Dawn_Muted
        AccentColor.SUBTLE -> Dawn_Subtle
        AccentColor.TEXT -> Dawn_Text
        AccentColor.HIGHLIGHT -> Dawn_Highlight
        AccentColor.SURFACE -> Dawn_Surface
        AccentColor.OVERLAY -> Dawn_Overlay
    }
    val secondaryColor = when (accent) {
        AccentColor.ROSE -> Dawn_Rose_secondary
        AccentColor.IRIS -> Dawn_Iris_secondary
        AccentColor.PINE -> Dawn_Pine_secondary
        AccentColor.GOLD -> Dawn_Gold_secondary
        AccentColor.LOVE -> Dawn_Love_secondary
        AccentColor.FOAM -> Dawn_Foam_secondary
        AccentColor.MUTED -> Dawn_Muted_secondary
        AccentColor.SUBTLE -> Dawn_Subtle_secondary
        AccentColor.TEXT -> Dawn_Text_secondary
        AccentColor.HIGHLIGHT -> Dawn_Highlight_secondary
        AccentColor.SURFACE -> Dawn_Surface_secondary
        AccentColor.OVERLAY -> Dawn_Overlay_secondary
    }
    val tertiaryColor = when (accent) {
        AccentColor.ROSE -> Dawn_Rose_tertiary
        AccentColor.IRIS -> Dawn_Iris_tertiary
        AccentColor.PINE -> Dawn_Pine_tertiary
        AccentColor.GOLD -> Dawn_Gold_tertiary
        AccentColor.LOVE -> Dawn_Love_tertiary
        AccentColor.FOAM -> Dawn_Foam_tertiary
        AccentColor.MUTED -> Dawn_Muted_tertiary
        AccentColor.SUBTLE -> Dawn_Subtle_tertiary
        AccentColor.TEXT -> Dawn_Text_tertiary
        AccentColor.HIGHLIGHT -> Dawn_Highlight_tertiary
        AccentColor.SURFACE -> Dawn_Surface_tertiary
        AccentColor.OVERLAY -> Dawn_Overlay_tertiary
    }

    return lightColorScheme(
        primary = primaryColor,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = primaryColor,
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF000000),
        secondary = secondaryColor,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = secondaryColor,
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = tertiaryColor,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = tertiaryColor,
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFFAF4ED),
        onBackground = Color(0xFF575279),
        surface = Color(0xFFFFFAF3),
        onSurface = Color(0xFF575279),
        surfaceVariant = Color(0xFFF2E9E1),
        onSurfaceVariant = Color(0xFF6E6A86),
        inverseSurface = Color(0xFF26233A),
        inverseOnSurface = Color(0xFFE0DEF4),
        error = Dawn_Love,
        onError = Color(0xFFFFFFFF),
        errorContainer = Dawn_Love,
        onErrorContainer = Color(0xFFFFFFFF),
        surfaceBright = Color(0xFFFFFAF3),
        surfaceDim = Color(0xFFF2E9E1),
        surfaceContainer = Color(0xFFFAF4ED),
        surfaceContainerHigh = Color(0xFFF2E9E1),
        surfaceContainerHighest = Color(0xFFE6DDD5),
        surfaceContainerLow = Color(0xFFFFFAF3),
        surfaceContainerLowest = Color(0xFFFFFFFF)
    )
}

fun getCustomDarkColorScheme(accent: AccentColor): ColorScheme {
    val primaryColor = when (accent) {
        AccentColor.ROSE -> RosePine_Rose
        AccentColor.IRIS -> RosePine_Iris
        AccentColor.PINE -> RosePine_Pine
        AccentColor.GOLD -> RosePine_Gold
        AccentColor.LOVE -> RosePine_Love
        AccentColor.FOAM -> RosePine_Foam
        AccentColor.MUTED -> RosePine_Muted
        AccentColor.SUBTLE -> RosePine_Subtle
        AccentColor.TEXT -> RosePine_Text
        AccentColor.HIGHLIGHT -> RosePine_Highlight
        AccentColor.SURFACE -> RosePine_Surface
        AccentColor.OVERLAY -> RosePine_Overlay
    }
    val secondaryColor = when (accent) {
        AccentColor.ROSE -> RosePine_Rose_secondary
        AccentColor.IRIS -> RosePine_Iris_secondary
        AccentColor.PINE -> RosePine_Pine_secondary
        AccentColor.GOLD -> RosePine_Gold_secondary
        AccentColor.LOVE -> RosePine_Love_secondary
        AccentColor.FOAM -> RosePine_Foam_secondary
        AccentColor.MUTED -> RosePine_Muted_secondary
        AccentColor.SUBTLE -> RosePine_Subtle_secondary
        AccentColor.TEXT -> RosePine_Text_secondary
        AccentColor.HIGHLIGHT -> RosePine_Highlight_secondary
        AccentColor.SURFACE -> RosePine_Surface_secondary
        AccentColor.OVERLAY -> RosePine_Overlay_secondary
    }
    val tertiaryColor = when (accent) {
        AccentColor.ROSE -> RosePine_Rose_tertiary
        AccentColor.IRIS -> RosePine_Iris_tertiary
        AccentColor.PINE -> RosePine_Pine_tertiary
        AccentColor.GOLD -> RosePine_Gold_tertiary
        AccentColor.LOVE -> RosePine_Love_tertiary
        AccentColor.FOAM -> RosePine_Foam_tertiary
        AccentColor.MUTED -> RosePine_Muted_tertiary
        AccentColor.SUBTLE -> RosePine_Subtle_tertiary
        AccentColor.TEXT -> RosePine_Text_tertiary
        AccentColor.HIGHLIGHT -> RosePine_Highlight_tertiary
        AccentColor.SURFACE -> RosePine_Surface_tertiary
        AccentColor.OVERLAY -> RosePine_Overlay_tertiary
    }

    return darkColorScheme(
        primary = primaryColor,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = primaryColor,
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFFFFFFF),
        secondary = secondaryColor,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = secondaryColor,
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = tertiaryColor,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = tertiaryColor,
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFF191724),
        onBackground = Color(0xFFE0DEF4),
        surface = Color(0xFF1F1D2E),
        onSurface = Color(0xFFE0DEF4),
        surfaceVariant = Color(0xFF26233A),
        onSurfaceVariant = Color(0xFF908CAA),
        inverseSurface = Color(0xFFE0DEF4),
        inverseOnSurface = Color(0xFF26233A),
        error = RosePine_Love,
        onError = Color(0xFFFFFFFF),
        errorContainer = RosePine_Love,
        onErrorContainer = Color(0xFFFFFFFF),
        surfaceBright = Color(0xFF26233A),
        surfaceDim = Color(0xFF191724),
        surfaceContainer = Color(0xFF1F1D2E),
        surfaceContainerHigh = Color(0xFF26233A),
        surfaceContainerHighest = Color(0xFF403D52),
        surfaceContainerLow = Color(0xFF1F1D2E),
        surfaceContainerLowest = Color(0xFF191724)
    )
}
