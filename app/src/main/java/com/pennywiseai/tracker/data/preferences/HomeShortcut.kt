package com.pennywiseai.tracker.data.preferences

/**
 * What Home's shortcut row can hold: one tap to a place that's otherwise a tab and a
 * section, or Settings, away. Declaration order is the order Edit home offers them in.
 */
enum class HomeShortcut {
    UNSORTED,
    SEARCH,
    BILLS,
    CALENDAR,
    ACCOUNTS,
    BUDGETS,
    GOALS,
    REVIEW,
    RECURRING,
    CHECKUP,
}

/**
 * Codec for the `home_shortcuts` preference: the picked shortcuts' names in display
 * order, comma-separated. Pure so it is unit-testable without DataStore.
 */
object HomeShortcutLayout {
    /** The row holds this many at most; four fill a phone's width at the tile size. */
    const val MAX = 4

    /**
     * What a user who never picked any sees: the daily job (sorting), the lookup the
     * tab takes two taps for, and the two views a menu or toolbar deep. Budgets,
     * Insights and Ask are already a tab, and Home's Coming up already opens Bills,
     * so they're picks, not defaults.
     */
    val DEFAULT: List<HomeShortcut> = listOf(
        HomeShortcut.UNSORTED,
        HomeShortcut.SEARCH,
        HomeShortcut.REVIEW,
        HomeShortcut.CALENDAR,
    )

    fun encode(shortcuts: List<HomeShortcut>): String = shortcuts.joinToString(",") { it.name }

    /**
     * Absent is [DEFAULT]; anything stored (even empty, every shortcut turned off) is
     * the user's. Unknown names and repeats are dropped, and it's cut to [MAX].
     */
    fun decode(raw: String?): List<HomeShortcut> {
        if (raw == null) return DEFAULT
        return raw.split(',')
            .mapNotNull { token -> HomeShortcut.entries.firstOrNull { it.name == token.trim() } }
            .distinct()
            .take(MAX)
    }

    /**
     * [shortcut] added at the end or taken out. Adding to a full row changes nothing:
     * the user takes one out first, so a pick never silently drops another.
     */
    fun toggle(shortcuts: List<HomeShortcut>, shortcut: HomeShortcut): List<HomeShortcut> = when {
        shortcut in shortcuts -> shortcuts - shortcut
        shortcuts.size >= MAX -> shortcuts
        else -> shortcuts + shortcut
    }
}
