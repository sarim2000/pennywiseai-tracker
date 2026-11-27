package com.pennywiseai.tracker.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** A photo import applies only while the avatar choice it started under is still current. */
class PhotoImportSelectionTest {
    @Test
    fun `a finished import applies while its selection is still current`() {
        val waiting = PhotoImportDraft(imageUri = "avatar://1", importPending = true, selectionToken = 4)
        assertEquals(
            PhotoImportDraft(imageUri = "file:///photo.jpg", importPending = false, selectionToken = 4),
            applyPhotoImport(waiting, startedToken = 4, importedUri = "file:///photo.jpg"),
        )
    }

    @Test
    fun `a failed import clears the wait and keeps the picture`() {
        val waiting = PhotoImportDraft(imageUri = "avatar://1", importPending = true, selectionToken = 4)
        assertEquals(
            waiting.copy(importPending = false),
            applyPhotoImport(waiting, startedToken = 4, importedUri = null),
        )
    }

    @Test
    fun `a preset chosen while the import runs keeps the preset`() {
        val preset = PhotoImportDraft(imageUri = "avatar://2", importPending = false, selectionToken = 5)
        assertEquals(preset, applyPhotoImport(preset, startedToken = 4, importedUri = "file:///late.jpg"))
    }

    @Test
    fun `a newer photo still importing is left pending`() {
        val newer = PhotoImportDraft(imageUri = "avatar://1", importPending = true, selectionToken = 6)
        assertEquals(newer, applyPhotoImport(newer, startedToken = 4, importedUri = "file:///old.jpg"))
    }
}
