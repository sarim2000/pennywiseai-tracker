package com.pennywiseai.tracker.data.trip

import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TripNudgeTest {
    private val start = LocalDateTime.of(2026, 12, 1, 9, 0)
    private val open = TransactionGroupEntity(id = 4, name = "Synthetic trip", tripStart = start, tripEnd = null)

    @Test
    fun dueExactlyFourteenDaysIn_notASecondBefore() {
        assertFalse(tripNudgeDue(open, start.plusDays(14).minusSeconds(1), null))
        assertTrue(tripNudgeDue(open, start.plusDays(14), null))
        assertTrue(tripNudgeDue(open, start.plusDays(40), null))
    }

    @Test
    fun postedOnceStaysQuietUntilAnswered() {
        val now = start.plusDays(14)
        val claimed = claimedNudge(open, now, null)!!
        assertTrue(claimed.posted)
        assertFalse(tripNudgeDue(open, now.plusDays(30), claimed))
        assertNull(claimedNudge(open, now.plusDays(30), claimed))
    }

    @Test
    fun keepGoingAsksAgainFourteenDaysLater() {
        val answered = start.plusDays(15)
        val kept = keepGoingNudge(open, answered)!!
        assertFalse(kept.posted)
        assertFalse(tripNudgeDue(open, answered.plusDays(14).minusSeconds(1), kept))
        assertTrue(tripNudgeDue(open, answered.plusDays(14), kept))
        // and the second question is posted once too
        val second = claimedNudge(open, answered.plusDays(14), kept)!!
        assertFalse(tripNudgeDue(open, answered.plusDays(60), second))
    }

    @Test
    fun aStoppedTripIsNeverDueAndCannotBeKeptGoing() {
        val stopped = open.copy(tripEnd = start.plusDays(3))
        assertFalse(tripNudgeDue(stopped, start.plusDays(30), null))
        assertNull(claimedNudge(stopped, start.plusDays(30), null))
        assertNull(keepGoingNudge(stopped, start.plusDays(30)))
    }

    @Test
    fun aDatedTripNeverNudges_evenBeforeItsEndDate() {
        val dated = open.copy(tripEnd = start.plusDays(60))
        assertFalse(tripNudgeDue(dated, start.plusDays(20), null))
    }

    @Test
    fun aPlainGroupOrScheduledTripNeverNudges() {
        assertFalse(tripNudgeDue(TransactionGroupEntity(name = "Synthetic group"), start.plusDays(99), null))
        assertFalse(tripNudgeDue(open, start.minusDays(1), null))
    }

    @Test
    fun aRecordFromAnEarlierRunOfTheTripIsIgnored() {
        val restarted = open.copy(tripStart = start.plusDays(100))
        val stale = TripNudgeState(start, start.plusDays(14), posted = true)
        assertFalse(tripNudgeDue(restarted, start.plusDays(113), stale))
        assertTrue(tripNudgeDue(restarted, start.plusDays(114), stale))
    }

    @Test
    fun recordSurvivesEncoding_andJunkReadsAsNone() {
        val state = TripNudgeState(start, start.plusDays(3), posted = false)
        assertEquals(state, TripNudgeState.decode(state.encode()))
        assertNull(TripNudgeState.decode(null))
        assertNull(TripNudgeState.decode("garbage"))
        assertNull(TripNudgeState.decode("a|b|P"))
    }

    @Test
    fun runningDaysAreWholeDays() {
        assertEquals(14, tripRunningDays(open, start.plusDays(14).plusHours(5)))
        assertEquals(13, tripRunningDays(open, start.plusDays(14).minusMinutes(1)))
    }
}
