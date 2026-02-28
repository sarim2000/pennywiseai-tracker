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
    accentColor: AccentColor = AccentColor.BLUE,
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
        AccentColor.ROSEWATER -> Latte_Rosewater
        AccentColor.FLAMINGO -> Latte_Flamingo
        AccentColor.PINK -> Latte_Pink
        AccentColor.MAUVE -> Latte_Mauve
        AccentColor.RED -> Latte_Red
        AccentColor.PEACH -> Latte_Peach
        AccentColor.YELLOW -> Latte_Yellow
        AccentColor.GREEN -> Latte_Green
        AccentColor.TEAL -> Latte_Teal
        AccentColor.SAPPHIRE -> Latte_Sapphire
        AccentColor.BLUE -> Latte_Blue
        AccentColor.LAVENDER -> Latte_Lavender
    }
    val secondaryColor = when (accent) {
        AccentColor.ROSEWATER -> Latte_Rosewater_secondary
        AccentColor.FLAMINGO -> Latte_Flamingo_secondary
        AccentColor.PINK -> Latte_Pink_secondary
        AccentColor.MAUVE -> Latte_Mauve_secondary
        AccentColor.RED -> Latte_Red_secondary
        AccentColor.PEACH -> Latte_Peach_secondary
        AccentColor.YELLOW -> Latte_Yellow_secondary
        AccentColor.GREEN -> Latte_Green_secondary
        AccentColor.TEAL -> Latte_Teal_secondary
        AccentColor.SAPPHIRE -> Latte_Sapphire_secondary
        AccentColor.BLUE -> Latte_Blue_secondary
        AccentColor.LAVENDER -> Latte_Lavender_secondary
    }
    val tertiaryColor = when (accent) {
        AccentColor.ROSEWATER -> Latte_Rosewater_tertiary
        AccentColor.FLAMINGO -> Latte_Flamingo_tertiary
        AccentColor.PINK -> Latte_Pink_tertiary
        AccentColor.MAUVE -> Latte_Mauve_tertiary
        AccentColor.RED -> Latte_Red_tertiary
        AccentColor.PEACH -> Latte_Peach_tertiary
        AccentColor.YELLOW -> Latte_Yellow_tertiary
        AccentColor.GREEN -> Latte_Green_tertiary
        AccentColor.TEAL -> Latte_Teal_tertiary
        AccentColor.SAPPHIRE -> Latte_Sapphire_tertiary
        AccentColor.BLUE -> Latte_Blue_tertiary
        AccentColor.LAVENDER -> Latte_Lavender_tertiary
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
        background = Color(0xFFE2E2E9),
        onBackground = Color(0xFF1A1B20),
        surface = Color(0xFFE5E5EA),
        onSurface = Color(0xFF1A1B20),
        surfaceVariant = Color(0xFFC4C6D0),
        onSurfaceVariant = Color(0xFF44474F),
        inverseSurface = Color(0xFF2F3036),
        inverseOnSurface = Color(0xFFF0F0F7),
        error = Latte_Red,
        onError = Color(0xFFFFFFFF),
        errorContainer = Latte_Red,
        onErrorContainer = Color(0xFFFFFFFF),
        surfaceBright = Color(0xFFE8E9EC),
        surfaceDim = Color(0xFFD9D9E0),
        surfaceContainer = Color(0xFFF9F9FF),
        surfaceContainerHigh = Color(0xFFE8E7EE),
        surfaceContainerHighest = Color(0xFFE2E2E9),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFF9F9FF)
    )
}

fun getCustomDarkColorScheme(accent: AccentColor): ColorScheme {
    val primaryColor = when (accent) {
        AccentColor.ROSEWATER -> Macchiato_Rosewater_dim
        AccentColor.FLAMINGO -> Macchiato_Flamingo_dim
        AccentColor.PINK -> Macchiato_Pink_dim
        AccentColor.MAUVE -> Macchiato_Mauve_dim
        AccentColor.RED -> Macchiato_Red_dim
        AccentColor.PEACH -> Macchiato_Peach_dim
        AccentColor.YELLOW -> Macchiato_Yellow_dim
        AccentColor.GREEN -> Macchiato_Green_dim
        AccentColor.TEAL -> Macchiato_Teal_dim
        AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim
        AccentColor.BLUE -> Macchiato_Blue_dim
        AccentColor.LAVENDER -> Macchiato_Lavender_dim
    }
    val secondaryColor = when (accent) {
        AccentColor.ROSEWATER -> Macchiato_Rosewater_dim_secondary
        AccentColor.FLAMINGO -> Macchiato_Flamingo_dim_secondary
        AccentColor.PINK -> Macchiato_Pink_dim_secondary
        AccentColor.MAUVE -> Macchiato_Mauve_dim_secondary
        AccentColor.RED -> Macchiato_Red_dim_secondary
        AccentColor.PEACH -> Macchiato_Peach_dim_secondary
        AccentColor.YELLOW -> Macchiato_Yellow_dim_secondary
        AccentColor.GREEN -> Macchiato_Green_dim_secondary
        AccentColor.TEAL -> Macchiato_Teal_dim_secondary
        AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim_secondary
        AccentColor.BLUE -> Macchiato_Blue_dim_secondary
        AccentColor.LAVENDER -> Macchiato_Lavender_dim_secondary
    }
    val tertiaryColor = when (accent) {
        AccentColor.ROSEWATER -> Macchiato_Rosewater_dim_tertiary
        AccentColor.FLAMINGO -> Macchiato_Flamingo_dim_tertiary
        AccentColor.PINK -> Macchiato_Pink_dim_tertiary
        AccentColor.MAUVE -> Macchiato_Mauve_dim_tertiary
        AccentColor.RED -> Macchiato_Red_dim_tertiary
        AccentColor.PEACH -> Macchiato_Peach_dim_tertiary
        AccentColor.YELLOW -> Macchiato_Yellow_dim_tertiary
        AccentColor.GREEN -> Macchiato_Green_dim_tertiary
        AccentColor.TEAL -> Macchiato_Teal_dim_tertiary
        AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim_tertiary
        AccentColor.BLUE -> Macchiato_Blue_dim_tertiary
        AccentColor.LAVENDER -> Macchiato_Lavender_dim_tertiary
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
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF1E1F25),
        onSurfaceVariant = Color(0xFFC4C6D0),
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2F3036),
        error = Macchiato_Red_dim,
        onError = Color(0xFFFFFFFF),
        errorContainer = Macchiato_Red_dim,
        onErrorContainer = Color(0xFFFFFFFF),
        surfaceBright = Color(0xFF37393E),
        surfaceDim = Color(0xFF0C0E13),
        surfaceContainer = Color(0xFF1E1F25),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
        surfaceContainerLow = Color(0xFF1E1F25),
        surfaceContainerLowest = Color(0xFF1A1B20)
    )
}
