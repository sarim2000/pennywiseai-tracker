package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetCurrencyMoveTest {

    private val dao = mockk<BudgetDao>(relaxed = true)
    private val repository = BudgetGroupRepository(dao, mockk(), mockk(), mockk())

    private fun budget(id: Long, limit: String, currency: String) = BudgetEntity(
        id = id,
        name = "Budget $id",
        limitAmount = BigDecimal(limit),
        periodType = BudgetPeriodType.MONTHLY,
        startDate = LocalDate.of(2026, 11, 1),
        endDate = LocalDate.of(2026, 11, 30),
        currency = currency
    )

    private fun moved(limit: String, category: String, rate: BigDecimal?, to: String = "INR"): Pair<BudgetEntity, BudgetCategoryEntity> {
        coEvery { dao.getAllBudgets() } returns flowOf(listOf(budget(1, limit, "JOD")))
        coEvery { dao.getAllBudgetCategories() } returns flowOf(
            listOf(BudgetCategoryEntity(id = 9, budgetId = 1, categoryName = "Food", budgetAmount = BigDecimal(category)))
        )
        lateinit var budgets: List<BudgetEntity>
        lateinit var categories: List<BudgetCategoryEntity>
        coEvery { dao.updateBudgetsAndCategories(any(), any()) } answers {
            budgets = firstArg()
            categories = secondArg()
        }
        runBlocking { repository.moveBudgetsToCurrency(to, rate) }
        return budgets.single() to categories.single()
    }

    @Test
    fun `converting multiplies the limit and its categories by the rate`() {
        val (budget, category) = moved("360", "60", BigDecimal("88.6"))
        assertEquals(BigDecimal("31896.00"), budget.limitAmount)
        assertEquals("INR", budget.currency)
        assertEquals(BigDecimal("5316.00"), category.budgetAmount)
    }

    @Test
    fun `a limit moving into a three-decimal currency keeps its fils`() {
        // Two decimals used to round 3.075 to 3.08.
        val (budget, _) = moved("100", "5", BigDecimal("0.03075"), to = "KWD")
        assertEquals(BigDecimal("3.075"), budget.limitAmount)
    }

    @Test
    fun `keeping the numbers only moves the budget's label`() {
        val (budget, category) = moved("360", "60", rate = null)
        assertEquals(BigDecimal("360"), budget.limitAmount)
        assertEquals("INR", budget.currency)
        assertEquals(BigDecimal("60"), category.budgetAmount)
    }

    @Test
    fun `a small limit moving to a far stronger currency isn't zeroed`() {
        val (budget, category) = moved("300", "100", BigDecimal("0.00001"))
        assertEquals(0, BigDecimal("0.003").compareTo(budget.limitAmount))
        assertEquals(0, BigDecimal("0.001").compareTo(category.budgetAmount))
    }

    @Test
    fun `an unset limit stays unset, since zero means use the category sum`() {
        val (budget, _) = moved("0", "60", BigDecimal("88.6"))
        assertEquals(0, BigDecimal.ZERO.compareTo(budget.limitAmount))
    }

    @Test
    fun `only active budgets are worth asking about`() = runBlocking {
        coEvery { dao.getActiveBudgets() } returns flowOf(emptyList())
        assertFalse(repository.hasActiveBudgets())
        coEvery { dao.getActiveBudgets() } returns flowOf(listOf(budget(1, "360", "JOD")))
        assertTrue(repository.hasActiveBudgets())
    }
}
