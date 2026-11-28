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
}
