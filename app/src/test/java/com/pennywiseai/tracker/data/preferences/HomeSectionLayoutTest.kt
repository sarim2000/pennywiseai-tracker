package com.pennywiseai.tracker.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionLayoutTest {

    @Test
    fun `round trips a reordered layout with a hidden flag`() {
        val layout = listOf(
            HomeSection.ACTIVITY to true,
            HomeSection.SAFE_TO_SPEND to false,
            HomeSection.SHORTCUTS to true,
            HomeSection.GROUPS to false,
            HomeSection.BUDGETS to true,
            HomeSection.UNSORTED to false,
            HomeSection.GOALS to false,
            HomeSection.LOANS to true,
            HomeSection.RECENT_TRANSACTIONS to false,
            HomeSection.ACCOUNTS to true,
            HomeSection.SUBSCRIPTIONS to true,
            HomeSection.MONEY_IN_MOTION to false,
        )
        val encoded = HomeSectionLayout.encode(layout)
        assertEquals("ACTIVITY,!SAFE_TO_SPEND,SHORTCUTS,!GROUPS,BUDGETS,!UNSORTED,!GOALS,LOANS,!RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS,!MONEY_IN_MOTION", encoded)
        assertEquals(layout, HomeSectionLayout.decode(encoded))
    }

    @Test
    fun `unknown names are dropped`() {
        val decoded = HomeSectionLayout.decode("BUDGETS,SAFE_TO_SPEND,WEATHER,!ACTIVITY")
        assertEquals(HomeSection.BUDGETS to true, decoded[0])
        assertEquals(HomeSection.ACTIVITY to false, decoded.single { it.first == HomeSection.ACTIVITY })
        assertEquals(HomeSection.entries.size, decoded.size)
    }

    @Test
    fun `missing sections are added visible at their default position`() {
        val decoded = HomeSectionLayout.decode("!ACTIVITY,ACCOUNTS")
        assertEquals(
            listOf(
                HomeSection.SAFE_TO_SPEND, HomeSection.SHORTCUTS, HomeSection.UNSORTED, HomeSection.RECENT_TRANSACTIONS,
                HomeSection.SUBSCRIPTIONS, HomeSection.BUDGETS, HomeSection.GOALS, HomeSection.LOANS, HomeSection.GROUPS,
                HomeSection.MONEY_IN_MOTION,
            ).map { it to true }.let { it.take(7) + listOf(HomeSection.ACTIVITY to false) + it.drop(7) } +
                listOf(HomeSection.ACCOUNTS to true),
            decoded,
        )
    }

    @Test
    fun `money in motion joins a saved layout late, not above the user's sections`() {
        // A layout saved before Money in motion was a section (it used to be a fixed card on top).
        val saved = "SAFE_TO_SPEND,BUDGETS,GOALS,LOANS,GROUPS,RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS,ACTIVITY"
        val decoded = HomeSectionLayout.decode(saved).map { it.first.name }
        assertEquals(saved.split(','), decoded - "MONEY_IN_MOTION" - "UNSORTED" - "SHORTCUTS")
        assertEquals(10, decoded.indexOf("MONEY_IN_MOTION"))
    }

    @Test
    fun `a section added in an update lands where a new user sees it`() {
        // A layout saved before Safe to spend existed: user moved Activity up, hid Groups.
        val decoded = HomeSectionLayout.decode(
            "ACTIVITY,BUDGETS,GOALS,LOANS,!GROUPS,RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS"
        )
        assertEquals(HomeSection.SAFE_TO_SPEND to true, decoded.first())
        // Shortcuts, added later still, land right under the hero.
        assertEquals(HomeSection.SHORTCUTS to true, decoded[1])
        assertEquals(
            listOf("ACTIVITY", "BUDGETS", "GOALS", "LOANS", "!GROUPS", "RECENT_TRANSACTIONS", "ACCOUNTS", "SUBSCRIPTIONS"),
            decoded.drop(2).filter { it.first != HomeSection.MONEY_IN_MOTION && it.first != HomeSection.UNSORTED }.map { (s, v) -> (if (v) "" else "!") + s.name },
        )
    }

    @Test
    fun `a layout never reordered moves to the new default order and keeps what's hidden`() {
        val old = "SAFE_TO_SPEND,BUDGETS,UNSORTED,RECENT_TRANSACTIONS,ACCOUNTS,!SUBSCRIPTIONS,LOANS,GOALS,GROUPS,MONEY_IN_MOTION,!ACTIVITY"
        assertEquals(
            "SAFE_TO_SPEND,SHORTCUTS,UNSORTED,RECENT_TRANSACTIONS,!SUBSCRIPTIONS,BUDGETS,GOALS,ACCOUNTS,LOANS,GROUPS,MONEY_IN_MOTION,!ACTIVITY",
            HomeSectionLayout.encode(HomeSectionLayout.decode(old)),
        )
        // An older default, saved before some sections existed, too.
        assertEquals(HomeSection.entries.toList(), HomeSectionLayout.decode("SAFE_TO_SPEND,BUDGETS,RECENT_TRANSACTIONS").map { it.first })
    }

    @Test
    fun `a reordered layout keeps its order`() {
        val custom = "BUDGETS,SAFE_TO_SPEND,SHORTCUTS,UNSORTED,RECENT_TRANSACTIONS,ACCOUNTS,SUBSCRIPTIONS,LOANS,GOALS,GROUPS,MONEY_IN_MOTION,ACTIVITY"
        assertEquals(custom, HomeSectionLayout.encode(HomeSectionLayout.decode(custom)))
    }

    @Test
    fun `blank or absent pref yields the default layout`() {
        assertEquals(HomeSectionLayout.DEFAULT, HomeSectionLayout.decode(null))
        assertEquals(HomeSectionLayout.DEFAULT, HomeSectionLayout.decode("  "))
        assertEquals(HomeSection.entries.map { it to true }, HomeSectionLayout.DEFAULT)
    }

    private val threeSections = listOf(
        HomeSection.SAFE_TO_SPEND to true,
        HomeSection.BUDGETS to false,
        HomeSection.ACTIVITY to true,
    )

    @Test
    fun `moving a section swaps it with its neighbour`() {
        assertEquals(
            listOf(HomeSection.BUDGETS to false, HomeSection.SAFE_TO_SPEND to true, HomeSection.ACTIVITY to true),
            HomeSectionLayout.move(threeSections, HomeSection.BUDGETS, -1),
        )
        assertEquals(
            listOf(HomeSection.SAFE_TO_SPEND to true, HomeSection.ACTIVITY to true, HomeSection.BUDGETS to false),
            HomeSectionLayout.move(threeSections, HomeSection.BUDGETS, 1),
        )
    }

    @Test
    fun `reordering applies the dragged order and keeps each section's visibility`() {
        assertEquals(
            listOf(HomeSection.ACTIVITY to true, HomeSection.SAFE_TO_SPEND to true, HomeSection.BUDGETS to false),
            HomeSectionLayout.reorder(threeSections, listOf(HomeSection.ACTIVITY, HomeSection.SAFE_TO_SPEND, HomeSection.BUDGETS)),
        )
        // Sections the order doesn't mention stay at the end in their old order.
        assertEquals(
            listOf(HomeSection.BUDGETS to false, HomeSection.SAFE_TO_SPEND to true, HomeSection.ACTIVITY to true),
            HomeSectionLayout.reorder(threeSections, listOf(HomeSection.BUDGETS)),
        )
    }

    @Test
    fun `moving past either end or moving a missing section changes nothing`() {
        assertEquals(threeSections, HomeSectionLayout.move(threeSections, HomeSection.SAFE_TO_SPEND, -1))
        assertEquals(threeSections, HomeSectionLayout.move(threeSections, HomeSection.ACTIVITY, 1))
        assertEquals(threeSections, HomeSectionLayout.move(threeSections, HomeSection.GROUPS, 1))
    }

    @Test
    fun `hiding or showing a section keeps its place`() {
        assertEquals(
            listOf(HomeSection.SAFE_TO_SPEND to false, HomeSection.BUDGETS to false, HomeSection.ACTIVITY to true),
            HomeSectionLayout.withVisibility(threeSections, HomeSection.SAFE_TO_SPEND, false),
        )
        assertEquals(
            listOf(HomeSection.SAFE_TO_SPEND to true, HomeSection.BUDGETS to true, HomeSection.ACTIVITY to true),
            HomeSectionLayout.withVisibility(threeSections, HomeSection.BUDGETS, true),
        )
    }

    // Fresh-install default (Activity off) vs. every stored / upgraded layout.

    @Test
    fun `fresh install with no stored layout seeds Activity hidden`() {
        val seeded = HomeSectionLayout.seedForFreshInstall(null)
        val decoded = HomeSectionLayout.decode(seeded)
        assertEquals(HomeSection.ACTIVITY to false, decoded.single { it.first == HomeSection.ACTIVITY })
        assertEquals(HomeSection.entries.toList(), decoded.map { it.first })
        assertEquals(HomeSection.entries.size - 1, decoded.count { it.second })
    }

    @Test
    fun `absent value on an upgrade still decodes to all visible`() {
        assertEquals(HomeSection.ACTIVITY to true, HomeSectionLayout.decode(null).single { it.first == HomeSection.ACTIVITY })
    }

    @Test
    fun `a stored layout is never reseeded`() {
        listOf("", "ACTIVITY", "!ACTIVITY", "BUDGETS,!UNSORTED").forEach {
            assertEquals(it, null, HomeSectionLayout.seedForFreshInstall(it))
        }
    }

    @Test
    fun `explicit Activity stays visible and hidden Activity stays hidden`() {
        assertEquals(HomeSection.ACTIVITY to true, HomeSectionLayout.decode("ACTIVITY").single { it.first == HomeSection.ACTIVITY })
        assertEquals(HomeSection.ACTIVITY to false, HomeSectionLayout.decode("!ACTIVITY").single { it.first == HomeSection.ACTIVITY })
    }

    @Test
    fun `legacy layout missing Activity gets it back visible`() {
        val decoded = HomeSectionLayout.decode("SAFE_TO_SPEND,BUDGETS,RECENT_TRANSACTIONS")
        assertEquals(HomeSection.ACTIVITY to true, decoded.single { it.first == HomeSection.ACTIVITY })
    }

    @Test
    fun `reordered and hidden Unsorted keeps its place and state`() {
        // Sections missing from the saved string slot in around these; the saved three keep their order.
        val saved = setOf(HomeSection.RECENT_TRANSACTIONS, HomeSection.UNSORTED, HomeSection.BUDGETS)
        val decoded = HomeSectionLayout.decode("RECENT_TRANSACTIONS,!UNSORTED,BUDGETS").filter { it.first in saved }
        assertEquals(listOf(HomeSection.RECENT_TRANSACTIONS to true, HomeSection.UNSORTED to false, HomeSection.BUDGETS to true), decoded)
        val full = HomeSection.entries.filter { it != HomeSection.UNSORTED } + HomeSection.UNSORTED
        val moved = HomeSectionLayout.decode(full.joinToString(",") { it.name })
        assertEquals(HomeSection.UNSORTED to true, moved.last())
    }
}
