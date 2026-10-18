package com.pennywiseai.tracker.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavItemTest {

    private val allRoutes = BottomNavItem.all.map { it.route }.toSet()

    @Test
    fun `nothing hidden by default shows every tab in order`() {
        assertEquals(
            listOf(BottomNavItem.Home, BottomNavItem.Transactions, BottomNavItem.Analytics, BottomNavItem.Chat),
            BottomNavItem.visible(emptySet())
        )
    }

    @Test
    fun `home stays even when every route is hidden`() {
        assertEquals(listOf(BottomNavItem.Home), BottomNavItem.visible(allRoutes))
    }

    @Test
    fun `hidden tabs drop out and the rest keep their order`() {
        assertEquals(
            listOf(BottomNavItem.Home, BottomNavItem.Analytics),
            BottomNavItem.visible(setOf("transactions", "chat"))
        )
    }

    @Test
    fun `home is not offered as hideable`() {
        assertFalse(BottomNavItem.Home in BottomNavItem.hideable)
        assertEquals(BottomNavItem.all.size - 1, BottomNavItem.hideable.size)
    }

    @Test
    fun `being on a tab that was just hidden falls back to home`() {
        assertEquals("home", BottomNavItem.fallbackFor("chat", setOf("chat")))
        assertEquals("home", BottomNavItem.fallbackFor("transactions", setOf("transactions", "chat")))
    }

    @Test
    fun `visible tabs and home stay put`() {
        assertNull(BottomNavItem.fallbackFor("analytics", setOf("chat")))
        assertNull(BottomNavItem.fallbackFor("home", allRoutes))
    }
}
