package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.pennywiseai.tracker.data.preferences.AppFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled fonts have no Arabic/Cyrillic/… glyphs: other scripts get the system font. */
class NonLatinFontFallbackTest {

    @Test
    fun `a non-Latin locale gets the system font whatever font is picked`() {
        AppFont.entries.forEach { font ->
            val t = typographyFor(font, latinScript = false)
            assertEquals(font.name, FontFamily.Default, t.titleLarge.fontFamily)
            assertEquals(font.name, FontFamily.Default, t.bodyLarge.fontFamily)
        }
    }

    @Test
    fun `a Latin locale keeps the picked font`() {
        val t = typographyFor(AppFont.GOOGLE_SANS_FLEX, latinScript = true)
        assertTrue(t.titleLarge.isGoogleSansFlex)
        assertTrue(t.bodyLarge.isGoogleSansFlex)
        assertEquals(SNProFontFamily, typographyFor(AppFont.SN_PRO, latinScript = true).bodyLarge.fontFamily)
    }
}
