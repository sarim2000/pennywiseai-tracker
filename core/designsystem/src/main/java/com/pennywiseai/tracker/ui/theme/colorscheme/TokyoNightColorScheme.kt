// Ported from Mihon (https://github.com/mihonapp/mihon), Apache-2.0.
package com.pennywiseai.tracker.ui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Tokyo Night Moon/Day
 *
 * https://github.com/folke/tokyonight.nvim
 */
internal object TokyoNightColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA0BFFF),
        onPrimary = Color(0xFF1B1D2B),
        primaryContainer = Color(0xFF444A73),
        onPrimaryContainer = Color(0xFFA3BFFF),
        secondary = Color(0xFF86E1FC),
        onSecondary = Color(0xFF1B1D2B),
        secondaryContainer = Color(0xFF444A73),
        onSecondaryContainer = Color(0xFFB2EBFF),
        tertiary = Color(0xFFC099FF),
        onTertiary = Color(0xFF1B1D2B),
        tertiaryContainer = Color(0xFF444A73),
        onTertiaryContainer = Color(0xFFCEB2FF),
        error = Color(0xFFFFA8AB),
        onError = Color(0xFF1B1D2B),
        errorContainer = Color(0xFFC53B53),
        onErrorContainer = Color(0xFFFFF0F0),
        background = Color(0xFF222436),
        onBackground = Color(0xFFC8D3F5),
        surface = Color(0xFF222436),
        onSurface = Color(0xFFC8D3F5),
        surfaceVariant = Color(0xFF444A73),
        onSurfaceVariant = Color(0xFFB3BDEC),
        outline = Color(0xFF828BB8),
        outlineVariant = Color(0xFF444A73),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFC8D3F5),
        inverseOnSurface = Color(0xFF222436),
        inversePrimary = Color(0xFF3A5898),
        surfaceContainerLowest = Color(0xFF1E202F),
        surfaceDim = Color(0xFF222436),
        surfaceContainerLow = Color(0xFF292C44),
        surfaceContainer = Color(0xFF2D304B),
        surfaceContainerHigh = Color(0xFF363B5D),
        surfaceContainerHighest = Color(0xFF3F466F),
        surfaceBright = Color(0xFF434A77),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF1D55A3),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF92A6D5),
        onPrimaryContainer = Color(0xFF15386A),
        inversePrimary = Color(0xFF92A6E5),
        secondary = Color(0xFF007197),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFA9C6D3),
        onSecondaryContainer = Color(0xFF01566A),
        tertiary = Color(0xFF9854F1),
        onTertiary = Color(0xFF090909),
        tertiaryContainer = Color(0xFFCFBFEA),
        onTertiaryContainer = Color(0xFF6834AA),
        background = Color(0xFFE1E2E7),
        onBackground = Color(0xFF2F53A6),
        surface = Color(0xFFE1E2E7),
        onSurface = Color(0xFF2F53A6),
        surfaceVariant = Color(0xFFD1D5E3),
        onSurfaceVariant = Color(0xFF485584),
        surfaceTint = Color(0xFF2E7DE9),
        inverseSurface = Color(0xFF343B58),
        inverseOnSurface = Color(0xFFE1E2E7),
        outline = Color(0xFF68709A),
        outlineVariant = Color(0xFFA8AECB),
        error = Color(0xFFA91943),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFE6B4C7),
        onErrorContainer = Color(0xFF6E132D),
        surfaceDim = Color(0xFFC9CEE0),
        surfaceBright = Color(0xFFE1E2E7),
        surfaceContainerLowest = Color(0xFFE9EAEE),
        surfaceContainerLow = Color(0xFFDFE1E9),
        surfaceContainer = Color(0xFFD8DBE6),
        surfaceContainerHigh = Color(0xFFD1D5E3),
        surfaceContainerHighest = Color(0xFFC9CEE0),
    )
}
