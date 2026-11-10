package com.pennywiseai.tracker.utils

import java.time.LocalDateTime

/**
 * The window to ask SQL for so that it returns every row between [start] and [end].
 * date_time is compared as text, and the converter reads more forms than it writes:
 * "2026-11-01 08:30:15" and "2026-11-01T00:00" both sort before a "2026-11-01T00:00:00"
 * bound, though neither is before that moment, and "2026-10-31 24:00:00" reads as
 * November 1 yet carries the previous date. So the lower bound clears all of the previous
 * date in every form, and the caller filters the parsed values to the exact boundary.
 */
internal fun dateQueryWindow(start: LocalDateTime, end: LocalDateTime): Pair<LocalDateTime, LocalDateTime> =
    start.minusDays(2) to end.plusDays(1)
