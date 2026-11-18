package com.pennywiseai.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.ui.effects.LocalBlurEffects
import com.pennywiseai.tracker.ui.theme.colorscheme.BaseColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.CatppuccinColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.GreenAppleColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.LavenderColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.MidnightDuskColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.MonochromeColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.NordColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.StrawberryColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.TakoColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.TealTurquoiseColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.TidalWaveColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.TokyoNightColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.YinYangColorScheme
import com.pennywiseai.tracker.ui.theme.colorscheme.YotsubaColorScheme

/** Also read by the home-screen widgets' Glance theme (PennyWiseWidgetTheme in :app). */
val LightColorScheme = lightColorScheme(
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
).withReadableInk()

/** Also read by the home-screen widgets' Glance theme (PennyWiseWidgetTheme in :app). */
val DarkColorScheme = darkColorScheme(
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
).withReadableInk()

/**
 * Whether this scheme is a dark one, read from the scheme itself rather than the
 * system setting. Holds for light, dark, AMOLED, branded and dynamic schemes.
 */
val ColorScheme.isDark: Boolean
    get() = background.luminance() < 0.5f

/**
 * Whether the app is drawing in dark mode. Use this, not `isSystemInDarkTheme()`:
 * Settings → Appearance can force Light or Dark regardless of the system.
 */
@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.isDark

// Semantic colours. Each picks its light/dark base, then readableOn() nudges it toward
// black/white only as far as this scheme's surfaces need for 4.5:1 text. A red stays a
// red, it just gets as dark (or light) as the palette demands.
val ColorScheme.success: Color
    get() = (if (isDark) success_dark else success_light).readableOn(neutralSurfaces)

val ColorScheme.warning: Color
    get() = (if (isDark) warning_dark else warning_light).readableOn(neutralSurfaces)

val ColorScheme.income: Color
    get() = (if (isDark) income_dark else income_light).readableOn(neutralSurfaces)

val ColorScheme.expense: Color
    get() = (if (isDark) expense_dark else expense_light).readableOn(neutralSurfaces)

/** Whether spending amounts draw in [expense] red (the default) or plain ink. */
val LocalRedSpending = staticCompositionLocalOf { true }

/** A spending amount: [expense] red, or [ColorScheme.onSurface] when the user turned red off. */
val ColorScheme.spending: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalRedSpending.current) expense else onSurface

val ColorScheme.credit: Color
    get() = (if (isDark) credit_dark else credit_light).readableOn(neutralSurfaces)

val ColorScheme.transfer: Color
    get() = (if (isDark) transfer_dark else transfer_light).readableOn(neutralSurfaces)

val ColorScheme.investment: Color
    get() = (if (isDark) investment_dark else investment_light).readableOn(neutralSurfaces)

val ColorScheme.loan: Color
    get() = (if (isDark) loan_dark else loan_light).readableOn(neutralSurfaces)

/**
 * The scheme [PennyWiseTheme] draws with for these settings. Appearance also uses it to
 * paint each theme's preview card in that theme's own colours.
 */
@Composable
fun pennyWiseColorScheme(
    themeStyle: ThemeStyle,
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    accentColor: AccentColor = AccentColor.SURFACE,
    isAmoledMode: Boolean = false,
): ColorScheme {
    val palette = themeStyle.palette()
    val colorScheme = when {
        palette != null -> if (darkTheme) palette.darkScheme else palette.lightScheme
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
    return if (darkTheme && isAmoledMode) colorScheme.withAmoledSurfaces() else colorScheme
}

/** The fixed palette behind a style, or null for the accent-driven (BRANDED) and wallpaper (DYNAMIC) styles. */
internal fun ThemeStyle.palette(): BaseColorScheme? = when (this) {
    ThemeStyle.BRANDED, ThemeStyle.DYNAMIC -> null
    ThemeStyle.CATPPUCCIN -> CatppuccinColorScheme
    ThemeStyle.GREEN_APPLE -> GreenAppleColorScheme
    ThemeStyle.LAVENDER -> LavenderColorScheme
    ThemeStyle.MIDNIGHT_DUSK -> MidnightDuskColorScheme
    ThemeStyle.NORD -> NordColorScheme
    ThemeStyle.STRAWBERRY_DAIQUIRI -> StrawberryColorScheme
    ThemeStyle.TAKO -> TakoColorScheme
    ThemeStyle.TEAL_TURQUOISE -> TealTurquoiseColorScheme
    ThemeStyle.TIDAL_WAVE -> TidalWaveColorScheme
    ThemeStyle.YIN_YANG -> YinYangColorScheme
    ThemeStyle.YOTSUBA -> YotsubaColorScheme
    ThemeStyle.TOKYO_NIGHT -> TokyoNightColorScheme
    ThemeStyle.MONOCHROME -> MonochromeColorScheme
}

@Composable
fun PennyWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    accentColor: AccentColor = AccentColor.SURFACE,
    isAmoledMode: Boolean = false,
    appFont: AppFont = AppFont.DEFAULT,
    blurEffects: Boolean = true,
    redSpending: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = pennyWiseColorScheme(themeStyle, darkTheme, dynamicColor, accentColor, isAmoledMode)

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

    val typography = remember(appFont) {
        when (appFont) {
            AppFont.SYSTEM -> getTypography(FontFamily.Default)
            AppFont.SN_PRO -> getTypography(SNProFontFamily)
            AppFont.GOOGLE_SANS_FLEX -> getTypography(GoogleSansFlexFamily, GoogleSansFlexHeadingFamily)
        }
    }

    CompositionLocalProvider(LocalBlurEffects provides blurEffects, LocalRedSpending provides redSpending) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            // Springy, overshooting spatial motion for every M3 component.
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}

