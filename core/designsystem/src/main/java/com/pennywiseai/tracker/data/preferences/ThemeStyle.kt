package com.pennywiseai.tracker.data.preferences

/** Lives in :core:designsystem under its historical package. Stored in DataStore by name: never rename an entry. */
enum class ThemeStyle {
    BRANDED, // Custom accent colors
    DYNAMIC, // Wallpaper colors (Material You)

    // Fixed palettes ported from Mihon (ui/theme/colorscheme).
    CATPPUCCIN,
    GREEN_APPLE,
    LAVENDER,
    MIDNIGHT_DUSK,
    NORD,
    STRAWBERRY_DAIQUIRI,
    TAKO,
    TEAL_TURQUOISE,
    TIDAL_WAVE,
    YIN_YANG,
    YOTSUBA,
    TOKYO_NIGHT,
    MONOCHROME,
}
