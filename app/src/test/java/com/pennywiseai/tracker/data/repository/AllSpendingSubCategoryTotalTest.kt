package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.BudgetPreferences
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** A sub-category's spend counts once toward a budget's total, in both currency modes. */
class AllSpendingSubCategoryTotalTest {

    private val today = LocalDate.now()

    private fun budget(categories: List<String> = emptyList()) = BudgetWithCategories(
        BudgetEntity(
            id = 1, name = "All", limitAmount = BigDecimal("1000"), periodType = BudgetPeriodType.WEEKLY,
            startDate = today, endDate = today.plusDays(6), weekStartDay = today.dayOfWeek.value,
            currency = "INR", groupType = BudgetGroupType.LIMIT
        ),
        categories.map { BudgetCategoryEntity(budgetId = 1, categoryName = it, budgetAmount = BigDecimal("1000")) }
    )

    private fun tx(id: Long, amount: String, type: TransactionType = TransactionType.EXPENSE) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Shop", category = "Snacks",
            transactionType = type, dateTime = today.atTime(9, 0), currency = "INR", transactionHash = "h$id"
        ),
        emptyList()
    )

    private val snacks = tx(1, "100")

    // A ₹30 refund filed under Snacks.
    private val refund = tx(2, "30", TransactionType.INCOME).let {
        it.copy(transaction = it.transaction.copy(budgetCategory = "Snacks", budgetImpactType = BudgetImpactType.DEDUCT_SPENT))
    }

    private fun spent(group: BudgetWithCategories, txs: List<TransactionWithSplits>, unified: Boolean): BigDecimal = runBlocking {
        val repo = BudgetGroupRepository(
            mockk<BudgetDao> { every { getActiveBudgetsWithCategories() } returns flowOf(listOf(group)) },
            mockk<TransactionSplitDao> {
                every { getTransactionsWithSplitsAllCurrencies(any(), any()) } returns flowOf(txs)
                every { getTransactionsWithSplitsFiltered(any(), any(), any()) } returns flowOf(txs)
            },
            mockk<BudgetPreferences> {
                every { budgetCycleStartDay } returns flowOf(1)
                coEvery { getBudgetCycleStartDay() } returns 1
            },
            // Snacks is a sub-category of Food.
            mockk<CategoryDao> {
                coEvery { getAllCategoriesList() } returns listOf(
                    CategoryEntity(id = 1, name = "Food", color = "#000000"),
                    CategoryEntity(id = 2, name = "Snacks", color = "#000000", parentId = 1)
                )
            }
        )
        val conversion = mockk<CurrencyConversionService> {
            coEvery { convertAmountOrNull(any(), any(), any(), any()) } answers { firstArg() }
            every { getAllRatesFlow() } returns flowOf(emptyList())
        }
        val prefs = mockk<CurrencyPreferences> {
            every { baseCurrency } returns flowOf("INR")
            every { unifiedCurrencyMode } returns flowOf(unified)
            every { displayCurrency } returns flowOf("INR")
        }
        BudgetSummaryProvider(repo, prefs, conversion).summary(YearMonth.from(today)).first().groups.single().totalActual
    }

    private fun assertSpent(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `sub-category spend counts once against an all-spending budget`() {
        assertSpent("100", spent(budget(), listOf(snacks), unified = false))
        assertSpent("100", spent(budget(), listOf(snacks), unified = true))
    }

    @Test
    fun `a sub-category refund comes off an all-spending budget once`() {
        assertSpent("70", spent(budget(), listOf(snacks, refund), unified = false))
    }

    @Test
    fun `a parent-category budget still counts its sub-category's spend`() {
        assertSpent("100", spent(budget(listOf("Food")), listOf(snacks), unified = false))
    }
}
