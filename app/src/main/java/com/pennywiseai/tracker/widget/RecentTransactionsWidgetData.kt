package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

data class RecentTransactionItem(
    val title: String,
    val subtitle: String,
    val amount: BigDecimal,
    val currency: String,
    val transactionType: TransactionType,
    /** Null for a snapshot written before rows carried ids; the row then opens the app. */
    val transactionId: Long? = null
)

data class RecentTransactionsWidgetData(
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val currency: String = "INR",
    val transactions: List<RecentTransactionItem> = emptyList()
)
