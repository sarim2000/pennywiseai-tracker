package com.pennywiseai.tracker.domain.challenges

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
enum class ChallengeKind {
    /** [ChallengeRun.target] days in the window with no expense at all. */
    NO_SPEND_DAYS,

    /** Every day of the window without an expense in [ChallengeRun.categoryName]. */
    CATEGORY_WEEK,
}

/**
 * One challenge as started. Its days are worked out from transactions each time, never
 * stored, so a late or re-categorised message still counts where it belongs.
 */
@Serializable
data class ChallengeRun(
    /** Set when the challenge starts, so a rename or a same-day restart is still this run. */
    val id: String = "",
    val kind: ChallengeKind = ChallengeKind.NO_SPEND_DAYS,
    /** The (top-level) category a [ChallengeKind.CATEGORY_WEEK] goes without, by its name when started. */
    val categoryName: String? = null,
    /** That category's id, so a rename doesn't lose the challenge's expenses; null for older runs. */
    val categoryId: Long? = null,
    /** The profile whose expenses count (null for all), as selected when started. */
    val profileId: Long? = null,
    val startEpochDay: Long = 0,
    val length: Int = WEEK,
    val target: Int = NO_SPEND_TARGET,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val end: LocalDate get() = start.plusDays(length - 1L)

    /** Whether [row] spoils its day. */
    fun counts(row: ChallengeRow): Boolean = kind == ChallengeKind.NO_SPEND_DAYS || categoryName in row.categories

    /** The same start, whatever its category is called now. */
    fun sameAs(other: ChallengeRun?): Boolean = other != null && id == other.id

    /** Once per run: the same challenge started again is a new moment. */
    val celebrationKey: String get() = "challenge:$id"

    companion object {
        const val WEEK = 7
        const val NO_SPEND_TARGET = 3

        fun start(
            kind: ChallengeKind,
            categoryName: String?,
            today: LocalDate,
            categoryId: Long? = null,
            profileId: Long? = null,
        ) = ChallengeRun(
            id = UUID.randomUUID().toString(),
            kind = kind,
            categoryName = categoryName.takeIf { kind == ChallengeKind.CATEGORY_WEEK },
            categoryId = categoryId.takeIf { kind == ChallengeKind.CATEGORY_WEEK },
            profileId = profileId,
            startEpochDay = today.toEpochDay(),
            length = WEEK,
            target = if (kind == ChallengeKind.NO_SPEND_DAYS) NO_SPEND_TARGET else WEEK,
        )
    }
}

/** A finished run. [seen] once the user has moved on from its result card. */
@Serializable
data class ChallengeOutcome(
    val run: ChallengeRun = ChallengeRun(),
    val cleanDays: Int = 0,
    val won: Boolean = false,
    val seen: Boolean = false,
)

/** What's kept on this device: the running challenge and the last few results, newest first. */
@Serializable
data class ChallengeState(
    val active: ChallengeRun? = null,
    val history: List<ChallengeOutcome> = emptyList(),
) {
    /** The latest result the user hasn't moved on from. */
    val unseenResult: ChallengeOutcome? get() = history.firstOrNull()?.takeUnless { it.seen }

    /** Moves [run] to history, if it's still the active one (two screens may both see it end). */
    fun finish(run: ChallengeRun, cleanDays: Int, won: Boolean): ChallengeState =
        if (active == null || !run.sameAs(active)) this
        else ChallengeState(null, (listOf(ChallengeOutcome(active, cleanDays, won)) + history).take(HISTORY))

    /** Give up [run] only if it's still the one running: a rollover may have finished it meanwhile. */
    fun giveUp(run: ChallengeRun): ChallengeState = if (run.sameAs(active)) copy(active = null) else this

    fun markSeen(): ChallengeState = copy(history = history.mapIndexed { i, o -> if (i == 0) o.copy(seen = true) else o })

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val HISTORY = 10
        private val json = Json { ignoreUnknownKeys = true }

        /** Empty for nothing stored, or for state this version can't read. */
        fun fromJson(raw: String?): ChallengeState =
            raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: ChallengeState()
    }
}

