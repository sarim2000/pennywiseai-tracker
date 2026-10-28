package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seeded profiles show in the app's language only while they keep their seeded name. */
class ProfileDisplayNameTest {
    private fun profile(id: Long, name: String) = ProfileEntity(id = id, name = name, colorHex = "#000000")

    @Test
    fun `seeded profiles with their seeded names are translated`() {
        assertEquals(R.string.profile_default_personal, profile(ProfileEntity.PERSONAL_ID, "Personal").seededNameRes())
        assertEquals(R.string.profile_default_business, profile(ProfileEntity.BUSINESS_ID, "Business").seededNameRes())
    }

    @Test
    fun `a renamed seeded profile shows as typed`() {
        assertNull(profile(ProfileEntity.PERSONAL_ID, "Home").seededNameRes())
        assertNull(profile(ProfileEntity.BUSINESS_ID, "Personal").seededNameRes())
    }

    @Test
    fun `other profiles show as typed even with a seeded name`() {
        assertNull(profile(3, "Personal").seededNameRes())
        assertNull(profile(4, "Business").seededNameRes())
    }
}
