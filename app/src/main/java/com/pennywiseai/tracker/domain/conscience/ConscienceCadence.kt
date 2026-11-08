package com.pennywiseai.tracker.domain.conscience

import kotlin.random.Random

data class ConscienceLog(
    val firedAtMillis: List<Long> = emptyList(),
    val lineUsedOnEpochDay: Map<String, Long> = emptyMap(),
    /** Last fire per "KIND_CATEGORY", for the per-kind cooldowns. */
    val kindFiredAtMillis: Map<String, Long> = emptyMap(),
)

/** A fact with the lines that rendered for it, in the order the detectors ranked them. */
data class ConscienceCandidate(val fact: ConscienceFact, val lines: List<ConscienceLine>)

data class ConscienceChoice(val fact: ConscienceFact, val line: ConscienceLine, val lineKey: String)

object ConscienceCadence {
    private const val HOUR_MILLIS = 60 * 60 * 1000L
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
    private const val WEEK_MILLIS = 7 * DAY_MILLIS
    private const val LINE_COOLDOWN_DAYS = 30L

    private const val FIRE_PREFIX = "fire"
    private const val LINE_PREFIX = "line"
    private const val KIND_PREFIX = "kind"

    /** How long a kind stays quiet in one category after it spoke there. */
    fun kindCooldownMillis(kind: ConscienceKind): Long = when (kind) {
        ConscienceKind.REPEAT -> 3 * DAY_MILLIS
        ConscienceKind.LATE_NIGHT -> WEEK_MILLIS
        ConscienceKind.POSITIVE -> 2 * WEEK_MILLIS
    }

    /** At most once a day and three times a week. */
    fun allows(log: ConscienceLog, nowMillis: Long): Boolean {
        fun firedWithin(window: Long) = log.firedAtMillis.count { nowMillis - it < window }
        return firedWithin(DAY_MILLIS) < 1 && firedWithin(WEEK_MILLIS) < 3
    }

    private fun kindKey(fact: ConscienceFact) = "${fact.kind.name}_${fact.category.name}"

    /**
     * The first candidate whose kind is off cooldown and still has a line to
     * say, or null. Candidates arrive ranked, so priority is theirs.
     */
    fun choose(
        candidates: List<ConscienceCandidate>,
        log: ConscienceLog,
        retired: Set<String>,
        contentLocale: String,
        nowMillis: Long,
        todayEpochDay: Long,
        random: Random,
    ): ConscienceChoice? {
        if (!allows(log, nowMillis)) return null
        for (candidate in candidates) {
            val fact = candidate.fact
            val lastOfKind = log.kindFiredAtMillis[kindKey(fact)]
            if (lastOfKind != null && nowMillis - lastOfKind < kindCooldownMillis(fact.kind)) continue
            val line = pickLine(candidate.lines, contentLocale, log, retired, todayEpochDay, random) ?: continue
            return ConscienceChoice(fact, line, ConscienceLines.key(contentLocale, line))
        }
        return null
    }

    /** A line the user turned down never returns; any other waits 30 days. */
    fun pickLine(
        pool: List<ConscienceLine>,
        contentLocale: String,
        log: ConscienceLog,
        retired: Set<String>,
        todayEpochDay: Long,
        random: Random,
    ): ConscienceLine? = pool.filter { line ->
        val key = ConscienceLines.key(contentLocale, line)
        key !in retired && (log.lineUsedOnEpochDay[key]?.let { todayEpochDay - it >= LINE_COOLDOWN_DAYS } ?: true)
    }.randomOrNull(random)

    fun record(log: ConscienceLog, choice: ConscienceChoice, nowMillis: Long, todayEpochDay: Long): ConscienceLog {
        val longestCooldown = ConscienceKind.entries.maxOf { kindCooldownMillis(it) }
        return ConscienceLog(
            firedAtMillis = (log.firedAtMillis + nowMillis).filter { nowMillis - it < WEEK_MILLIS },
            lineUsedOnEpochDay = (log.lineUsedOnEpochDay + (choice.lineKey to todayEpochDay))
                .filterValues { todayEpochDay - it < LINE_COOLDOWN_DAYS },
            kindFiredAtMillis = (log.kindFiredAtMillis + (kindKey(choice.fact) to nowMillis))
                .filterValues { nowMillis - it < longestCooldown },
        )
    }

    fun encode(log: ConscienceLog): Set<String> =
        log.firedAtMillis.map { "$FIRE_PREFIX|$it" }.toSet() +
            log.lineUsedOnEpochDay.map { (id, day) -> "$LINE_PREFIX|$id|$day" } +
            log.kindFiredAtMillis.map { (kind, at) -> "$KIND_PREFIX|$kind|$at" }

    fun decode(raw: Set<String>): ConscienceLog {
        val entries = raw.map { it.split('|') }
        return ConscienceLog(
            firedAtMillis = entries
                .filter { it.size == 2 && it[0] == FIRE_PREFIX }
                .mapNotNull { it[1].toLongOrNull() }
                .sorted(),
            lineUsedOnEpochDay = entries
                .filter { it.size == 3 && it[0] == LINE_PREFIX && it[1].isNotEmpty() }
                .mapNotNull { (_, id, day) -> day.toLongOrNull()?.let { id to it } }
                .toMap(),
            kindFiredAtMillis = entries
                .filter { it.size == 3 && it[0] == KIND_PREFIX && it[1].isNotEmpty() }
                .mapNotNull { (_, kind, at) -> at.toLongOrNull()?.let { kind to it } }
                .toMap(),
        )
    }
}
