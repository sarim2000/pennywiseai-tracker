package com.pennywiseai.tracker.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShortcutLayoutTest {

    @Test
    fun `absent is the default row, empty is the user's empty row`() {
        assertEquals(HomeShortcutLayout.DEFAULT, HomeShortcutLayout.decode(null))
        assertEquals(emptyList<HomeShortcut>(), HomeShortcutLayout.decode(""))
    }

    @Test
    fun `round-trips in order, dropping unknown names and repeats, at most four`() {
        val picked = listOf(HomeShortcut.CALENDAR, HomeShortcut.UNSORTED, HomeShortcut.GOALS)
        assertEquals(picked, HomeShortcutLayout.decode(HomeShortcutLayout.encode(picked)))
        assertEquals(
            listOf(HomeShortcut.BILLS, HomeShortcut.SEARCH),
            HomeShortcutLayout.decode("BILLS, GONE,BILLS,SEARCH")
        )
        assertEquals(
            HomeShortcutLayout.MAX,
            HomeShortcutLayout.decode(HomeShortcut.entries.joinToString(",") { it.name }).size
        )
    }

    @Test
    fun `toggle adds at the end, removes, and never pushes one out of a full row`() {
        val three = listOf(HomeShortcut.UNSORTED, HomeShortcut.SEARCH, HomeShortcut.BILLS)
        assertEquals(three + HomeShortcut.GOALS, HomeShortcutLayout.toggle(three, HomeShortcut.GOALS))
        assertEquals(listOf(HomeShortcut.UNSORTED, HomeShortcut.BILLS), HomeShortcutLayout.toggle(three, HomeShortcut.SEARCH))
        val full = HomeShortcutLayout.DEFAULT
        assertEquals(full, HomeShortcutLayout.toggle(full, HomeShortcut.GOALS))
    }

    @Test
    fun `an upgrader's saved sections gain the shortcut row right under the hero`() {
        val decoded = HomeSectionLayout.decode("SAFE_TO_SPEND,BUDGETS,!GROUPS,ACTIVITY")
        assertEquals(HomeSection.SHORTCUTS to true, decoded[1])
    }
}
