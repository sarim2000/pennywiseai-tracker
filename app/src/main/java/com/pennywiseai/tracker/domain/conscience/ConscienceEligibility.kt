package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType

object ConscienceEligibility {
    fun toPurchase(
        tx: TransactionEntity,
        builtinKeyByCategoryName: Map<String, String>,
        countCreditAsExpense: Boolean,
        splitTransactionIds: Set<Long>,
    ): ConsciencePurchase? {
        val isSpend = tx.transactionType == TransactionType.EXPENSE ||
            (countCreditAsExpense && tx.transactionType == TransactionType.CREDIT)
        if (!isSpend || tx.isDeleted || tx.excludedFromAnalytics || tx.isRecurring) return null
        if (tx.loanId != null || tx.lentSharesTotal != null || tx.id in splitTransactionIds) return null
        if (tx.profileId == ProfileEntity.BUSINESS_ID || tx.amount.signum() <= 0) return null
        val category = ConscienceCategory.fromBuiltinKey(builtinKeyByCategoryName[tx.category]) ?: return null
        return ConsciencePurchase(tx.dateTime, tx.amount, tx.currency, category)
    }
}
