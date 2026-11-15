package com.pennywiseai.tracker.data.preferences

/** Codec for the `needs_answer_snoozed` preference (#1049): "hash|epochDay" entries. */
object NeedsAnswerSnoozes {
    /** "Keep as recorded": hidden for good. */
    const val FOREVER = Long.MAX_VALUE

    fun decode(raw: Set<String>?): Map<String, Long> = raw.orEmpty().mapNotNull { entry ->
        val at = entry.lastIndexOf('|')
        if (at <= 0) return@mapNotNull null
        entry.substring(at + 1).toLongOrNull()?.let { entry.substring(0, at) to it }
    }.toMap()

    fun encode(snoozes: Map<String, Long>): Set<String> = snoozes.map { (hash, until) -> "$hash|$until" }.toSet()
}
