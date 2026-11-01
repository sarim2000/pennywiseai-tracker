package com.pennywiseai.tracker.data.preferences

/**
 * How dates are shown. [full] has the year, [short] is day + month; null means
 * the language's own order ([AUTO]). Stored by name, so don't rename entries.
 */
enum class DateFormatStyle(val full: String?, val short: String?) {
    AUTO(null, null),
    DAY_MONTH_YEAR("d MMM yyyy", "d MMM"),
    MONTH_DAY_YEAR("MMM d, yyyy", "MMM d"),
    DMY_NUMERIC("dd/MM/yyyy", "dd/MM"),
    MDY_NUMERIC("MM/dd/yyyy", "MM/dd"),
    ISO("yyyy-MM-dd", "MM-dd")
}
