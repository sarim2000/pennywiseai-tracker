package com.pennywiseai.tracker.data.trip

import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** An open-ended trip is asked "still on it?" this many days after it started, and again after each "Keep going". */
const val TRIP_NUDGE_DAYS = 14L

/**
 * What the app remembers about one trip's nudge, on this device only (never in a backup).
 * [anchor] is where the 14 days count from: the trip's start, or the last "Keep going".
 * [posted] means the question is out and waiting: nothing repeats it until the user answers.
 * [tripStart] ties the record to one run of the trip, so a restarted trip begins afresh.
 */
data class TripNudgeState(val tripStart: LocalDateTime, val anchor: LocalDateTime, val posted: Boolean) {
    fun encode(): String = "$tripStart|$anchor|${if (posted) "P" else "W"}"

    companion object {
        fun decode(raw: String?): TripNudgeState? {
            val parts = raw?.split('|') ?: return null
            if (parts.size != 3) return null
            return runCatching { TripNudgeState(LocalDateTime.parse(parts[0]), LocalDateTime.parse(parts[1]), parts[2] == "P") }.getOrNull()
        }
    }
}

/** A trip that began, has no end date and hasn't been stopped: the only kind that is ever nudged. */
fun TransactionGroupEntity.isOpenEndedRunning(now: LocalDateTime): Boolean =
    tripEnd == null && tripStart?.let { !it.isAfter(now) } == true

/** The record that applies to [trip]'s current run, or null (none, or left by an earlier run). */
private fun TransactionGroupEntity.currentNudge(state: TripNudgeState?): TripNudgeState? = state?.takeIf { it.tripStart == tripStart }

/** True when [trip] should be asked now: open-ended, running, 14 days since its anchor, and not already asked. */
fun tripNudgeDue(trip: TransactionGroupEntity, now: LocalDateTime, state: TripNudgeState?): Boolean {
    val start = trip.tripStart ?: return false
    if (!trip.isOpenEndedRunning(now)) return false
    val current = trip.currentNudge(state)
    if (current?.posted == true) return false
    val anchor = current?.anchor ?: start
    return !now.isBefore(anchor.plusDays(TRIP_NUDGE_DAYS))
}

/** The record to store when the nudge is posted; null when it isn't due. */
fun claimedNudge(trip: TransactionGroupEntity, now: LocalDateTime, state: TripNudgeState?): TripNudgeState? {
    if (!tripNudgeDue(trip, now, state)) return null
    val start = trip.tripStart ?: return null
    return TripNudgeState(start, trip.currentNudge(state)?.anchor ?: start, posted = true)
}

/** The record for "Keep going": asks again 14 days from [now]. Null when the trip is no longer open-ended and running. */
fun keepGoingNudge(trip: TransactionGroupEntity, now: LocalDateTime): TripNudgeState? {
    val start = trip.tripStart ?: return null
    return if (trip.isOpenEndedRunning(now)) TripNudgeState(start, now, posted = false) else null
}

/** Whole days the trip has run, for the notification's text. */
fun tripRunningDays(trip: TransactionGroupEntity, now: LocalDateTime): Int {
    val start = trip.tripStart ?: return 0
    return ChronoUnit.DAYS.between(start, now).coerceAtLeast(0).toInt()
}

/** What the nudge machinery does to a trip's pending question when the trip stops being open-ended. */
interface TripNudges {
    /** Forgets the trip's nudge record and takes its notification down. Call after the write, never inside a Room transaction. */
    suspend fun clear(tripId: Long)
}
