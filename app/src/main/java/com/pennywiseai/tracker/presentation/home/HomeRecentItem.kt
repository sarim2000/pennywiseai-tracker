package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import java.math.BigDecimal
import java.time.LocalDateTime

sealed class HomeRecentItem {
    abstract val sortTime: LocalDateTime

    data class SingleTransaction(
        val transaction: TransactionEntity,
        val convertedAmount: BigDecimal? = null
    ) : HomeRecentItem() {
        override val sortTime: LocalDateTime get() = transaction.dateTime
    }

    data class GroupItem(
        val group: TransactionGroupEntity,
        val transactions: List<TransactionEntity>,
        val convertedAmounts: Map<Long, BigDecimal> = emptyMap()
    ) : HomeRecentItem() {
        override val sortTime: LocalDateTime
            get() = transactions.maxOfOrNull { it.dateTime } ?: group.updatedAt
    }
}
