package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.utils.forTotals

object ConscienceEligibility {
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
        if (tx.profileId == ProfileEntity.BUSINESS_ID || tx.amount.signum() <= 0) return null
        val category = ConscienceCategory.fromBuiltinKey(builtinKeyByCategoryName[tx.category]) ?: return null
        return ConsciencePurchase(tx.dateTime, tx.amount, tx.currency, category)
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
        if (tx.profileId == ProfileEntity.BUSINESS_ID) return emptyList()
        val counted = row.forTotals() ?: return emptyList()
        return counted.getAmountByCategory().mapNotNull { (name, amount) ->
            val category = ConscienceCategory.fromBuiltinKey(builtinKeyByCategoryName[name])
            if (category == null || amount.signum() <= 0) null
            else ConsciencePurchase(tx.dateTime, amount, tx.currency, category)
        }
    }
}
