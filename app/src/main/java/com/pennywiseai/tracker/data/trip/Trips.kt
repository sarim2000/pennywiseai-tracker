package com.pennywiseai.tracker.data.trip

import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TripExclusionEntity
import java.time.LocalDateTime

enum class TripStatus { SCHEDULED, RUNNING, ENDED }

val TransactionGroupEntity.isTrip: Boolean get() = tripStart != null

/** Null for an ordinary group. An end of null means "until I stop it". */
fun TransactionGroupEntity.tripStatus(now: LocalDateTime): TripStatus? {
    val start = tripStart ?: return null
    val end = tripEnd
    return when {
        now.isBefore(start) -> TripStatus.SCHEDULED
        end != null && now.isAfter(end) -> TripStatus.ENDED
        else -> TripStatus.RUNNING
    }
}

private fun TransactionGroupEntity.contains(at: LocalDateTime): Boolean {
    val start = tripStart ?: return false
    val end = tripEnd
    return !at.isBefore(start) && (end == null || !at.isAfter(end))
}

/** Windows are inclusive at both ends; an open end reaches forward forever. */
fun TransactionGroupEntity.overlaps(other: TransactionGroupEntity): Boolean {
    val aStart = tripStart ?: return false
    val bStart = other.tripStart ?: return false
    val aEnd = tripEnd
    val bEnd = other.tripEnd
    val aEndsBeforeB = aEnd != null && aEnd.isBefore(bStart)
    val bEndsBeforeA = bEnd != null && bEnd.isBefore(aStart)
    return !aEndsBeforeB && !bEndsBeforeA
}

/** The other trip whose window overlaps [candidate]'s, or null when it is free to save. */
fun findOverlappingTrip(candidate: TransactionGroupEntity, all: List<TransactionGroupEntity>): TransactionGroupEntity? =
    all.firstOrNull { it.id != candidate.id && candidate.overlaps(it) }

/**
 * Whether a newly saved row joins [trip]: spending only (expense or card spend),
 * inside the window, not already grouped, not a subscription/EMI (`isRecurring`
 * is set by the subscription match) or loan row, and not excluded by hash.
 */
fun joinsTrip(tx: TransactionEntity, trip: TransactionGroupEntity, excludedHashes: Set<String>): Boolean =
    tx.groupId == null &&
        tx.loanId == null &&
        !tx.isRecurring &&
        (tx.transactionType == TransactionType.EXPENSE || tx.transactionType == TransactionType.CREDIT) &&
        trip.contains(tx.dateTime) &&
        tx.transactionHash !in excludedHashes

/**
 * Puts each of [transactions] into the running trip when it qualifies. One cheap
 * read for the batch (two when something qualifies); no writes, no DataStore, so
 * it is safe inside a Room transaction. Restores never call this.
 */
suspend fun TransactionDao.withTripGroup(
    transactions: List<TransactionEntity>,
    now: LocalDateTime = LocalDateTime.now()
): List<TransactionEntity> {
    if (transactions.isEmpty()) return transactions
    val trip = getRunningTrip(now) ?: return transactions
    if (transactions.none { joinsTrip(it, trip, emptySet()) }) return transactions
    val excluded = getTripExclusions(trip.id).toSet()
    return transactions.map { if (joinsTrip(it, trip, excluded)) it.copy(groupId = trip.id) else it }
}

/**
 * A trip picked by dates covers whole days: from the start of [start] to the last instant of [end].
 * A date the user didn't change keeps its stored timestamp ([storedStart] / [storedEnd]) exactly,
 * so renaming a trip stopped at 14:30 doesn't widen it back to end of day and restart it.
 */
fun tripWindowOf(
    start: java.time.LocalDate,
    end: java.time.LocalDate?,
    storedStart: LocalDateTime? = null,
    storedEnd: LocalDateTime? = null
): Pair<LocalDateTime, LocalDateTime?> {
    val s = storedStart?.takeIf { it.toLocalDate() == start } ?: start.atStartOfDay()
    val e = end?.let { d -> storedEnd?.takeIf { it.toLocalDate() == d } ?: d.atTime(java.time.LocalTime.MAX) }
    return s to e
}

/** Saving a trip whose window overlaps [other]'s, named so the message can say which. */
class TripOverlapException(val other: String) : Exception("Trip overlaps $other")

/**
 * The next instant after [now] at which a trip's status flips (its start, or just past its end),
 * across [groups]; null when nothing will change.
 */
fun nextTripBoundary(groups: List<TransactionGroupEntity>, now: LocalDateTime): LocalDateTime? =
    groups.flatMap { g -> listOfNotNull(g.tripStart, g.tripEnd?.plusNanos(1)) }
        .filter { it.isAfter(now) }
        .minOrNull()

/** What removing [tx] from [group] must remember: its hash, for a trip, when it has one (a manual row has none). */
fun tripExclusionFor(tx: TransactionEntity, group: TransactionGroupEntity?): TripExclusionEntity? =
    if (group != null && group.isTrip && tx.transactionHash.isNotBlank()) TripExclusionEntity(group.id, tx.transactionHash) else null

/** A rebuild re-creates rows that were deliberately outside any trip; only a normal scan may join one. */
fun joinsTripOnScan(rebuildStartedAt: LocalDateTime?): Boolean = rebuildStartedAt == null

/**
 * Lets a new trip start right after a trip stopped earlier (today): when every conflict of
 * [candidate] is a trip already ended before [now] that covers the head of its window, the
 * candidate starts the instant after the latest such end. Anything else (a running or future
 * trip, an ended trip inside the window's middle, or no room left) comes back unchanged and
 * still overlaps, so the caller refuses it.
 */
fun startAfterEndedTrips(candidate: TransactionGroupEntity, others: List<TransactionGroupEntity>, now: LocalDateTime): TransactionGroupEntity {
    val start = candidate.tripStart ?: return candidate
    val conflicts = others.filter { it.id != candidate.id && candidate.overlaps(it) }
    if (conflicts.isEmpty()) return candidate
    val ends = conflicts.map { other ->
        val end = other.tripEnd
        val otherStart = other.tripStart
        if (end == null || !end.isBefore(now) || otherStart == null || otherStart.isAfter(start)) return candidate
        end
    }
    val newStart = ends.max().plusNanos(1)
    val candidateEnd = candidate.tripEnd
    if (candidateEnd != null && newStart.isAfter(candidateEnd)) return candidate
    return candidate.copy(tripStart = newStart)
}

/** In a MERGE restore an imported trip that overlaps one already kept becomes an ordinary group. */
fun demoteIfOverlapping(imported: TransactionGroupEntity, keptTrips: List<TransactionGroupEntity>): TransactionGroupEntity =
    if (findOverlappingTrip(imported, keptTrips) != null) imported.copy(tripStart = null, tripEnd = null) else imported

/**
 * Without Pro a trip's dates can't be set or changed: starting one, moving its start or end, or
 * re-enabling "until I stop it". Name and note edits, Stop and switching the trip off stay free.
 */
fun tripEditNeedsPro(stored: TransactionGroupEntity, enabled: Boolean, newStart: LocalDateTime?, newEnd: LocalDateTime?): Boolean =
    enabled && (stored.tripStart == null || newStart != stored.tripStart || newEnd != stored.tripEnd)
