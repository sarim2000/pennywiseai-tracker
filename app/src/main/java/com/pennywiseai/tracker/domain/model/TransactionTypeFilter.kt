package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.TransactionType

enum class TransactionTypeFilter {
    ALL,
    INCOME,
    EXPENSE,
    CREDIT,
    /** Expense + credit-card spend together (#898). Analytics-only, see [inTransactionsList]. */
    SPENDING,
    TRANSFER,
    INVESTMENT;

    /** The transaction types this filter shows; null means every type. */
    val transactionTypes: Set<TransactionType>?
        get() = when (this) {
            ALL -> null
            INCOME -> setOf(TransactionType.INCOME)
            EXPENSE -> setOf(TransactionType.EXPENSE)
            CREDIT -> setOf(TransactionType.CREDIT)
            SPENDING -> setOf(TransactionType.EXPENSE, TransactionType.CREDIT)
            TRANSFER -> setOf(TransactionType.TRANSFER)
            INVESTMENT -> setOf(TransactionType.INVESTMENT)
        }

    /** Spend views that net refunds (INCOME + DEDUCT_SPENT) off their totals (#629). */
    val netsRefunds: Boolean
        get() = this == EXPENSE || this == SPENDING

    /**
     * Offered in the Transactions screen's type menu. SPENDING is an Analytics
     * view (#898); the list already shows expense and credit totals side by side.
     */
    val inTransactionsList: Boolean
        get() = this != SPENDING
}
