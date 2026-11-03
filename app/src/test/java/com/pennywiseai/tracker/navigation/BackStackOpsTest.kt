package com.pennywiseai.tracker.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The navigation-compose behaviours the Navigation 3 back stack helpers stand in for. */
class BackStackOpsTest {

    private fun stack(vararg keys: NavKey) = NavBackStack(*keys)

    @Test
    fun `navigate is single top - same screen no-op, same kind replaced, else pushed`() {
        val s = stack(Home, TransactionDetail(1))
        s.navigate(TransactionDetail(1))
        assertEquals(listOf(Home, TransactionDetail(1)), s.toList())
        s.navigate(TransactionDetail(2))
        assertEquals(listOf(Home, TransactionDetail(2)), s.toList())
        s.navigate(LoanDetail(3))
        assertEquals(listOf(Home, TransactionDetail(2), LoanDetail(3)), s.toList())
    }

    @Test
    fun `pop only pops the screen asking while it is on top, never the last one`() {
        val s = stack(Home, Settings, Categories)
        s.pop(Settings)
        assertEquals(3, s.size)
        s.pop(Categories)
        s.pop(Categories) // double tap
        assertEquals(listOf(Home, Settings), s.toList())
        s.pop(Settings)
        s.pop(Home)
        assertEquals(listOf(Home), s.toList())
    }

    @Test
    fun `replaceLast pops up to the last match inclusive and puts the key there`() {
        val s = stack(Home, GoalDetail(1), GoalDetail(2), GoalEdit(2))
        s.replaceLast(GoalDetail(3)) { it is GoalDetail }
        assertEquals(listOf(Home, GoalDetail(1), GoalDetail(3)), s.toList())

        val onboarding = stack(OnBoarding)
        onboarding.replaceLast(Home) { it == OnBoarding }
        onboarding.replaceLast(Home) { it == OnBoarding } // repeat: Home already on top
        assertEquals(listOf<NavKey>(Home), onboarding.toList())
    }
}
