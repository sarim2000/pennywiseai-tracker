package com.pennywiseai.tracker.data.trip

import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TripExclusionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TripsTest {
    private val start = LocalDateTime.of(2026, 12, 1, 0, 0)
    private val end = LocalDateTime.of(2026, 12, 5, 23, 59)
    private val trip = TransactionGroupEntity(id = 7, name = "Synthetic trip", tripStart = start, tripEnd = end)

    private fun tx(
        type: TransactionType = TransactionType.EXPENSE,
        at: LocalDateTime = LocalDateTime.of(2026, 12, 2, 10, 0),
        hash: String = "h1",
        groupId: Long? = null,
        recurring: Boolean = false,
        loanId: Long? = null
    ) = TransactionEntity(
        amount = BigDecimal("10"), merchantName = "Synthetic shop", category = "Others",
        transactionType = type, dateTime = at, transactionHash = hash,
        groupId = groupId, isRecurring = recurring, loanId = loanId
    )

    @Test
    fun statusAtBoundaries() {
        assertNull(TransactionGroupEntity(name = "plain").tripStatus(start))
        assertEquals(TripStatus.SCHEDULED, trip.tripStatus(start.minusNanos(1)))
        assertEquals(TripStatus.RUNNING, trip.tripStatus(start))
        assertEquals(TripStatus.RUNNING, trip.tripStatus(end))
        assertEquals(TripStatus.ENDED, trip.tripStatus(end.plusNanos(1)))
        assertEquals(TripStatus.RUNNING, trip.copy(tripEnd = null).tripStatus(start.plusYears(5)))
    }

    @Test
    fun windowOfDatesCoversWholeDays() {
        val (s, e) = tripWindowOf(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5))
        assertEquals(start, s)
        assertTrue(e!!.isAfter(LocalDateTime.of(2026, 12, 5, 23, 59, 59)))
        assertNull(tripWindowOf(LocalDate.of(2026, 12, 1), null).second)
    }

    @Test
    fun editingNameOnlyKeepsStoppedTripTimestamps() {
        val stoppedAt = LocalDateTime.of(2026, 12, 3, 14, 30, 5)
        val stopped = trip.copy(tripStart = LocalDateTime.of(2026, 12, 1, 9, 15), tripEnd = stoppedAt)
        val (s, e) = tripWindowOf(stopped.tripStart!!.toLocalDate(), stoppedAt.toLocalDate(), stopped.tripStart, stopped.tripEnd)
        assertEquals(stopped.tripStart, s)
        assertEquals(stoppedAt, e)
        // changing the end date takes the new day's end
        val (_, moved) = tripWindowOf(stopped.tripStart!!.toLocalDate(), LocalDate.of(2026, 12, 6), stopped.tripStart, stopped.tripEnd)
        assertEquals(LocalDateTime.of(2026, 12, 6, 23, 59, 59, 999_999_999), moved)
        // "until I stop it" stays open
        assertNull(tripWindowOf(stopped.tripStart!!.toLocalDate(), null, stopped.tripStart, stopped.tripEnd).second)
    }

    @Test
    fun nextBoundaryIsTheNextStartOrJustPastAnEnd() {
        val other = TransactionGroupEntity(id = 8, name = "Later", tripStart = LocalDateTime.of(2026, 12, 20, 0, 0))
        assertEquals(start, nextTripBoundary(listOf(trip, other), start.minusDays(2)))
        assertEquals(end.plusNanos(1), nextTripBoundary(listOf(trip, other), start))
        assertEquals(other.tripStart, nextTripBoundary(listOf(trip, other), end.plusNanos(1)))
        assertNull(nextTripBoundary(listOf(trip), end.plusDays(1)))
        assertNull(nextTripBoundary(listOf(TransactionGroupEntity(name = "plain")), start))
    }

    @Test
    fun overlapRefusedAndNamed() {
        val other = TransactionGroupEntity(id = 8, name = "Other", tripStart = end, tripEnd = null)
        assertEquals("Other", findOverlappingTrip(trip, listOf(trip, other))?.name) // shares the end instant
        val later = other.copy(tripStart = end.plusSeconds(1))
        assertNull(findOverlappingTrip(trip, listOf(trip, later)))
        // an open-ended trip blocks everything after it starts
        val open = trip.copy(id = 9, tripEnd = null)
        assertTrue(open.overlaps(later))
        assertFalse(open.overlaps(TransactionGroupEntity(id = 10, name = "Before", tripStart = start.minusDays(3), tripEnd = start.minusDays(1))))
        assertNull(findOverlappingTrip(trip, listOf(TransactionGroupEntity(id = 11, name = "Plain"))))
    }

    @Test
    fun whichRowsJoin() {
        assertTrue(joinsTrip(tx(), trip, emptySet()))
        assertTrue(joinsTrip(tx(TransactionType.CREDIT), trip, emptySet()))
        listOf(TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.INVESTMENT).forEach {
            assertFalse(it.name, joinsTrip(tx(it), trip, emptySet()))
        }
        assertFalse("already grouped", joinsTrip(tx(groupId = 3), trip, emptySet()))
        assertFalse("excluded hash", joinsTrip(tx(), trip, setOf("h1")))
        assertFalse("before window", joinsTrip(tx(at = start.minusSeconds(1)), trip, emptySet()))
        assertFalse("after window", joinsTrip(tx(at = end.plusSeconds(1)), trip, emptySet()))
        assertTrue("window edges", joinsTrip(tx(at = start), trip, emptySet()) && joinsTrip(tx(at = end), trip, emptySet()))
        assertFalse("subscription / EMI", joinsTrip(tx(recurring = true), trip, emptySet()))
        assertFalse("loan row", joinsTrip(tx(loanId = 1), trip, emptySet()))
        assertTrue("open end", joinsTrip(tx(at = end.plusYears(1)), trip.copy(tripEnd = null), emptySet()))
    }

    @Test
    fun exclusionRecordedForTripsByHashOnly() {
        assertEquals(TripExclusionEntity(7, "h1"), tripExclusionFor(tx(), trip))
        assertNull("manual row has no hash", tripExclusionFor(tx(hash = ""), trip))
        assertNull("ordinary group", tripExclusionFor(tx(), TransactionGroupEntity(id = 1, name = "plain")))
        assertNull(tripExclusionFor(tx(), null))
    }

    @Test
    fun batchAutoJoinReadsTripOnceAndSkipsExcluded() = runBlocking {
        val dao = mockk<TransactionDao>()
        val now = LocalDateTime.of(2026, 12, 2, 12, 0)
        coEvery { dao.getRunningTrip(now) } returns trip
        coEvery { dao.getTripExclusions(7) } returns listOf("h2")

        val out = dao.withTripGroup(listOf(tx(hash = "h1"), tx(hash = "h2"), tx(TransactionType.INCOME, hash = "h3")), now)

        assertEquals(listOf(7L, null, null), out.map { it.groupId })
        coVerify(exactly = 1) { dao.getRunningTrip(now) }
        coVerify(exactly = 1) { dao.getTripExclusions(7) }
    }

    @Test
    fun noRunningTripLeavesRowsAlone() = runBlocking {
        val dao = mockk<TransactionDao>()
        val now = LocalDateTime.of(2026, 12, 2, 12, 0)
        coEvery { dao.getRunningTrip(now) } returns null
        val rows = listOf(tx())
        assertEquals(rows, dao.withTripGroup(rows, now))
        coVerify(exactly = 0) { dao.getTripExclusions(any()) }
    }

    @Test
    fun rebuildNeverJoinsButANormalScanDoes() {
        assertFalse(joinsTripOnScan(LocalDateTime.of(2026, 12, 2, 9, 0)))
        assertTrue(joinsTripOnScan(null))
    }

    @Test
    fun newTripStartsAfterATripStoppedEarlierToday() {
        val now = LocalDateTime.of(2026, 12, 2, 18, 0)
        val stoppedAt = LocalDateTime.of(2026, 12, 2, 14, 30)
        val stopped = TransactionGroupEntity(id = 1, name = "Stopped", tripStart = LocalDateTime.of(2026, 12, 1, 0, 0), tripEnd = stoppedAt)
        val fresh = TransactionGroupEntity(id = 2, name = "New", tripStart = LocalDateTime.of(2026, 12, 2, 0, 0), tripEnd = null)

        val adjusted = startAfterEndedTrips(fresh, listOf(stopped), now)

        assertEquals(stoppedAt.plusNanos(1), adjusted.tripStart)
        assertNull(findOverlappingTrip(adjusted, listOf(stopped)))
    }

    @Test
    fun runningFutureOrMiddleTripsStillRefuse() {
        val now = LocalDateTime.of(2026, 12, 2, 18, 0)
        val fresh = TransactionGroupEntity(id = 2, name = "New", tripStart = LocalDateTime.of(2026, 12, 2, 0, 0), tripEnd = LocalDateTime.of(2026, 12, 9, 23, 59))
        val running = TransactionGroupEntity(id = 1, name = "Running", tripStart = LocalDateTime.of(2026, 12, 1, 0, 0), tripEnd = null)
        val future = TransactionGroupEntity(id = 3, name = "Future", tripStart = LocalDateTime.of(2026, 12, 5, 0, 0), tripEnd = null)
        val middle = TransactionGroupEntity(id = 4, name = "Middle", tripStart = LocalDateTime.of(2026, 12, 3, 0, 0), tripEnd = LocalDateTime.of(2026, 12, 4, 0, 0))
        val ended = TransactionGroupEntity(id = 5, name = "Ended", tripStart = LocalDateTime.of(2026, 12, 1, 0, 0), tripEnd = LocalDateTime.of(2026, 12, 2, 14, 0))

        listOf(running, future, middle).forEach {
            assertEquals(it.name, fresh, startAfterEndedTrips(fresh, listOf(it), now))
        }
        // a head conflict doesn't excuse a second, middle one
        assertEquals(fresh, startAfterEndedTrips(fresh, listOf(ended, middle), now))
        // and the new trip must still have room after the stop
        val tiny = fresh.copy(tripEnd = LocalDateTime.of(2026, 12, 2, 10, 0))
        assertEquals(tiny, startAfterEndedTrips(tiny, listOf(ended), now))
    }

    @Test
    fun mergedTripOverlappingAKeptOneBecomesAnOrdinaryGroup() {
        val kept = TransactionGroupEntity(id = 1, name = "Kept", tripStart = start, tripEnd = null)
        val imported = TransactionGroupEntity(id = 2, name = "Imported", tripStart = start, tripEnd = null)
        val demoted = demoteIfOverlapping(imported, listOf(kept))
        assertNull(demoted.tripStart)
        assertNull(demoted.tripEnd)
        assertEquals("Imported", demoted.name)
        val apart = imported.copy(tripStart = end.plusDays(5))
        assertEquals(apart, demoteIfOverlapping(apart, listOf(kept.copy(tripEnd = end))))
    }

    @Test
    fun freeUsersCannotMoveTripDatesButCanRenameOrSwitchOff() {
        assertFalse(tripEditNeedsPro(trip, true, start, end)) // same window: name/note edit
        assertTrue(tripEditNeedsPro(trip, true, start, end.plusDays(5)))
        assertTrue(tripEditNeedsPro(trip, true, start.plusDays(1), end))
        assertTrue("until I stop it again", tripEditNeedsPro(trip, true, start, null))
        assertFalse("switch off", tripEditNeedsPro(trip, false, null, null))
        assertTrue("new trip", tripEditNeedsPro(TransactionGroupEntity(name = "plain"), true, start, end))
    }

    @Test
    fun movingOutOfATripRecordsThatTripsExclusion() {
        assertEquals(TripExclusionEntity(7, "h1"), tripExclusionFor(tx(groupId = 7), trip))
    }
}