/** One counted expense: its day and its top-level categories (a split can carry several). */
data class ChallengeRow(
    val id: Long,
    val date: LocalDate,
    val categories: Set<String>,
    val merchant: String,
)

enum class DayStatus {
    CLEAN,
    MISSED,

    /**
     * Nothing counted yet, but not settled: today, the [GRACE_DAYS] after it (a bank
     * message can land late), or any day while messages can't be read.
     */
    PENDING,
    FUTURE,
}

data class ChallengeDay(val date: LocalDate, val status: DayStatus, val misses: List<ChallengeRow> = emptyList())

data class ChallengeProgress(
    val run: ChallengeRun,
    val days: List<ChallengeDay>,
    /** The day it was worked out on. */
    val on: LocalDate,
    /** Messages can't be read, so no day settles and nothing finishes. */
    val paused: Boolean = false,
) {
    /** 1-based day of the window [on] falls on; the last one while its grace day runs. */
    val dayNumber: Int get() = (ChronoUnit.DAYS.between(run.start, on) + 1).toInt().coerceIn(1, run.length)

    val cleanDays: Int get() = days.count { it.status == DayStatus.CLEAN }
    val won: Boolean get() = cleanDays >= run.target

    /** Every day has settled: the window and the grace after it are over. */
    val over: Boolean get() = !paused && on.minusDays(GRACE_DAYS).isAfter(run.end)

    /**
     * Won early, or every day settled. A loss waits for that even when it's certain, so an
     * expense corrected later (made a transfer) can still clear its day.
     */
    val finished: Boolean get() = won || over

    /** The target is still within reach. */
    val winnable: Boolean get() = cleanDays + days.count { it.status == DayStatus.PENDING || it.status == DayStatus.FUTURE } >= run.target

    /** Today, while it's inside the window. */
    val today: ChallengeDay? get() = days.firstOrNull { it.date == on }
}

/** Days a clean day waits after it ends before it counts, for bank messages that arrive late. */
const val GRACE_DAYS = 1L

/**
 * Each day of [this] run on [today]. A day with a counted expense is missed at once (more
 * messages can't undo it); a day without one is only clean once [GRACE_DAYS] have passed
 * after it, and only while [tracking] (messages are being read at all).
 */
fun ChallengeRun.progress(rows: List<ChallengeRow>, today: LocalDate, tracking: Boolean = true): ChallengeProgress {
    val misses = rows.filter(::counts).groupBy { it.date }
    val settledBefore = today.minusDays(GRACE_DAYS)
    val days = (0 until length).map { i ->
        val date = start.plusDays(i.toLong())
        val hits = misses[date].orEmpty()
        val status = when {
            date.isAfter(today) -> DayStatus.FUTURE
            hits.isNotEmpty() -> DayStatus.MISSED
            tracking && date.isBefore(settledBefore) -> DayStatus.CLEAN
            else -> DayStatus.PENDING
        }
        ChallengeDay(date, status, if (status == DayStatus.MISSED) hits else emptyList())
    }
    return ChallengeProgress(this, days, today, paused = !tracking)
}

const val FOOD_AND_DINING = "Food & Dining"
const val OTHERS = "Others"

/**
 * The categories worth a "week without", most expenses first (by count: no money is summed),
 * [food] (Food & Dining, by its current name) leading when the user has any. [others] says
 * nothing to go without.
 */
fun challengeCategories(
    rows: List<ChallengeRow>,
    food: String = FOOD_AND_DINING,
    others: Set<String> = setOf(OTHERS),
    limit: Int = 3,
): List<String> {
    val ranked = rows.flatMap { it.categories }.filter { it.isNotBlank() && it !in others }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    val ordered = if (food in ranked) listOf(food) + (ranked - food) else ranked
    return ordered.take(limit).ifEmpty { listOf(food) }
}
