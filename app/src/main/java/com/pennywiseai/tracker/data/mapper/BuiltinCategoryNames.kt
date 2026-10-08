package com.pennywiseai.tracker.data.mapper

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.builtinKey

/**
 * Built-in original name → current name, mirrored from the categories table by
 * PennyWiseApplication (#823). The auto-categorizer speaks in built-in names;
 * code that turns its output into a transaction (SMS/notification/statement
 * mapping, chat, subscriptions) translates through here, before rules run, so a
 * renamed built-in keeps receiving its transactions.
 */
object BuiltinCategoryNames {
    @Volatile
    private var currentByKey: Map<String, String> = emptyMap()

    fun update(categories: List<CategoryEntity>) {
        currentByKey = categories.mapNotNull { c -> c.builtinKey?.let { it to c.name } }
            .filter { (key, name) -> key != name }
            .toMap()
    }

    internal fun snapshot(): Map<String, String> = currentByKey
    internal fun restore(snapshot: Map<String, String>) { currentByKey = snapshot }

    /** The current name for the auto-categorizer's [category]. */
    fun current(category: String): String = currentByKey[category] ?: category
}
