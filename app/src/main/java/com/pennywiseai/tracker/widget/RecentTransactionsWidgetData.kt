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
    val transactionId: Long? = null,
    /** Still in "Others": the row offers a Sort pill. */
    val needsCategory: Boolean = false,
    /** The category's emoji, else its first letter, for the avatar. */
    val badge: String = "",
    /** The category's color; 0 (an old snapshot) draws a neutral avatar. */
    val colorArgb: Long = 0
)

data class RecentTransactionsWidgetData(
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    /** Today's share of [totalSpent]; the summary widget shows both. */
    val todaySpent: BigDecimal = BigDecimal.ZERO,
    val currency: String = "INR",
    val transactions: List<RecentTransactionItem> = emptyList()
)
