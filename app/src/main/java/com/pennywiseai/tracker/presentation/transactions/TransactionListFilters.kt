package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.countedCategory
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import java.math.BigDecimal

/*
 * The Transactions list's in-memory query steps, as plain functions of their inputs.
 * TransactionsViewModel decides when they run and on which thread, and applies them in this
 * order: categories, type, search, tracked accounts and profile, account, tag, currency and
 * sort.
 */

/**
 * Whether [name] is one of these categories, in any case: a chat figure counts a row filed as
 * "food" under "Food", and the list its link opens has to show that row too. A blank one is
 * Others ([countedCategory]), where budgets and Analytics count it.
 */
private fun Set<String>.hasCategory(name: String) = countedCategory(name).let { counted -> any { it.equals(counted, ignoreCase = true) } }

/** A refund: money back that comes off spending instead of counting as income. */
internal fun TransactionEntity.isRefund() =
    transactionType == TransactionType.INCOME && budgetImpactType == BudgetImpactType.DEDUCT_SPENT

/**
 * Rows in [categories]: a split row by its splits' categories, any other by its own, the same
 * match Analytics makes through [TransactionWithSplits.getAmountByCategory]. A refund also
 * matches by the budget category it comes off (blank is Others), as Analytics nets it, so the
 * list adds up to the figure it was opened from.
 */
internal fun filterByCategories(
    transactions: List<TransactionEntity>,
    categories: Set<String>,
    splits: List<TransactionSplitEntity>,
): List<TransactionEntity> {
    val splitsByTxId = splits.groupBy { it.transactionId }
    return transactions.filter { tx ->
        (splitsByTxId[tx.id]?.any { categories.hasCategory(it.category) } ?: categories.hasCategory(tx.category)) ||
            (tx.isRefund() && categories.hasCategory(tx.budgetCategory.orEmpty()))
    }
}

/**
 * The part of each split row that falls in a category filter (#1192). Without it a ₹10,000
 * row split ₹7,000 Others + ₹3,000 Shopping showed at ₹10,000 under both categories, while
 * Analytics counted ₹7,000 and ₹3,000.
 */
class CategorySplitShares(
    private val categories: Set<String>,
    splits: List<TransactionSplitEntity>,
) {
    private val splitsByTxId = splits.groupBy { it.transactionId }

    /** The amount of [tx]'s splits in the filter, or null when [tx] isn't split. For display. */
    fun shareOf(tx: TransactionEntity): BigDecimal? =
        if (tx.refundsFilter()) null else splitsByTxId[tx.id]?.filter { categories.hasCategory(it.category) }?.sumOf { it.amount }

    // A refund comes off its budget category whole, whatever its splits say, as in Analytics (#1376).
    private fun TransactionEntity.refundsFilter() = isRefund() && categories.hasCategory(budgetCategory.orEmpty())

    /** The amount [tx] shows: its share when split, else its own amount. */
    fun shownAmount(tx: TransactionEntity): BigDecimal = shareOf(tx) ?: tx.amount

    /**
     * [tx] as the totals should count it: [forTotals] on the whole row, splits cut in
     * proportion, then only the splits in the filter. Null when none of it counts.
     */
    fun forTotals(tx: TransactionEntity): TransactionEntity? {
        val splits = splitsByTxId[tx.id]?.takeUnless { tx.refundsFilter() } ?: return tx.forTotals()
        val counted = TransactionWithSplits(tx, splits).forTotals() ?: return null
        val share = counted.splits.filter { categories.hasCategory(it.category) }.sumOf { it.amount }
        return tx.copy(amount = share, loanId = null, loanContribution = null, lentSharesTotal = null).forTotals()
    }
}

internal fun filterByType(
    transactions: List<TransactionEntity>,
    typeFilter: TransactionTypeFilter,
): List<TransactionEntity> {
    val types = typeFilter.transactionTypes ?: return transactions
    return transactions.filter { it.transactionType in types }
}

/**
 * Rows matching [query]; a blank query is no search at all. See [matchesSearch]. [amountOf] is
 * the amount the row shows, its split share under a category filter (#1192).
 */
internal fun filterBySearch(
    transactions: List<TransactionEntity>,
    query: String,
    tagsById: Map<Long, List<String>>,
    aliasByMerchant: Map<String, String>,
    amountOf: (TransactionEntity) -> BigDecimal = { it.amount },
): List<TransactionEntity> {
    if (query.isBlank()) return transactions
    return transactions.filter { it.matchesSearch(query, tagsById[it.id], aliasByMerchant[it.merchantName], amountOf(it)) }
}

