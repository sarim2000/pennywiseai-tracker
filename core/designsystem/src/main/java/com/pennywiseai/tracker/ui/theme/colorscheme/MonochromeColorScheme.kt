// Ported from Mihon (https://github.com/mihonapp/mihon), Apache-2.0.
package com.pennywiseai.tracker.ui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object MonochromeColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFFFFFFF),
        onPrimaryContainer = Color(0xFF000000),
        secondary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF777777),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFF777777),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFFFFFFF),
        onTertiaryContainer = Color(0xFF000000),
        // A finance app needs over-budget/overdraft to stand out, even in Monochrome.
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF000000),
        onSurfaceVariant = Color(0xFFFFFFFF),
        outline = Color(0xFFFFFFFF),
        outlineVariant = Color(0xFFFFFFFF),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF000000),
        inversePrimary = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF000000),
        surfaceContainer = Color(0xFF000000),
        surfaceContainerHigh = Color(0xFF000000),
        surfaceContainerHighest = Color(0xFF000000),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF000000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF000000),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFF000000),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF888888),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFF888888),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF000000),
        onTertiaryContainer = Color(0xFFFFFFFF),
        // A finance app needs over-budget/overdraft to stand out, even in Monochrome.
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF000000),
        outline = Color(0xFF000000),
        outlineVariant = Color(0xFF000000),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF000000),
        inverseOnSurface = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFFFFFFF),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        surfaceContainerHighest = Color(0xFFFFFFFF),
    )
}
