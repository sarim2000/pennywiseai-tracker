package com.pennywiseai.tracker.domain.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** Which 25 / 50 / 75% milestone an Add just crossed. */
class GoalMilestonesTest {

    private fun crossed(before: String, after: String, target: String = "1000") =
        crossedMilestone(BigDecimal(before), BigDecimal(after), BigDecimal(target))

    @Test
    fun crossingEachMilestoneReportsIt() {
        assertEquals(25, crossed("100", "300"))
        assertEquals(50, crossed("300", "600"))
        assertEquals(75, crossed("600", "800"))
    }

    @Test
    fun landingExactlyOnTheMarkCountsButStartingOnItDoesNot() {
        assertEquals(50, crossed("400", "500"))
        assertNull(crossed("500", "600"))
    }

    @Test
    fun oneAddPastSeveralReportsTheHighest() {
        assertEquals(75, crossed("0", "900"))
        assertEquals(50, crossed("240", "510"))
    }

    @Test
    fun staysInsideOneBandIsNone() {
        assertNull(crossed("0", "249.99"))
        assertNull(crossed("510", "740"))
        assertNull(crossed("760", "990"))
    }

    @Test
    fun reachingTheTargetIsLeftToReached() {
        assertNull(crossed("700", "1000"))
        assertNull(crossed("100", "1200"))
    }

    @Test
    fun goingDownOrAlreadyPastIsNone() {
        assertNull(crossed("600", "400"))
        assertNull(crossed("1100", "1200"))
    }

    @Test
    fun noTargetHasNoMilestones() {
        assertNull(crossed("0", "10", target = "0"))
    }

    @Test
    fun marksAreExactForTargetsThatDontSplitEvenly() {
        // 25% of 333.33 is 83.3325; a cent short isn't there yet.
        assertNull(crossed("0", "83.33", target = "333.33"))
        assertEquals(25, crossed("0", "83.34", target = "333.33"))
    }
}
