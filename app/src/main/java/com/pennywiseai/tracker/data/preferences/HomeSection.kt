package com.pennywiseai.tracker.data.preferences

/**
 * The toggleable / reorderable sections of the Home screen (#770). Declaration
 * order is the default display order: decisions first (what can I spend, am I
 * over budget, did the app catch my spending), reference detail last. The fixed
 * header (balance card) is deliberately not here.
 */
enum class HomeSection(val label: String) {
    SAFE_TO_SPEND("Safe to spend"),
    BUDGETS("Budgets"),
    RECENT_TRANSACTIONS("Recent Transactions"),
    ACCOUNTS("Bank Accounts"),
    SUBSCRIPTIONS("Upcoming Subscriptions"),
    LOANS("Loans"),
    GOALS("Goals"),
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
    val DEFAULT: List<Pair<HomeSection, Boolean>> = HomeSection.entries.map { it to true }

    fun encode(layout: List<Pair<HomeSection, Boolean>>): String =
        layout.joinToString(",") { (section, visible) -> (if (visible) "" else "!") + section.name }

    /**
     * Unknown names are dropped; enum values absent from the string are inserted
     * visible at their default position (clamped to the end), so a section added
     * in a later version shows up where a new user would see it.
     */
    fun decode(raw: String?): List<Pair<HomeSection, Boolean>> {
        if (raw.isNullOrBlank()) return DEFAULT
        val parsed = raw.split(',').mapNotNull { token ->
            val t = token.trim()
            val hidden = t.startsWith("!")
            HomeSection.entries.firstOrNull { it.name == t.removePrefix("!") }?.let { it to !hidden }
        }.distinctBy { it.first }
        val seen = parsed.mapTo(HashSet()) { it.first }
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

    /** [section] shown or hidden in place. */
    fun withVisibility(layout: List<Pair<HomeSection, Boolean>>, section: HomeSection, visible: Boolean): List<Pair<HomeSection, Boolean>> =
        layout.map { if (it.first == section) section to visible else it }
}
