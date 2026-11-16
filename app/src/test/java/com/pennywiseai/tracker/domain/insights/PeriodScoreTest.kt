package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PeriodScoreTest {
    private var nextId = 1L

    private fun spends(count: Int, uncategorised: Int = 0) = (1..count).map { i ->
        TransactionEntity(
            id = nextId, amount = BigDecimal.TEN, merchantName = "Shop",
            category = if (i <= uncategorised) "Others" else "Food",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDate.parse("2026-11-10").atTime(12, 0),
            currency = "INR", transactionHash = "h${nextId++}",
        )
    }

    private fun budget(spent: String, currency: String = "INR") = BudgetStatus(
        1, "Food", currency, BigDecimal(spent), BigDecimal("3000"), 10, 30,
        LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"),
    )

    private fun score(
        income: String = "1000",
        spent: String = "800",
        budgets: List<BudgetStatus> = emptyList(),
        rows: List<TransactionEntity> = spends(10),
    ) = PeriodScore.of("INR", BigDecimal(income), BigDecimal(spent), budgets, rows) { it == "Others" }

    @Test
    fun `too little data has no score`() {
        assertNull(score(rows = spends(9)))
        // Categorising alone isn't a score: it needs income or a budget beside it.
        assertNull(score(income = "0"))
    }

    @Test
    fun `parts are weighted and missing ones left out`() {
        // Kept 20%: full savings marks; everything categorised: full marks.
        assertEquals(100, score()!!.score)

        // Kept 10% (50 points, weight 40), half categorised (50, weight 30), one budget on track (100, weight 30).
        val mixed = score(spent = "900", budgets = listOf(budget("500")), rows = spends(10, uncategorised = 5))!!
        assertEquals(PeriodScore.Part.Savings(10, 50), mixed.savings)
        assertEquals(PeriodScore.Part.Budgets(1, 1, 100), mixed.budgets)
        assertEquals(PeriodScore.Part.Categorised(5, 10, 50), mixed.categorised)
        assertEquals(65, mixed.score)
    }

    @Test
    fun `spending past income scores zero for savings, not below`() {
        val result = score(spent = "1500")!!
        assertEquals(-50, result.savings!!.percent)
        assertEquals(0, result.savings!!.points)
        // Savings 0 at weight 40, categorised 100 at weight 30.
        assertEquals(43, result.score)
    }

    @Test
    fun `budgets over pace or in another currency`() {
        val result = score(income = "0", budgets = listOf(budget("2500"), budget("500"), budget("100", currency = "USD")))!!
        assertNull(result.savings)
        // 2,500 in 10 of 30 days is far over pace; the dollar budget isn't this page's.
        assertEquals(PeriodScore.Part.Budgets(1, 2, 50), result.budgets)
        assertEquals(75, result.score)
    }
}
