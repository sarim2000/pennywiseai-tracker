package com.pennywiseai.tracker.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * These enums' names are the values UserPreferencesRepository stores in DataStore and reads
 * back with `valueOf`, falling back to the default on an unknown name. Renaming an entry
 * would silently reset that choice for existing users, so the names are literals here.
 */
class AppearancePreferenceNamesTest {

    @Test
    fun accentColorNamesAreStable() = assertEquals(
        setOf(
            "ROSE", "IRIS", "PINE", "GOLD", "LOVE", "FOAM",
            "MUTED", "SUBTLE", "TEXT", "HIGHLIGHT", "SURFACE", "OVERLAY",
        ),
        AccentColor.entries.map { it.name }.toSet(),
    )

    @Test
    fun appFontNamesAreStable() = assertEquals(
        setOf("SYSTEM", "SN_PRO", "GOOGLE_SANS_FLEX"),
        AppFont.entries.map { it.name }.toSet(),
    )

    @Test
    fun appFontDefaultIsGoogleSansFlex() = assertEquals(AppFont.GOOGLE_SANS_FLEX, AppFont.DEFAULT)

    @Test
    fun themeStyleNamesAreStable() = assertEquals(
        setOf("BRANDED", "DYNAMIC"),
        ThemeStyle.entries.map { it.name }.toSet(),
    )
}
