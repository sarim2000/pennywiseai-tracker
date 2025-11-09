package com.pennywiseai.tracker.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Utility functions for date range formatting
 */
object DateRangeUtils {

    private val defaultFormatter = DateTimeFormatter.ofPattern("MMM d")

    /**
     * Formats a date range as a compact label string.
     * Used for displaying custom date ranges in filter chips and UI.
     *
     * @param startDate The start date of the range
     * @param endDate The end date of the range
     * @param formatter Optional custom formatter (defaults to "MMM d" pattern)
     * @return Formatted string like "Jan 1 - Jan 31" or "Dec 25 - Jan 5"
     *
     * Example:
     * ```
     * formatDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
     * // Returns: "Jan 1 - Jan 31"
     * ```
     */
    fun formatDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
        formatter: DateTimeFormatter = defaultFormatter
    ): String {
        return "${startDate.format(formatter)} - ${endDate.format(formatter)}"
    }

    /**
     * Formats an optional date range pair as a compact label string.
     * Returns null if the pair is null.
     *
     * @param dateRange Optional pair of start and end dates
     * @param formatter Optional custom formatter (defaults to "MMM d" pattern)
     * @return Formatted string or null if dateRange is null
     */
    fun formatDateRange(
        dateRange: Pair<LocalDate, LocalDate>?,
        formatter: DateTimeFormatter = defaultFormatter
    ): String? {
        return dateRange?.let { (start, end) ->
            formatDateRange(start, end, formatter)
        }
    }
}
