package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PaydayDetectionTest {

    private var nextId = 0L

    private fun tx(
        date: String,
        amount: String,
        type: TransactionType = TransactionType.INCOME,
        category: String = "Others",
        currency: String = "JOD"
    ) = TransactionEntity(
        id = ++nextId, amount = BigDecimal(amount), merchantName = "Employer", category = category,
        transactionType = type, dateTime = LocalDate.parse(date).atTime(10, 0),
        transactionHash = "h$nextId", currency = currency
    )

    @Test
    fun `payday is the earliest day of the monthly salary`() {
        val txs = listOf(
            tx("2026-08-26", "900", category = "Salary"),
            tx("2026-09-25", "900", category = "Salary"),
            tx("2026-10-27", "900", category = "Salary"),
            // A bigger one-off credit that isn't salary doesn't move it.
            tx("2026-10-03", "5000", category = "Income")
        )
        assertEquals(25, detectPayday(txs, salaryCategory = "Salary"))
    }

    @Test
    fun `payday falls back to the biggest monthly credit when nothing is categorised as salary`() {
        val txs = listOf(
            tx("2026-09-01", "20"),
            tx("2026-09-28", "900"),
            tx("2026-10-28", "900")
        )
        assertEquals(28, detectPayday(txs, salaryCategory = "Salary"))
    }

    @Test
    fun `scattered or single-month income gives no payday`() {
        assertNull(detectPayday(listOf(tx("2026-10-25", "900")), "Salary"))
        val scattered = listOf(tx("2026-09-03", "900"), tx("2026-10-20", "900"))
        assertNull(detectPayday(scattered, "Salary"))
    }

    @Test
    fun `expenses and deleted credits are ignored`() {
        val txs = listOf(
            tx("2026-09-25", "900"),
            tx("2026-10-25", "900"),
            tx("2026-10-02", "5000", type = TransactionType.EXPENSE),
            tx("2026-10-05", "9000").copy(isDeleted = true)
        )
        assertEquals(25, detectPayday(txs, "Salary"))
    }
}
