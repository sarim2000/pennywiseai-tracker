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

/**
 * During a rebuild only a message whose row the rebuild deleted stays out of a trip (it was out
 * deliberately); one the app never saved joins like any new message. Rows are paired the way the
 * rebuild pairs them, by (sender, body). [rebuilt] is empty outside a rebuild.
 */
fun joinsTripOnScan(smsSender: String?, smsBody: String?, rebuilt: Set<Pair<String, String>>): Boolean =
    smsSender == null || smsBody == null || (smsSender to smsBody) !in rebuilt

/** The outcome of saving a trip window: the trip to write, or the trip that blocks it. */
class TripSave(val trip: TransactionGroupEntity, val blocker: TransactionGroupEntity?)

/**
 * Plain rule: the window as picked; if it overlaps and starts today, retry once starting now (a
 * trip stopped earlier today, started again, any number of times a day). A past or future start
 * is never shifted. A refusal names the trip blocking the final check, a running or scheduled one
 * before an ended one.
 */
fun resolveTripSave(candidate: TransactionGroupEntity, others: List<TransactionGroupEntity>, now: LocalDateTime): TripSave {
    val start = candidate.tripStart ?: return TripSave(candidate, null)
    fun blockers(c: TransactionGroupEntity) = others.filter { it.id != c.id && c.overlaps(it) }
    val first = blockers(candidate)
    if (first.isEmpty()) return TripSave(candidate, null)
    val end = candidate.tripEnd
    if (start.toLocalDate() == now.toLocalDate() && start.isBefore(now) && (end == null || !now.isAfter(end))) {
        val retry = candidate.copy(tripStart = now)
        val second = blockers(retry)
        if (second.isEmpty()) return TripSave(retry, null)
        return TripSave(retry, second.firstOrNull { it.tripStatus(now) != TripStatus.ENDED } ?: second.first())
    }
    return TripSave(candidate, first.firstOrNull { it.tripStatus(now) != TripStatus.ENDED } ?: first.first())
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
