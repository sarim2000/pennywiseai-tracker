package com.pennywiseai.tracker.data.database.entity

import androidx.room.Embedded
import androidx.room.Relation
import java.math.BigDecimal

data class TransactionWithSplits(
    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val splits: List<TransactionSplitEntity>
) {
    val hasSplits: Boolean
        get() = splits.isNotEmpty()

    /**
     * Returns amount breakdown by category.
     * If transaction has splits, returns split amounts.
     * Otherwise, returns the full transaction amount under its category.
     */
    fun getAmountByCategory(): Map<String, BigDecimal> {
        return if (hasSplits) {
            // Group by category and sum amounts (in case of duplicates)
            splits.groupBy { it.category }
                .mapValues { (_, splitList) -> splitList.sumOf { it.amount } }
        } else {
            mapOf(transaction.category to transaction.amount)
        }
    }

    /**
     * Calculates total of all splits. Should equal transaction.amount when valid.
     */
    fun getSplitsTotal(): BigDecimal {
        return splits.sumOf { it.amount }
    }

    /**
     * Checks if splits are valid (sum equals transaction amount within tolerance).
     */
    fun areSplitsValid(tolerance: BigDecimal = BigDecimal("0.01")): Boolean {
        if (!hasSplits) return true
        val difference = (transaction.amount - getSplitsTotal()).abs()
        return difference <= tolerance
    }
}
