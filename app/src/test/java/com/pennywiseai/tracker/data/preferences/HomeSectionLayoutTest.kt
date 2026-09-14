package com.pennywiseai.tracker.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionLayoutTest {

    @Test
    fun `round trips a reordered layout with a hidden flag`() {
        val layout = listOf(
            HomeSection.ACTIVITY to true,
            HomeSection.GROUPS to false,
            HomeSection.BUDGETS to true,
            HomeSection.LOANS to true,
            HomeSection.RECENT_TRANSACTIONS to false,
            HomeSection.ACCOUNTS to true,
            HomeSection.SUBSCRIPTIONS to true,
        )
        val encoded = HomeSectionLayout.encode(layout)
        assertEquals("ACTIVITY,!GROUPS,BUDGETS,LOANS,!RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS", encoded)
        assertEquals(layout, HomeSectionLayout.decode(encoded))
    }

    @Test
    fun `unknown names are dropped`() {
        val decoded = HomeSectionLayout.decode("BUDGETS,WEATHER,!ACTIVITY")
        assertEquals(HomeSection.BUDGETS to true, decoded[0])
        assertEquals(HomeSection.ACTIVITY to false, decoded[1])
        assertEquals(HomeSection.entries.size, decoded.size)
    }

    @Test
    fun `missing sections are appended visible in default order`() {
        val decoded = HomeSectionLayout.decode("!ACTIVITY,ACCOUNTS")
        assertEquals(
            listOf(HomeSection.ACTIVITY to false, HomeSection.ACCOUNTS to true) +
                listOf(
                    HomeSection.BUDGETS, HomeSection.LOANS, HomeSection.GROUPS,
                    HomeSection.RECENT_TRANSACTIONS, HomeSection.SUBSCRIPTIONS,
                ).map { it to true },
            decoded,
        )
    }

    @Test
    fun `blank or absent pref yields the default layout`() {
        assertEquals(HomeSectionLayout.DEFAULT, HomeSectionLayout.decode(null))
        assertEquals(HomeSectionLayout.DEFAULT, HomeSectionLayout.decode("  "))
        assertEquals(HomeSection.entries.map { it to true }, HomeSectionLayout.DEFAULT)
    }
}
