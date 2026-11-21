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
    /** Set when the challenge starts. Older saved runs have none until the next load. */
    val id: String? = null,
    val kind: ChallengeKind = ChallengeKind.NO_SPEND_DAYS,
    /** The (top-level) category a [ChallengeKind.CATEGORY_WEEK] goes without, by its name when started. */
    val categoryName: String? = null,
    /** That category's id, so a rename doesn't lose the challenge's expenses; null for older runs. */
    val categoryId: Long? = null,
    val startEpochDay: Long = 0,
    val length: Int = WEEK,
    val target: Int = NO_SPEND_TARGET,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val end: LocalDate get() = start.plusDays(length - 1L)

    /** Whether [row] spoils its day. */
    fun counts(row: ChallengeRow): Boolean = kind == ChallengeKind.NO_SPEND_DAYS || categoryName in row.categories

    /**
     * The same start, whatever its category is called now.
     * A missing id matches nothing, so two runs from before ids existed are not the same one.
     */
    fun sameAs(other: ChallengeRun?): Boolean = other != null && id != null && id == other.id

    /** Once per run: the same challenge started another day is a new moment. */
    val celebrationKey: String get() = "challenge:$start:$kind"

    companion object {
        const val WEEK = 7
        const val NO_SPEND_TARGET = 3

        fun start(kind: ChallengeKind, categoryName: String?, today: LocalDate, categoryId: Long? = null) = ChallengeRun(
            id = newChallengeId(),
            kind = kind,
            categoryName = categoryName.takeIf { kind == ChallengeKind.CATEGORY_WEEK },
            categoryId = categoryId.takeIf { kind == ChallengeKind.CATEGORY_WEEK },
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
            raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull()?.ensuredIds() } ?: ChallengeState()
    }
}

private fun newChallengeId(): String = UUID.randomUUID().toString()

/**
 * Gives every stored run an id, so the next save keeps it.
 * An older run has none on disk: the id is derived from the run, so a second read of the
 * same JSON agrees and giving up still finds the one on screen.
 */
private fun ChallengeState.ensuredIds(): ChallengeState = copy(
    active = active?.withId(),
    history = history.map { it.copy(run = it.run.withId()) },
)

private fun ChallengeRun.withId(): ChallengeRun = if (id != null) this else copy(id = legacyChallengeId())

private fun ChallengeRun.legacyChallengeId(): String {
    val name = "$kind|$startEpochDay|${categoryName.orEmpty()}|${categoryId ?: ""}"
    return UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)).toString()
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

    /** Today with nothing against it yet: it only settles once the day is over (late SMS). */
    TODAY,
    FUTURE,
}

data class ChallengeDay(val date: LocalDate, val status: DayStatus, val misses: List<ChallengeRow> = emptyList())

data class ChallengeProgress(
    val run: ChallengeRun,
    val days: List<ChallengeDay>,
    /** 1-based day of the window today falls on. */
    val dayNumber: Int,
    /** Today is past the window's last day. */
    val over: Boolean = false,
) {
    val cleanDays: Int get() = days.count { it.status == DayStatus.CLEAN }
    val won: Boolean get() = cleanDays >= run.target

    /**
     * Won early, or the window is over. A loss waits for the window's end even when it's
     * certain, so an expense corrected the same day (made a transfer) can still clear it.
     */
    val finished: Boolean get() = won || over

    /** The target is still within reach. */
    val winnable: Boolean get() = cleanDays + days.count { it.status == DayStatus.TODAY || it.status == DayStatus.FUTURE } >= run.target

    val today: ChallengeDay? get() = days.getOrNull(dayNumber - 1)
}

/**
 * Each day of [this] run on [today]. A day with a counted expense is missed at once (more
 * messages can't undo it); a day without one is only clean once it has ended.
 */
fun ChallengeRun.progress(rows: List<ChallengeRow>, today: LocalDate): ChallengeProgress {
    val misses = rows.filter(::counts).groupBy { it.date }
    val days = (0 until length).map { i ->
        val date = start.plusDays(i.toLong())
        val hits = misses[date].orEmpty()
        val status = when {
            date.isAfter(today) -> DayStatus.FUTURE
            hits.isNotEmpty() -> DayStatus.MISSED
            date == today -> DayStatus.TODAY
            else -> DayStatus.CLEAN
        }
        ChallengeDay(date, status, if (status == DayStatus.MISSED) hits else emptyList())
    }
    // ponytail: a clean day settles at midnight, so a message dated that day arriving later
    // can't undo a win already recorded; hold days for a grace period if banks lag that much.
    return ChallengeProgress(this, days, (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceIn(1, length), today.isAfter(end))
}

const val FOOD_AND_DINING = "Food & Dining"

/**
 * The categories worth a "week without", most expenses first (by count: no money is summed),
 * Food & Dining leading when the user has any. Others says nothing to go without.
 */
fun challengeCategories(rows: List<ChallengeRow>, limit: Int = 3): List<String> {
    val ranked = rows.flatMap { it.categories }.filter { it.isNotBlank() && it != "Others" }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    val ordered = if (FOOD_AND_DINING in ranked) listOf(FOOD_AND_DINING) + (ranked - FOOD_AND_DINING) else ranked
    return ordered.take(limit).ifEmpty { listOf(FOOD_AND_DINING) }
}
