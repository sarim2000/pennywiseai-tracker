package com.pennywiseai.tracker.receiver

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** The recap's walk only shows rows that still need a category, in the recap's order. */
class QuickCategoryPickerWalkTest {

    private fun tx(id: Long, category: String = "Others", excluded: Boolean = false, deleted: Boolean = false) = TransactionEntity(
        id = id, amount = BigDecimal("5"), merchantName = "M", category = category,
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.now(), currency = "JOD",
        transactionHash = "h$id", excludedFromAnalytics = excluded, isDeleted = deleted
    )

    @Test
    fun `rows sorted meanwhile, excluded or deleted drop out, the rest keep their order`() {
        val rows = listOf(tx(4), tx(3, deleted = true), tx(2, category = "Food"), tx(1, excluded = true), tx(0))
        assertEquals(listOf(4L, 0L), QuickCategoryPickerActivity.stillUnsorted(rows, "Others").map { it.id })
    }
}
