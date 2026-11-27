package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Moving a budget up or down is display only, so it keeps `updatedAt`: the under-budget
 * card reads a later `updatedAt` as a limit that may have changed since the cycle ended.
 */
class BudgetReorderTest {

    private val edited = LocalDateTime.of(2026, 9, 15, 10, 0)

    private fun budget(id: Long, order: Int) = BudgetEntity(
        id = id, name = "Budget $id", limitAmount = BigDecimal("100"), periodType = BudgetPeriodType.MONTHLY,
        startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 30),
        currency = "USD", groupType = BudgetGroupType.LIMIT, displayOrder = order,
        createdAt = edited, updatedAt = edited,
    )

    private fun reorder(move: suspend BudgetGroupRepository.() -> Unit): List<BudgetEntity> {
        val written = mutableListOf<BudgetEntity>()
        val dao = mockk<BudgetDao> {
            every { getActiveBudgets() } returns flowOf(listOf(budget(1, 0), budget(2, 1)))
            coEvery { updateBudget(any()) } answers { written.add(firstArg()) }
        }
        val repo = BudgetGroupRepository(dao, mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        runBlocking { repo.move() }
        return written
    }

    @Test
    fun `moving a budget up swaps the order and keeps updatedAt`() {
        val written = reorder { moveGroupUp(2) }
        assertEquals(mapOf(2L to 0, 1L to 1), written.associate { it.id to it.displayOrder })
        assertEquals(listOf(edited, edited), written.map { it.updatedAt })
    }

    @Test
    fun `moving a budget down swaps the order and keeps updatedAt`() {
        val written = reorder { moveGroupDown(1) }
        assertEquals(mapOf(1L to 1, 2L to 0), written.associate { it.id to it.displayOrder })
        assertEquals(listOf(edited, edited), written.map { it.updatedAt })
    }
}
