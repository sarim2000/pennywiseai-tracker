package com.pennywiseai.tracker.domain.review

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class PeriodDetailTest {
    private var nextId = 1L

    private fun tx(
        date: String,
        amount: String = "100",
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "JOD",
        category: String = "Food",
        impact: BudgetImpactType? = null,
    ) = TransactionWithSplits(
        TransactionEntity(
            id = nextId, amount = BigDecimal(amount), merchantName = "Shop", category = category,
            transactionType = type, dateTime = LocalDate.parse(date).atTime(12, 0), currency = currency,
            transactionHash = "h${nextId++}", budgetImpactType = impact, budgetCategory = category,
        ),
        emptyList()
    )

    private val current = reviewRange(ReviewKind.MONTH, 0, LocalDate.parse("2026-11-16"), 25, DayOfWeek.MONDAY)
    private val cycles = cyclesEndingWith(current, 25, 3)

    @Test
    fun `cycles run oldest first and end with the reviewed one`() {
        assertEquals(
            listOf(
                LocalDate.parse("2026-08-25") to LocalDate.parse("2026-09-24"),
                LocalDate.parse("2026-09-25") to LocalDate.parse("2026-10-24"),
                LocalDate.parse("2026-10-25") to LocalDate.parse("2026-11-24"),
            ),
            cycles,
        )
    }

    @Test
    fun `each currency gets every cycle, empty ones as zero`() = runBlocking {
        val detail = buildPeriodDetail(
            cycles,
            listOf(tx("2026-11-01", "40"), tx("2026-09-01", "5", currency = "USD")),
            creditAsExpense = false,
            parentOf = emptyMap(),
        )
        val jod = detail.cycles.getValue("JOD")
        assertEquals(3, jod.size)
        assertFalse(jod[0].hasActivity)
        assertEquals(BigDecimal("40"), jod[2].spent)
        // Dollars stay in their own series, never added to dinars.
        assertEquals(BigDecimal("5"), detail.cycles.getValue("USD")[0].spent)
        assertEquals(BigDecimal.ZERO, detail.cycles.getValue("USD")[2].spent)
    }

    @Test
    fun `cycles use the review's definitions`() = runBlocking {
        val rows = listOf(
            tx("2026-11-01", "100"),
            tx("2026-11-02", "30", type = TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
            tx("2026-11-03", "500", type = TransactionType.INCOME, category = "Salary"),
            tx("2026-11-04", "70", type = TransactionType.CREDIT),
            tx("2026-11-05", "900", type = TransactionType.TRANSFER),
        )
        val detail = buildPeriodDetail(
            cycles, rows, creditAsExpense = true, parentOf = emptyMap(),
            settlementLoss = listOf(emptyMap(), emptyMap(), mapOf("JOD" to BigDecimal("10"))),
        )
        val now = detail.cycles.getValue("JOD").last()
        // 100 + 70 card spend - 30 refund + 10 forgiven loan; the refund isn't income.
        assertEquals(BigDecimal("150"), now.spent)
        assertEquals(BigDecimal("500"), now.income)
        assertEquals(BigDecimal("140"), now.byCategory.getValue("Food"))
        // Only rows that count as spending: no refunds, income or transfers.
        assertEquals(listOf("100", "70"), detail.spendRows.map { it.transaction.amount.toPlainString() })

        val review = buildPeriodReview(current, rows, emptyList(), true, emptyMap(), mapOf("JOD" to BigDecimal("10")))
        assertEquals(review.currencies.single().spent, now.spent)
        assertEquals(review.currencies.single().income, now.income)
    }
}
