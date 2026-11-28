package com.pennywiseai.tracker.domain.challenges

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.domain.spending.analyticsRowsFor

/** [this] run under its category's current name: a rename moves its expenses with it. */
internal fun ChallengeRun.namedAsNow(categories: List<CategoryEntity>): ChallengeRun =
    categoryId?.let { id -> categories.firstOrNull { it.id == id } }?.let { copy(categoryName = it.name) } ?: this

/**
 * The spending Review counts ([analyticsRowsFor]: no transfers between own accounts,
 * loans or exclusions), one per row whatever its amount or currency. Card purchases
 * always count, whatever "count credit card as expense" says: that setting picks how
 * money is totalled, and a swipe at a restaurant is still spending that day. [keep] (the
 * running week's category) isn't rolled into a parent it was moved under mid-run.
 */
internal fun challengeRows(
    rows: List<TransactionWithSplits>,
    parentOf: Map<String, String>,
    keep: String? = null,
): List<ChallengeRow> = analyticsRowsFor(rows, TransactionTypeFilter.SPENDING).map { row ->
    val tx = row.transaction
    ChallengeRow(
        id = tx.id,
        date = tx.dateTime.toLocalDate(),
        // Only shares with something counted: a split's zero share doesn't spend its category.
        categories = row.getAmountByCategory().filterValues { it.signum() > 0 }.keys.map { it.ifEmpty { OTHERS }.let { name -> if (name == keep) name else parentOf[name] ?: name } }.toSet(),
        merchant = tx.merchantName,
    )
}
