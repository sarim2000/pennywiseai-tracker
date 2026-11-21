package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.BudgetPreferences
import com.pennywiseai.tracker.presentation.budgetgroups.historyBudgetTotal
import com.pennywiseai.tracker.presentation.budgetgroups.historyHeaderWindows
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Budget History for a monthly budget that starts on the 5th: every cycle is listed
 * under some month, and the header sets the limit against one cycle.
 */
class BudgetHistoryCycleTest {

    private val group = BudgetWithCategories(
        BudgetEntity(
            id = 1, name = "Food", limitAmount = BigDecimal("100"), periodType = BudgetPeriodType.MONTHLY,
            startDate = LocalDate.of(2026, 9, 5), endDate = LocalDate.of(2026, 10, 4), monthStartDay = 5,
            currency = "JOD", groupType = BudgetGroupType.LIMIT
        ),
        listOf(BudgetCategoryEntity(budgetId = 1, categoryName = "Food", budgetAmount = BigDecimal("100")))
    )

    private val transactions = listOf(
        spend(1, "50", LocalDateTime.of(2026, 10, 10, 12, 0)),
        spend(2, "30", LocalDateTime.of(2026, 11, 10, 12, 0))
    )

    private fun spend(id: Long, amount: String, at: LocalDateTime) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Shop", category = "Food",
            transactionType = TransactionType.EXPENSE, dateTime = at, currency = "JOD", transactionHash = "h$id"
        ),
        emptyList()
    )

    private val repo: BudgetGroupRepository = run {
        fun between(start: LocalDateTime, end: LocalDateTime) =
            flowOf(transactions.filter { !it.transaction.dateTime.isBefore(start) && !it.transaction.dateTime.isAfter(end) })
        val budgetDao = mockk<BudgetDao> { every { getActiveBudgetsWithCategories() } returns flowOf(listOf(group)) }
        val splitDao = mockk<TransactionSplitDao> {
            every { getTransactionsWithSplitsAllCurrencies(any(), any()) } answers { between(firstArg(), secondArg()) }
            every { getTransactionsWithSplitsFiltered(any(), any(), any()) } answers { between(firstArg(), secondArg()) }
        }
        val prefs = mockk<BudgetPreferences> {
            every { budgetCycleStartDay } returns flowOf(1)
            coEvery { getBudgetCycleStartDay() } returns 1
        }
        val categories = mockk<CategoryDao> { coEvery { getAllCategoriesList() } returns emptyList() }
        BudgetGroupRepository(budgetDao, splitDao, prefs, categories)
    }

    private fun history(month: Int, today: LocalDate) =
        runBlocking { repo.getBudgetHistoryForMonth(group.budget, 2026, month, "JOD", today = today) }

    @Test
    fun `after the start day the cycle that just ended stays listed beside the running one`() {
        val today = LocalDate.of(2026, 11, 20)
        val november = history(11, today)

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 5) to LocalDate.of(2026, 12, 4)
            ),
            november.map { it.window.start to it.window.end }
        )
        assertEquals(0, BigDecimal("50").compareTo(november[0].spent))
        assertEquals(0, BigDecimal("30").compareTo(november[1].spent))
        assertFalse(november[0].isLive)
        assertTrue(november[1].isLive)
        // October lists only the cycle holding its 1st, so Oct 5..Nov 4 is under November alone.
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5) to LocalDate.of(2026, 10, 4)),
            history(10, today).map { it.window.start to it.window.end }
        )
        // The header sets one limit against the running cycle, not against both.
        val header = historyHeaderWindows(group.budget, november)
        assertEquals(0, BigDecimal("30").compareTo(header.sumOf { it.spent }))
        assertEquals(0, BigDecimal("100").compareTo(historyBudgetTotal(group.budget, header)))
    }

    @Test
    fun `an ended cycle is frozen as of its own end, not the month end`() {
        val today = LocalDate.of(2026, 11, 20)

        assertEquals(LocalDate.of(2026, 11, 4), history(11, today)[0].capDate)
        assertEquals(LocalDate.of(2026, 10, 4), history(10, today).single().capDate)
    }

    @Test
    fun `before the start day the running cycle is listed once and is live`() {
        val november = history(11, LocalDate.of(2026, 11, 3))

        assertEquals(1, november.size)
        assertEquals(LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 11, 4), november.single().window.let { it.start to it.end })
        assertTrue(november.single().isLive)
        assertEquals(0, BigDecimal("50").compareTo(november.single().spent))
    }
}
