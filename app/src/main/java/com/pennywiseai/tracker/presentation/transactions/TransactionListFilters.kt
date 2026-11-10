package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import java.time.LocalDate

/*
 * The Transactions list's in-memory query steps, as plain functions of their inputs.
 * TransactionsViewModel decides when they run and on which thread, and applies them in this
 * order: categories, type, search, tracked accounts and profile, account, tag, currency, sort,
 * date groups.
 */

/** Rows whose own category, or the category of one of their splits, is in [categories]. */
internal fun filterByCategories(
    transactions: List<TransactionEntity>,
    categories: Set<String>,
    splits: List<TransactionSplitEntity>,
): List<TransactionEntity> {
    val splitsByTxId = splits.groupBy { it.transactionId }
    return transactions.filter { tx ->
        tx.category in categories ||
            splitsByTxId[tx.id]?.any { it.category in categories } == true
    }
}

internal fun filterByType(
    transactions: List<TransactionEntity>,
    typeFilter: TransactionTypeFilter,
): List<TransactionEntity> {
    val types = typeFilter.transactionTypes ?: return transactions
    return transactions.filter { it.transactionType in types }
}

/** Rows matching [query]; a blank query is no search at all. See [matchesSearch]. */
internal fun filterBySearch(
    transactions: List<TransactionEntity>,
    query: String,
    tagsById: Map<Long, List<String>>,
    aliasByMerchant: Map<String, String>,
): List<TransactionEntity> {
    if (query.isBlank()) return transactions
    return transactions.filter { it.matchesSearch(query, tagsById[it.id], aliasByMerchant[it.merchantName]) }
}

/**
 * Whether [query] appears, ignoring case, in this row's merchant, description, reference,
 * [tags], the merchant's user-set [merchantAlias] (#691) or SMS body, or in its amount.
 */
internal fun TransactionEntity.matchesSearch(
    query: String,
    tags: List<String>?,
    merchantAlias: String?,
): Boolean =
    merchantName.contains(query, ignoreCase = true) ||
        description?.contains(query, ignoreCase = true) == true ||
        reference?.contains(query, ignoreCase = true) == true ||
        tags?.any { it.contains(query, ignoreCase = true) } == true ||
        merchantAlias?.contains(query, ignoreCase = true) == true ||
        smsBody?.contains(query, ignoreCase = true) == true ||
        amountMatchesSearch(query)

/** Partial match on the plain amount, so "1,000" finds 1000.00. Non-numeric queries never match. */
private fun TransactionEntity.amountMatchesSearch(query: String): Boolean {
    val cleanedQuery = query.replace(",", "").replace(" ", "").trim()
    return cleanedQuery.isNotEmpty() &&
        cleanedQuery.all { it.isDigit() || it == '.' } &&
        amount.toPlainString().contains(cleanedQuery)
}

internal fun filterTrackedByProfile(
    transactions: List<TransactionEntity>,
    profileId: Long?,
    ignoredAccountKeys: Set<String>,
    profileAccountKeys: Map<Long, Set<String>>,
): List<TransactionEntity> {
    // Ignored accounts drop out first, and independently of the profile
    // filter — that one returns early for "All profiles" and falls back to
    // Personal for an unattributed transaction, so it can't carry the
    // exclusion (#826).
    val tracked = if (ignoredAccountKeys.isEmpty()) {
        transactions
    } else {
        transactions.filterNot { tx ->
            IgnoredAccountsStore.isIgnored(ignoredAccountKeys, tx.bankName, tx.accountNumber)
        }
    }
    return filterTransactionsByProfile(tracked, profileId, profileAccountKeys)
}

/** Rows carrying exactly [tag], ignoring case; null means no tag filter. */
internal fun filterByTag(
    transactions: List<TransactionEntity>,
    tag: String?,
    tagsById: Map<Long, List<String>>,
): List<TransactionEntity> {
    if (tag == null) return transactions
    return transactions.filter { tx ->
        tagsById[tx.id]?.any { it.equals(tag, ignoreCase = true) } == true
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
    val present = (transactions.map { it.category } + splits.map { it.category }).distinct()
    return (present + present.mapNotNull { parentOf[it] }).distinct().sorted()
}

internal fun sortTransactions(
    transactions: List<TransactionEntity>,
    sortOption: SortOption,
): List<TransactionEntity> =
    when (sortOption) {
        SortOption.DATE_NEWEST -> transactions.sortedByDescending { it.dateTime }
        SortOption.DATE_OLDEST -> transactions.sortedBy { it.dateTime }
        SortOption.AMOUNT_HIGHEST -> transactions.sortedByDescending { it.amount }
        SortOption.AMOUNT_LOWEST -> transactions.sortedBy { it.amount }
        SortOption.MERCHANT_AZ -> transactions.sortedBy { it.merchantName.lowercase() }
        SortOption.MERCHANT_ZA -> transactions.sortedByDescending { it.merchantName.lowercase() }
    }

internal fun groupTransactionsByDate(
    transactions: List<TransactionEntity>,
    today: LocalDate = LocalDate.now(),
): Map<DateGroup, List<TransactionEntity>> {
    val yesterday = today.minusDays(1)
    val weekStart = today.minusWeeks(1)

    return transactions.groupBy { transaction ->
        val transactionDate = transaction.dateTime.toLocalDate()
        when {
            transactionDate == today -> DateGroup.TODAY
            transactionDate == yesterday -> DateGroup.YESTERDAY
            transactionDate > weekStart -> DateGroup.THIS_WEEK
            else -> DateGroup.EARLIER
        }
    }
}
