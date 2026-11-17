package com.pennywiseai.tracker.ui.theme.colorscheme

import androidx.compose.material3.ColorScheme

/** A fixed light/dark palette pair, ported from Mihon's theme set. AMOLED is applied by PennyWiseTheme. */
internal abstract class BaseColorScheme {
    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme
}
