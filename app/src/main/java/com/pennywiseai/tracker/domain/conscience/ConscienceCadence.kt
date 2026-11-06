package com.pennywiseai.tracker.domain.conscience

import kotlin.random.Random

data class ConscienceLog(
    val firedAtMillis: List<Long> = emptyList(),
    val lineUsedOnEpochDay: Map<String, Long> = emptyMap(),
)

object ConscienceCadence {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val WEEK_MILLIS = 7 * DAY_MILLIS
    private const val LINE_COOLDOWN_DAYS = 30L
    private const val FIRE_PREFIX = "fire"
    private const val LINE_PREFIX = "line"

    fun allows(log: ConscienceLog, frequency: ConscienceFrequency, nowMillis: Long): Boolean {
        fun firedWithin(window: Long) = log.firedAtMillis.count { nowMillis - it < window }
        return when (frequency) {
            ConscienceFrequency.NORMAL -> firedWithin(DAY_MILLIS) < 1 && firedWithin(WEEK_MILLIS) < 3
            ConscienceFrequency.LESS -> firedWithin(WEEK_MILLIS) < 1
        }
    }

    fun pickLine(
        pool: List<ConscienceLine>,
        log: ConscienceLog,
        todayEpochDay: Long,
        random: Random,
    ): ConscienceLine? = pool
        .filter { line -> log.lineUsedOnEpochDay[line.id]?.let { todayEpochDay - it >= LINE_COOLDOWN_DAYS } ?: true }
        .randomOrNull(random)

    fun record(log: ConscienceLog, lineId: String, nowMillis: Long, todayEpochDay: Long): ConscienceLog =
        ConscienceLog(
            firedAtMillis = (log.firedAtMillis + nowMillis).filter { nowMillis - it < WEEK_MILLIS },
            lineUsedOnEpochDay = (log.lineUsedOnEpochDay + (lineId to todayEpochDay))
                .filterValues { todayEpochDay - it < LINE_COOLDOWN_DAYS },
        )

    fun encode(log: ConscienceLog): Set<String> =
        log.firedAtMillis.map { "$FIRE_PREFIX|$it" }.toSet() +
            log.lineUsedOnEpochDay.map { (id, day) -> "$LINE_PREFIX|$id|$day" }

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
        )
    }
}