internal fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = amoled_background,
    surface = amoled_surface,
    surfaceVariant = amoled_surfaceVariant,
    surfaceBright = amoled_surfaceBright,
    surfaceDim = amoled_surfaceDim,
    surfaceContainerLowest = amoled_surfaceContainerLowest,
    surfaceContainerLow = amoled_surfaceContainerLow,
    surfaceContainer = amoled_surfaceContainer,
    surfaceContainerHigh = amoled_surfaceContainerHigh,
    surfaceContainerHighest = amoled_surfaceContainerHighest,
)

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
        onPrimary = primaryColor.bestInk(),
        primaryContainer = primaryColor,
        onPrimaryContainer = primaryColor.bestInk(),
        inversePrimary = primaryColor,
        secondary = secondaryColor,
        onSecondary = secondaryColor.bestInk(),
        secondaryContainer = secondaryColor,
        onSecondaryContainer = secondaryColor.bestInk(),
        tertiary = tertiaryColor,
        onTertiary = tertiaryColor.bestInk(),
        tertiaryContainer = tertiaryColor,
        onTertiaryContainer = tertiaryColor.bestInk(),
        background = Color(0xFFFAF4ED),
        onBackground = Color(0xFF575279),
        surface = Color(0xFFFFFAF3),
        onSurface = Color(0xFF575279),
        surfaceVariant = Color(0xFFF2E9E1),
        onSurfaceVariant = Color(0xFF6E6A86),
        inverseSurface = Color(0xFF26233A),
        inverseOnSurface = Color(0xFFE0DEF4),
        error = Dawn_Love,
        onError = Dawn_Love.bestInk(),
        errorContainer = Dawn_Love,
        onErrorContainer = Dawn_Love.bestInk(),
        surfaceBright = Color(0xFFFFFAF3),
        surfaceDim = Color(0xFFF2E9E1),
        surfaceContainer = Color(0xFFFAF4ED),
        surfaceContainerHigh = Color(0xFFF2E9E1),
        surfaceContainerHighest = Color(0xFFE6DDD5),
        surfaceContainerLow = Color(0xFFFFFAF3),
        surfaceContainerLowest = Color(0xFFFFFFFF)
    ).withReadableInk()
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
        onPrimary = primaryColor.bestInk(),
        primaryContainer = primaryColor,
        onPrimaryContainer = primaryColor.bestInk(),
        inversePrimary = primaryColor,
        secondary = secondaryColor,
        onSecondary = secondaryColor.bestInk(),
        secondaryContainer = secondaryColor,
        onSecondaryContainer = secondaryColor.bestInk(),
        tertiary = tertiaryColor,
        onTertiary = tertiaryColor.bestInk(),
        tertiaryContainer = tertiaryColor,
        onTertiaryContainer = tertiaryColor.bestInk(),
        background = Color(0xFF191724),
        onBackground = Color(0xFFE0DEF4),
        surface = Color(0xFF1F1D2E),
        onSurface = Color(0xFFE0DEF4),
        surfaceVariant = Color(0xFF26233A),
        onSurfaceVariant = Color(0xFF908CAA),
        inverseSurface = Color(0xFFE0DEF4),
        inverseOnSurface = Color(0xFF26233A),
        error = RosePine_Love,
        onError = RosePine_Love.bestInk(),
        errorContainer = RosePine_Love,
        onErrorContainer = RosePine_Love.bestInk(),
        surfaceBright = Color(0xFF26233A),
        surfaceDim = Color(0xFF191724),
        surfaceContainer = Color(0xFF1F1D2E),
        surfaceContainerHigh = Color(0xFF26233A),
        surfaceContainerHighest = Color(0xFF403D52),
        surfaceContainerLow = Color(0xFF1F1D2E),
        surfaceContainerLowest = Color(0xFF191724)
    ).withReadableInk()
}
