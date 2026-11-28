package com.pennywiseai.tracker.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A Material date picker speaks UTC midnight millis; these two convert to and from a plain date. */
fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
