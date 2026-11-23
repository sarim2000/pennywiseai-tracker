package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.ColorScheme
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.ui.theme.DarkColorScheme
import com.pennywiseai.tracker.ui.theme.LightColorScheme
import com.pennywiseai.tracker.ui.theme.fixedColorScheme
import com.pennywiseai.tracker.ui.theme.getCustomDarkColorScheme
import com.pennywiseai.tracker.ui.theme.getCustomLightColorScheme
import com.pennywiseai.tracker.ui.theme.withAmoledSurfaces

/** Every scheme the app ships, named: the defaults, each Rosé Pine accent and each fixed palette, in light, dark and AMOLED. */
internal val builtInSchemes: List<Pair<String, ColorScheme>> = buildList {
    fun addAll(name: String, light: ColorScheme, dark: ColorScheme) {
        add("$name/light" to light)
        add("$name/dark" to dark)
        add("$name/amoled" to dark.withAmoledSurfaces())
    }
    addAll("default", LightColorScheme, DarkColorScheme)
    AccentColor.entries.forEach { addAll("rosePine-${it.name}", getCustomLightColorScheme(it), getCustomDarkColorScheme(it)) }
    ThemeStyle.entries.forEach { style ->
        val light = style.fixedColorScheme(darkTheme = false) ?: return@forEach
        val dark = style.fixedColorScheme(darkTheme = true) ?: return@forEach
        addAll(style.name, light, dark)
    }
}
