package com.pennywiseai.tracker.domain.conscience

import kotlin.random.Random

data class ConscienceLog(
    val firedAtMillis: List<Long> = emptyList(),
    val lineUsedOnEpochDay: Map<String, Long> = emptyMap(),
    /** Last fire per "KIND_CATEGORY", for the per-kind cooldowns. */
    val kindFiredAtMillis: Map<String, Long> = emptyMap(),
)

/** What the user said about lines, by line key. A retired line never comes back; a liked one comes up more. */
data class ConscienceFeedback(
    val liked: Set<String> = emptySet(),
    val retired: Set<String> = emptySet(),
)

/** A fact with the lines that rendered for it, in the order the detectors ranked them. */
data class ConscienceCandidate(val fact: ConscienceFact, val lines: List<ConscienceLine>)

data class ConscienceChoice(val fact: ConscienceFact, val line: ConscienceLine, val lineKey: String)

object ConscienceCadence {
    private const val HOUR_MILLIS = 60 * 60 * 1000L
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
    private const val WEEK_MILLIS = 7 * DAY_MILLIS
    private const val LINE_COOLDOWN_DAYS = 30L
    private const val LIKED_WEIGHT = 2

    /** A hello only fills a silence: none when anything was said in the last 3 days. */
    private const val HELLO_SILENCE_MILLIS = 3 * DAY_MILLIS

    private const val FIRE_PREFIX = "fire"
    private const val LINE_PREFIX = "line"
    private const val KIND_PREFIX = "kind"
    private const val LIKE_PREFIX = "like"
    private const val RETIRE_PREFIX = "retire"

    /** How long a kind stays quiet in one category after it spoke there. */
    fun kindCooldownMillis(kind: ConscienceKind): Long = when (kind) {
        ConscienceKind.REPEAT -> 3 * DAY_MILLIS
        ConscienceKind.COMEBACK, ConscienceKind.LATE_NIGHT -> WEEK_MILLIS
        ConscienceKind.POSITIVE, ConscienceKind.WEEKLY, ConscienceKind.RECORD_DAY, ConscienceKind.SPIKE -> 2 * WEEK_MILLIS
        ConscienceKind.HELLO -> HELLO_SILENCE_MILLIS
    }

    fun allows(log: ConscienceLog, frequency: ConscienceFrequency, nowMillis: Long): Boolean {
        fun firedWithin(window: Long) = log.firedAtMillis.count { nowMillis - it < window }
        return when (frequency) {
            ConscienceFrequency.NORMAL -> firedWithin(DAY_MILLIS) < 1 && firedWithin(WEEK_MILLIS) < 3
            ConscienceFrequency.LESS -> firedWithin(WEEK_MILLIS) < 1
        }
    }

    private fun kindKey(fact: ConscienceFact) = "${fact.kind.name}_${fact.category.name}"

    /**
     * The first candidate whose kind is off cooldown and still has a line to
     * say, or null. Candidates arrive ranked, so priority is theirs.
     */
    fun choose(
        candidates: List<ConscienceCandidate>,
        log: ConscienceLog,
        feedback: ConscienceFeedback,
        frequency: ConscienceFrequency,
        contentLocale: String,
        nowMillis: Long,
        todayEpochDay: Long,
        random: Random,
    ): ConscienceChoice? {
        if (!allows(log, frequency, nowMillis)) return null
        for (candidate in candidates) {
            val fact = candidate.fact
            if (fact.kind == ConscienceKind.HELLO && log.firedAtMillis.any { nowMillis - it < HELLO_SILENCE_MILLIS }) continue
            val lastOfKind = log.kindFiredAtMillis[kindKey(fact)]
            if (lastOfKind != null && nowMillis - lastOfKind < kindCooldownMillis(fact.kind)) continue
            val line = pickLine(candidate.lines, contentLocale, log, feedback, todayEpochDay, random) ?: continue
            return ConscienceChoice(fact, line, ConscienceLines.key(contentLocale, line))
        }
        return null
    }

    fun pickLine(
        pool: List<ConscienceLine>,
        contentLocale: String,
        log: ConscienceLog,
        feedback: ConscienceFeedback,
        todayEpochDay: Long,
        random: Random,
    ): ConscienceLine? {
        val open = pool.filter { line ->
            val key = ConscienceLines.key(contentLocale, line)
            key !in feedback.retired &&
                (log.lineUsedOnEpochDay[key]?.let { todayEpochDay - it >= LINE_COOLDOWN_DAYS } ?: true)
        }
        if (open.isEmpty()) return null
        val weighted = open.flatMap { line ->
            List(if (ConscienceLines.key(contentLocale, line) in feedback.liked) LIKED_WEIGHT else 1) { line }
        }
        return weighted.random(random)
    }

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

    fun encodeFeedback(feedback: ConscienceFeedback): Set<String> =
        feedback.liked.map { "$LIKE_PREFIX|$it" }.toSet() + feedback.retired.map { "$RETIRE_PREFIX|$it" }

    fun decodeFeedback(raw: Set<String>): ConscienceFeedback {
        fun keys(prefix: String) = raw.filter { it.startsWith("$prefix|") }.map { it.removePrefix("$prefix|") }
            .filter { it.isNotEmpty() }.toSet()
        return ConscienceFeedback(liked = keys(LIKE_PREFIX), retired = keys(RETIRE_PREFIX))
    }
}
