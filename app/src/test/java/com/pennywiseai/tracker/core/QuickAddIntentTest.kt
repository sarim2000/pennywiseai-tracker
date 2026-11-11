package com.pennywiseai.tracker.core

import com.pennywiseai.tracker.presentation.add.QuickAddActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickAddIntentTest {

    @Test
    fun `class name matches the Quick Add activity`() {
        assertEquals(QuickAddActivity::class.java.name, QuickAddIntent.ACTIVITY_CLASS_NAME)
    }

    @Test
    fun `manifest declares the Quick Add activity`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("""<activity\s[^>]*android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .map { if (it.startsWith(".")) "com.pennywiseai.tracker$it" else it }
            .toList()
        assertTrue(
            "${QuickAddIntent.ACTIVITY_CLASS_NAME} is not declared in AndroidManifest.xml: $declared",
            QuickAddIntent.ACTIVITY_CLASS_NAME in declared,
        )
    }
}
