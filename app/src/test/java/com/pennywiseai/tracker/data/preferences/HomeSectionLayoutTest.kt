package com.pennywiseai.tracker.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionLayoutTest {

    @Test
    fun `round trips a reordered layout with a hidden flag`() {
        val layout = listOf(
            HomeSection.ACTIVITY to true,
            HomeSection.SAFE_TO_SPEND to false,
            HomeSection.GROUPS to false,
            HomeSection.BUDGETS to true,
            HomeSection.GOALS to false,
            HomeSection.LOANS to true,
            HomeSection.RECENT_TRANSACTIONS to false,
            HomeSection.ACCOUNTS to true,
            HomeSection.SUBSCRIPTIONS to true,
        )
        val encoded = HomeSectionLayout.encode(layout)
        assertEquals("ACTIVITY,!SAFE_TO_SPEND,!GROUPS,BUDGETS,!GOALS,LOANS,!RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS", encoded)
        assertEquals(layout, HomeSectionLayout.decode(encoded))
    }

    @Test
    fun `unknown names are dropped`() {
        val decoded = HomeSectionLayout.decode("SAFE_TO_SPEND,BUDGETS,WEATHER,!ACTIVITY")
        assertEquals(HomeSection.BUDGETS to true, decoded[1])
        assertEquals(HomeSection.ACTIVITY to false, decoded.single { it.first == HomeSection.ACTIVITY })
        assertEquals(HomeSection.entries.size, decoded.size)
    }

    @Test
    fun `missing sections are added visible at their default position`() {
        val decoded = HomeSectionLayout.decode("!ACTIVITY,ACCOUNTS")
        assertEquals(
            listOf(
                HomeSection.SAFE_TO_SPEND, HomeSection.BUDGETS, HomeSection.GOALS, HomeSection.LOANS,
                HomeSection.GROUPS, HomeSection.RECENT_TRANSACTIONS,
            ).map { it to true } +
                listOf(HomeSection.ACTIVITY to false, HomeSection.SUBSCRIPTIONS to true, HomeSection.ACCOUNTS to true),
            decoded,
        )
    }

    @Test
    fun `a section added in an update lands where a new user sees it`() {
        // A layout saved before Safe to spend existed: user moved Activity up, hid Groups.
        val decoded = HomeSectionLayout.decode(
            "ACTIVITY,BUDGETS,GOALS,LOANS,!GROUPS,RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS"
        )
        assertEquals(HomeSection.SAFE_TO_SPEND to true, decoded.first())
        assertEquals(
            listOf("ACTIVITY", "BUDGETS", "GOALS", "LOANS", "!GROUPS", "RECENT_TRANSACTIONS", "ACCOUNTS", "SUBSCRIPTIONS"),
            decoded.drop(1).map { (s, v) -> (if (v) "" else "!") + s.name },
        )
    }

    @Test
    fun `blank or absent pref yields the default layout`() {
        assertEquals(HomeSectionLayout.DEFAULT, HomeSectionLayout.decode(null))
        assertEquals(HomeSectionLayout.DEFAULT, HomeSectionLayout.decode("  "))
        assertEquals(HomeSection.entries.map { it to true }, HomeSectionLayout.DEFAULT)
    }
}
