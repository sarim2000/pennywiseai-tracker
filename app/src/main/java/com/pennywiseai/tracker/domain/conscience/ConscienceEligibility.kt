package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.utils.forTotals

object ConscienceEligibility {
    /**
     * Which rows the conscience may read at all: not an ignored account's, which the rest of
     * the app hides, and not business spending. SMS rows usually have no profile of their own,
     * so a business account's rows only show up as business through their account.
     */
    fun inScope(ignoredKeys: Set<String>, profileAccountKeys: Map<Long, Set<String>>): (TransactionEntity) -> Boolean = { tx ->
        !IgnoredAccountsStore.isIgnored(ignoredKeys, tx.bankName, tx.accountNumber) &&
            filterTransactionsByProfile(listOf(tx), ProfileEntity.BUSINESS_ID, profileAccountKeys).isEmpty()
    }

    /** Whether [tx] may trigger a line. Stricter than [historyOf]: a planned or shared charge earns no joke. */
    fun toPurchase(
        tx: TransactionEntity,
        builtinKeyByCategoryName: Map<String, String>,
        countCreditAsExpense: Boolean,
        hasSplits: Boolean,
    ): ConsciencePurchase? {
        val isSpend = tx.transactionType == TransactionType.EXPENSE ||
            (countCreditAsExpense && tx.transactionType == TransactionType.CREDIT)
        if (!isSpend || tx.isDeleted || tx.excludedFromAnalytics || tx.isRecurring) return null
        if (tx.loanId != null || tx.lentSharesTotal != null || hasSplits) return null
        if (tx.amount.signum() <= 0) return null
        val category = ConscienceCategory.fromBuiltinKey(builtinKeyByCategoryName[tx.category]) ?: return null
        return ConsciencePurchase(tx.dateTime, tx.amount, tx.currency, category, tx.id, tx.merchantName, tx.isRecurring)
    }

    /**
     * What a row spent per category, for the detectors to compare against. Every
     * real purchase counts, or a line could call a day a record or a category
     * quiet when the ledger says otherwise: recurring charges and card purchases
     * in full, split rows by their split categories, shared bills by the user's part.
     */
    fun historyOf(
        row: TransactionWithSplits,
        builtinKeyByCategoryName: Map<String, String>,
    ): List<ConsciencePurchase> {
        val tx = row.transaction
        if (tx.transactionType != TransactionType.EXPENSE && tx.transactionType != TransactionType.CREDIT) return emptyList()
        val counted = row.forTotals() ?: return emptyList()
        return counted.getAmountByCategory().mapNotNull { (name, amount) ->
            val category = ConscienceCategory.fromBuiltinKey(builtinKeyByCategoryName[name])
            if (category == null || amount.signum() <= 0) null
            else ConsciencePurchase(tx.dateTime, amount, tx.currency, category, tx.id, tx.merchantName, tx.isRecurring, tx.amount)
        }
    }
}
