package com.pennywiseai.tracker.data.preferences

/**
 * The toggleable / reorderable sections of the Home screen (#770). Declaration
 * order is the default display order: the things to act on first (what still needs a
 * category, what just happened, what's due), then the plans, then reference balances.
 * The hero is fixed and not here; Safe to spend is a line inside it, so its entry only
 * shows or hides that line.
 */
enum class HomeSection(val label: String) {
    SAFE_TO_SPEND("Safe to spend"),
    UNSORTED("Needs a category"),
    RECENT_TRANSACTIONS("Recent Transactions"),
    SUBSCRIPTIONS("Upcoming Subscriptions"),
    BUDGETS("Budgets"),
    GOALS("Goals"),
    ACCOUNTS("Bank Accounts"),
    LOANS("Loans"),
    GROUPS("Groups"),
    MONEY_IN_MOTION("Money in motion"),
    ACTIVITY("Activity"),
}

/**
 * Codec for the `home_sections` preference: comma-separated section names in
 * display order, a leading `!` marking a hidden one, e.g. `BUDGETS,!GROUPS,…`,
 * and the edits Home's section customisation makes to a layout.
 * Pure so it is unit-testable without DataStore.
 */
object HomeSectionLayout {
    /** The default order before Home was regrouped; a layout still in it was never reordered. */
    private val LEGACY_ORDER = listOf(
        HomeSection.SAFE_TO_SPEND, HomeSection.BUDGETS, HomeSection.UNSORTED, HomeSection.RECENT_TRANSACTIONS,
        HomeSection.ACCOUNTS, HomeSection.SUBSCRIPTIONS, HomeSection.LOANS, HomeSection.GOALS,
        HomeSection.GROUPS, HomeSection.MONEY_IN_MOTION, HomeSection.ACTIVITY,
    )

    val DEFAULT: List<Pair<HomeSection, Boolean>> = HomeSection.entries.map { it to true }

    /**
     * What a brand-new install starts with: [DEFAULT] minus the Activity heatmap, which
     * Insights already draws. Only ever written by [seedForFreshInstall]; an existing,
     * restored or absent-on-upgrade layout keeps decoding to [DEFAULT].
     */
    val FRESH_INSTALL: List<Pair<HomeSection, Boolean>> = withVisibility(DEFAULT, HomeSection.ACTIVITY, false)

    /**
     * The value to store when onboarding finishes on a fresh install, or null to leave
     * [stored] alone. Only an absent value is seeded: anything stored (even blank) is the
     * user's or a restore's, and is never overwritten.
     */
    fun seedForFreshInstall(stored: String?): String? = if (stored == null) encode(FRESH_INSTALL) else null

    fun encode(layout: List<Pair<HomeSection, Boolean>>): String =
        layout.joinToString(",") { (section, visible) -> (if (visible) "" else "!") + section.name }

    /**
     * Unknown names are dropped; enum values absent from the string are inserted
     * visible at their default position (clamped to the end), so a section added
     * in a later version shows up where a new user would see it. A layout still in
     * the old default order (never reordered, maybe some hidden) moves to the new
     * default order, keeping what's hidden; a reordered one keeps its order.
     */
    fun decode(raw: String?): List<Pair<HomeSection, Boolean>> {
        if (raw.isNullOrBlank()) return DEFAULT
        val parsed = raw.split(',').mapNotNull { token ->
            val t = token.trim()
            val hidden = t.startsWith("!")
            HomeSection.entries.firstOrNull { it.name == t.removePrefix("!") }?.let { it to !hidden }
        }.distinctBy { it.first }
        val seen = parsed.mapTo(HashSet()) { it.first }
        if (parsed.map { it.first } == LEGACY_ORDER.filter { it in seen }) {
            val hidden = parsed.filterNot { it.second }.mapTo(HashSet()) { it.first }
            return HomeSection.entries.map { it to (it !in hidden) }
        }
        val layout = parsed.toMutableList()
        HomeSection.entries.filter { it !in seen }
            .forEach { layout.add(it.ordinal.coerceAtMost(layout.size), it to true) }
        return layout
    }

    /**
     * [section] swapped with the section [delta] places away. Unchanged when [section]
     * isn't in [layout] or the move would pass either end.
     */
    fun move(layout: List<Pair<HomeSection, Boolean>>, section: HomeSection, delta: Int): List<Pair<HomeSection, Boolean>> {
        val from = layout.indexOfFirst { it.first == section }
        val to = from + delta
        return if (from < 0 || to !in layout.indices) layout
        else layout.toMutableList().also { java.util.Collections.swap(it, from, to) }
    }

    /**
     * [layout] put in [order] (a drag-and-drop result), each section keeping its own
     * visibility. Sections missing from [order] keep their relative order at the end.
     */
    fun reorder(layout: List<Pair<HomeSection, Boolean>>, order: List<HomeSection>): List<Pair<HomeSection, Boolean>> =
        layout.sortedBy { order.indexOf(it.first).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }

    /** [section] shown or hidden in place. */
    fun withVisibility(layout: List<Pair<HomeSection, Boolean>>, section: HomeSection, visible: Boolean): List<Pair<HomeSection, Boolean>> =
        layout.map { if (it.first == section) section to visible else it }
}