/**
 * Whether [query] appears, ignoring case, in this row's merchant, description, reference,
 * [tags], the merchant's user-set [merchantAlias] (#691) or SMS body, or in its amount.
 */
internal fun TransactionEntity.matchesSearch(
    query: String,
    tags: List<String>?,
    merchantAlias: String?,
    shownAmount: BigDecimal = amount,
): Boolean =
    merchantName.contains(query, ignoreCase = true) ||
        description?.contains(query, ignoreCase = true) == true ||
        reference?.contains(query, ignoreCase = true) == true ||
        tags?.any { it.contains(query, ignoreCase = true) } == true ||
        merchantAlias?.contains(query, ignoreCase = true) == true ||
        smsBody?.contains(query, ignoreCase = true) == true ||
        amountMatchesSearch(query, shownAmount)

/** Partial match on the plain amount, so "1,000" finds 1000.00. Non-numeric queries never match. */
private fun amountMatchesSearch(query: String, amount: BigDecimal): Boolean {
    val cleanedQuery = query.replace(",", "").replace(" ", "").trim()
    return cleanedQuery.isNotEmpty() &&
        cleanedQuery.all { it.isDigit() || it == '.' } &&
        amount.toPlainString().contains(cleanedQuery)
}

/**
 * The (first, second) tag filter after dropping any tag for which [exists] is false; a second tag
 * with no first is promoted to first, so the picker and chips always match what the list filters by.
 */
internal fun normalizeTags(first: String?, also: String?, exists: (String) -> Boolean = { true }): Pair<String?, String?> {
    val f = first?.takeIf(exists)
    val a = also?.takeIf(exists)
    return if (f == null) a to null else f to a
}

/**
 * Rows carrying exactly [tag], ignoring case; null means no tag filter. A non-null [alsoTag]
 * (an Analytics drill-down into a tag row under a tag filter) is required as well.
 */
internal fun filterByTag(
    transactions: List<TransactionEntity>,
    tag: String?,
    tagsById: Map<Long, List<String>>,
    alsoTag: String? = null,
): List<TransactionEntity> {
    val required = listOfNotNull(tag, alsoTag)
    if (required.isEmpty()) return transactions
    return transactions.filter { tx ->
        val tags = tagsById[tx.id].orEmpty()
        required.all { want -> tags.any { it.equals(want, ignoreCase = true) } }
    }
}

/**
 * The categories the list can be filtered on, for the chips row. [allSplits] may hold splits of
 * rows outside [transactions]; those don't count.
 */
internal fun filterableCategories(
    transactions: List<TransactionEntity>,
    allSplits: List<TransactionSplitEntity>,
    parentOf: Map<String, String>,
): List<String> {
    val ids = transactions.mapTo(HashSet()) { it.id }
    val splits = allSplits.filter { it.transactionId in ids }
    // Parents of present sub-categories are filterable too (#374),
    // and the auto-clear in the ViewModel must not drop them.
    // A refund's budget category too: [filterByCategories] lists it there, even with no spend left in it (#1376).
    val refunded = transactions.filter { it.isRefund() }.map { countedCategory(it.budgetCategory.orEmpty()) }
    val present = (transactions.map { countedCategory(it.category) } + splits.map { countedCategory(it.category) } + refunded).distinct()
    return (present + present.mapNotNull { parentOf[it] }).distinct().sorted()
}

/** [amountOf] is the amount the row shows, its split share under a category filter (#1192). */
internal fun sortTransactions(
    transactions: List<TransactionEntity>,
    sortOption: SortOption,
    amountOf: (TransactionEntity) -> BigDecimal = { it.amount },
): List<TransactionEntity> =
    when (sortOption) {
        SortOption.DATE_NEWEST -> transactions.sortedByDescending { it.dateTime }
        SortOption.DATE_OLDEST -> transactions.sortedBy { it.dateTime }
        SortOption.AMOUNT_HIGHEST -> transactions.sortedByDescending(amountOf)
        SortOption.AMOUNT_LOWEST -> transactions.sortedBy(amountOf)
        SortOption.MERCHANT_AZ -> transactions.sortedBy { it.merchantName.lowercase() }
        SortOption.MERCHANT_ZA -> transactions.sortedByDescending { it.merchantName.lowercase() }
    }
