package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePhotosTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun files() = tmp.root.list()!!.sorted()

    private fun seed() = listOf("profile_image.jpg", "profile_photo_1.jpg", "profile_photo_2.jpg", "notes.txt")
        .forEach { tmp.newFile(it) }

    @Test
    fun `keeps the current photo and removes the others`() {
        seed()
        ProfilePhotos.sweep(tmp.root, "file:///data/files/profile_photo_2.jpg")
        assertEquals(listOf("notes.txt", "profile_photo_2.jpg"), files())
    }

    @Test
    fun `removes every photo once a preset avatar is picked`() {
        seed()
        ProfilePhotos.sweep(tmp.root, "avatar://3")
        assertEquals(listOf("notes.txt"), files())
    }

    @Test
    fun `a tall decode is scaled so both sides fit`() {
        assertEquals(21 to 512, ProfilePhotos.boundedSides(512, 12000, 512))
    }

    @Test
    fun `an ordinary portrait already inside the bound is unchanged`() {
        assertEquals(384 to 512, ProfilePhotos.boundedSides(384, 512, 512))
        assertEquals(400 to 300, ProfilePhotos.boundedSides(400, 300, 512))
    }

    @Test
    fun `a portrait slightly past the bound keeps its frame and fits`() {
        assertEquals(383 to 512, ProfilePhotos.boundedSides(512, 683, 512))
    }

    @Test
    fun `does nothing while the current picture is unknown`() {
        seed()
        ProfilePhotos.sweep(tmp.root, null)
        assertEquals(listOf("notes.txt", "profile_image.jpg", "profile_photo_1.jpg", "profile_photo_2.jpg"), files())
    }
}
