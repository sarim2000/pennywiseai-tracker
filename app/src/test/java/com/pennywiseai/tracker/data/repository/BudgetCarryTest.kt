package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetCarryTest {

    private val day = LocalDate.of(2026, 11, 10)

    private fun group(limit: String, vararg categories: Pair<String, String>) = BudgetWithCategories(
        BudgetEntity(
            id = 1, name = "B", limitAmount = BigDecimal(limit), periodType = BudgetPeriodType.WEEKLY,
            startDate = day, endDate = day.plusDays(6)
        ),
        categories.map { (name, amount) -> BudgetCategoryEntity(budgetId = 1, categoryName = name, budgetAmount = BigDecimal(amount)) }
    )

    private var id = 1L
    private fun tx(
        amount: String,
        category: String = "Food",
        currency: String = "JOD",
        type: TransactionType = TransactionType.EXPENSE,
        refundOf: String? = null,
    ) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "M", category = category,
            transactionType = type, dateTime = day.atTime(10, 0), currency = currency, transactionHash = "h${id++}",
            budgetCategory = refundOf, budgetImpactType = refundOf?.let { BudgetImpactType.DEDUCT_SPENT }
        ),
        emptyList()
    )

    private val sameCurrency: suspend (String, BigDecimal) -> BigDecimal? = { from, amount -> amount.takeIf { from == "JOD" } }

    private fun carry(
        group: BudgetWithCategories,
        previous: List<TransactionWithSplits>?,
        parentOf: Map<String, String> = emptyMap(),
    ) = runBlocking { budgetCarry(group, previous, "JOD", "JOD", parentOf, sameCurrency) }

    @Test
    fun `extra budget raises a category-limits budget's limit for the carry, as it does on its card`() {
        // No overall limit: Food 1000, plus 500 of extra budget last period, 1400 spent.
        val boost = TransactionWithSplits(
            TransactionEntity(
                id = id++, amount = BigDecimal("500"), merchantName = "M", category = "Food",
                transactionType = TransactionType.INCOME, dateTime = day.atTime(9, 0), currency = "JOD",
                transactionHash = "boost", budgetCategory = "Food", budgetImpactType = BudgetImpactType.ADD_TO_LIMIT
            ),
            emptyList()
        )
        assertEquals(Money(BigDecimal("100"), "JOD"), carry(group("0", "Food" to "1000"), listOf(boost, tx("1400"))).amount)
        // With an overall limit the card ignores boosts, so the carry does too.
        assertEquals(Money(BigDecimal("-400"), "JOD"), carry(group("1000", "Food" to "1000"), listOf(boost, tx("1400"))).amount)
        // An overspend past limit and boost carries at most one limit: this period starts at 0, not below.
        assertEquals(Money(BigDecimal("-1000"), "JOD"), carry(group("0", "Food" to "1000"), listOf(boost, tx("3000"))).amount)
    }

    @Test
    fun `a refund filed under a parent comes off a track-all budget's spend once`() {
        // 80 on Groceries, a 30 refund filed under its parent Food: 50 spent of 100.
        val previous = listOf(tx("80", category = "Groceries"), tx("30", category = "Food", type = TransactionType.INCOME, refundOf = "Food"))
        val c = carry(group("100"), previous, parentOf = mapOf("Groceries" to "Food"))
        assertEquals(Money(BigDecimal("50"), "JOD"), c.amount)
    }

    @Test
    fun `no source period carries nothing`() {
        assertEquals(BudgetCarry.none("JOD"), carry(group("100"), null))
    }

    @Test
    fun `a budget with no categories counts every expense, less categorised refunds`() {
        val c = carry(group("100"), listOf(tx("50", "Food"), tx("30", "Fuel"), tx("10", type = TransactionType.INCOME, refundOf = "Food")))
        assertEquals(Money(BigDecimal("30"), "JOD"), c.amount)
        assertFalse(c.incomplete)
    }

    @Test
    fun `a budget counts only its own categories, a parent including its children`() {
        val c = carry(
            group("0", "Food" to "80"),
            listOf(tx("20", "Groceries"), tx("30", "Food"), tx("500", "Rent")),
            parentOf = mapOf("Groceries" to "Food")
        )
        // No group limit: the categories' 80 is the base.
        assertEquals(0, BigDecimal("30").compareTo(c.amount.amount))
    }

    @Test
    fun `spending this budget counts that has no rate makes the carry incomplete`() {
        val c = carry(group("100", "Food" to "100"), listOf(tx("20", "Food"), tx("5", "Food", "USD")))
        assertTrue(c.incomplete)
        assertEquals(0, BigDecimal.ZERO.compareTo(c.amount.amount))
    }

    @Test
    fun `spending outside the budget with no rate doesn't`() {
        val c = carry(group("100", "Food" to "100"), listOf(tx("20", "Food"), tx("5", "Rent", "USD")))
        assertFalse(c.incomplete)
        assertEquals(0, BigDecimal("80").compareTo(c.amount.amount))
    }

    @Test
    fun `the carry is added onto the limit it's applied to`() {
        assertEquals(0, BigDecimal("125").compareTo(BudgetCarry(Money(BigDecimal("25"), "JOD")).appliedTo(BigDecimal("100"))))
    }
}
