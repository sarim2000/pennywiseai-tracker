package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
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
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.domain.insights.InsightFact
import com.pennywiseai.tracker.domain.insights.InsightRules
import com.pennywiseai.tracker.domain.insights.budgetStatuses
import com.pennywiseai.tracker.domain.spending.BudgetAlertTier
import com.pennywiseai.tracker.domain.spending.MoneyAlertRules
import com.pennywiseai.tracker.presentation.home.homeBudgetSummary
import com.pennywiseai.tracker.widget.budgetWidgetData
import com.pennywiseai.tracker.worker.DailySummaryWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Every figure a rolled-over budget feeds (the Budgets screen in both currency
 * modes, Home, the widget, alerts, the digest, Insights, Budget History) gets
 * the same carry from last week.
 */
class BudgetRolloverRoutingTest {

    // A weekly budget whose week starts today, so last week is the 7 days before.
    private val today = LocalDate.now()
    private val lastWeek = today.minusDays(3).atTime(12, 0)

    private fun budget(rollover: Boolean = true) = BudgetWithCategories(
        BudgetEntity(
            id = 1, name = "Food", limitAmount = BigDecimal("100"), periodType = BudgetPeriodType.WEEKLY,
            startDate = today, endDate = today.plusDays(6), weekStartDay = today.dayOfWeek.value,
            currency = "JOD", groupType = BudgetGroupType.LIMIT,
            rolloverEnabled = rollover, rolloverSince = today.minusDays(30)
        ),
        listOf(BudgetCategoryEntity(budgetId = 1, categoryName = "Food", budgetAmount = BigDecimal("100")))
    )

    private var nextId = 1L
    private fun spend(amount: String, currency: String = "JOD", at: LocalDateTime = today.atTime(9, 0)) =
        TransactionWithSplits(
            TransactionEntity(
                id = nextId, amount = BigDecimal(amount), merchantName = "Shop", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = at, currency = currency,
                transactionHash = "h${nextId++}"
            ),
            emptyList()
        )

    private fun repository(group: BudgetWithCategories, transactions: List<TransactionWithSplits>) =
        repository(group, MutableStateFlow(transactions))

    // Each query re-emits only when its own range changes, so a figure that reads a
    // range it doesn't observe goes stale.
    private fun repository(group: BudgetWithCategories, transactions: StateFlow<List<TransactionWithSplits>>): BudgetGroupRepository {
        fun between(start: LocalDateTime, end: LocalDateTime) = transactions.map { all ->
            all.filter { !it.transaction.dateTime.isBefore(start) && !it.transaction.dateTime.isAfter(end) }
        }.distinctUntilChanged()
        val budgetDao = mockk<BudgetDao> {
            every { getActiveBudgetsWithCategories() } returns flowOf(listOf(group))
        }
        val splitDao = mockk<TransactionSplitDao> {
            every { getTransactionsWithSplitsAllCurrencies(any(), any()) } answers { between(firstArg(), secondArg()) }
            every { getTransactionsWithSplitsFiltered(any(), any(), any()) } answers {
                val currency = thirdArg<String>()
                between(firstArg(), secondArg()).map { list -> list.filter { it.transaction.currency == currency } }
            }
        }
        val prefs = mockk<BudgetPreferences> {
            every { budgetCycleStartDay } returns flowOf(1)
            coEvery { getBudgetCycleStartDay() } returns 1
        }
        val categories = mockk<CategoryDao> { coEvery { getAllCategoriesList() } returns emptyList() }
        return BudgetGroupRepository(budgetDao, splitDao, prefs, categories)
    }

    // USD converts at half; XYZ has never had a rate.
    private val conversion = mockk<CurrencyConversionService> {
        coEvery { convertAmountOrNull(any(), any(), any(), any()) } answers {
            when (secondArg<String>()) {
                "XYZ" -> null
                "USD" -> firstArg<BigDecimal>().divide(BigDecimal(2))
                else -> firstArg()
            }
        }
    }

    private fun overall(repo: BudgetGroupRepository, unified: Boolean): BudgetOverallSummary = runBlocking {
        val prefs = mockk<CurrencyPreferences> {
            every { baseCurrency } returns flowOf("JOD")
            every { unifiedCurrencyMode } returns flowOf(unified)
            every { displayCurrency } returns flowOf("JOD")
        }
        BudgetSummaryProvider(repo, prefs, conversion).summary(YearMonth.from(today)).first()
    }

    private fun summary(repo: BudgetGroupRepository, unified: Boolean): BudgetGroupSpending =
        overall(repo, unified).groups.single()

    private fun home(repo: BudgetGroupRepository): BudgetGroupSpending = runBlocking {
        val raw = repo.getGroupSpendingAllCurrencies(today.year, today.monthValue).first()
        homeBudgetSummary(raw, "JOD", "JOD") { amount, from, to -> conversion.convertAmountOrNull(amount, from, to) }
            .groups.single()
    }

    private fun assertFigures(g: BudgetGroupSpending, carry: String, limit: String, remaining: String) {
        assertEquals(0, BigDecimal(carry).compareTo(g.carry))
        assertEquals(0, BigDecimal(limit).compareTo(g.totalBudget))
        assertEquals(0, BigDecimal(remaining).compareTo(g.remaining))
    }

    @Test
    fun `last week's leftover raises this week's limit everywhere`() {
        val repo = repository(budget(), listOf(spend("60", at = lastWeek), spend("30")))
        assertFigures(summary(repo, unified = false), carry = "40", limit = "140", remaining = "110")
        assertFigures(summary(repo, unified = true), carry = "40", limit = "140", remaining = "110")
        assertFigures(home(repo), carry = "40", limit = "140", remaining = "110")
        for (unified in listOf(false, true)) {
            val widget = budgetWidgetData(overall(repo, unified), overall(repo, unified))
            assertEquals(0, BigDecimal("140").compareTo(widget.totalLimit))
            assertEquals(0, BigDecimal("110").compareTo(widget.remaining))
        }
    }

    @Test
    fun `an overspend lowers this week's limit everywhere`() {
        val repo = repository(budget(), listOf(spend("130", at = lastWeek), spend("30")))
        assertFigures(summary(repo, unified = false), carry = "-30", limit = "70", remaining = "40")
        assertFigures(summary(repo, unified = true), carry = "-30", limit = "70", remaining = "40")
        assertFigures(home(repo), carry = "-30", limit = "70", remaining = "40")
    }

    @Test
    fun `overspending last week twice over uses up this week's limit, and it still counts as one`() {
        val repo = repository(budget(), listOf(spend("250", at = lastWeek), spend("30")))
        for (g in listOf(summary(repo, unified = false), summary(repo, unified = true), home(repo))) {
            assertFigures(g, carry = "-100", limit = "0", remaining = "-30")
            assertTrue(g.hasLimit)
            assertEquals(100f, g.percentageUsed)
            assertEquals(
                BudgetAlertTier.OVER,
                MoneyAlertRules.budgetTier(g.totalActual, g.totalBudget, g.windowStart, g.windowEnd, today, g.hasLimit)
            )
        }
        for (unified in listOf(false, true)) {
            val overall = overall(repo, unified)
            assertEquals(
                listOf(DailySummaryWorker.BudgetLeft("Food", BigDecimal("-30"), incomplete = false)),
                DailySummaryWorker.budgetsLeft(overall, today)
            )
            assertTrue(InsightRules.budgets(budgetStatuses(overall, today), "p").single().fact is InsightFact.BudgetOver)
        }
    }

    @Test
    fun `a transaction landing in last week afterwards updates this week's carry`() = runBlocking {
        val transactions = MutableStateFlow(listOf(spend("60", at = lastWeek), spend("30")))
        val repo = repository(budget(), transactions)
        val native = repo.getGroupSpending(today.year, today.monthValue, "JOD").map { it.groups.single().carry }
        val raw = repo.getGroupSpendingAllCurrencies(today.year, today.monthValue).map { raw ->
            raw.carryFor(raw.budgetsWithCategories.single(), "JOD", "JOD") { _, amount -> amount }.amount.amount
        }
        for (carries in listOf(native, raw)) {
            transactions.value = listOf(spend("60", at = lastWeek), spend("30"))
            val seen = Channel<BigDecimal>(Channel.UNLIMITED)
            val collecting = launch { carries.collect { seen.send(it) } }
            assertEquals(0, BigDecimal("40").compareTo(withTimeout(5_000) { seen.receive() }))
            transactions.value = transactions.value + spend("25", at = lastWeek)
            withTimeout(5_000) { while (BigDecimal("15").compareTo(seen.receive()) != 0) Unit }
            collecting.cancel()
        }
    }

    @Test
    fun `rollover off carries nothing`() {
        val repo = repository(budget(rollover = false), listOf(spend("60", at = lastWeek), spend("30")))
        assertFigures(summary(repo, unified = false), carry = "0", limit = "100", remaining = "70")
        assertFigures(home(repo), carry = "0", limit = "100", remaining = "70")
    }

    @Test
    fun `unified mode converts last week's other currencies at today's rate`() {
        val repo = repository(budget(), listOf(spend("60", at = lastWeek), spend("20", "USD", lastWeek), spend("30")))
        assertFigures(summary(repo, unified = true), carry = "30", limit = "130", remaining = "100")
        assertFigures(home(repo), carry = "30", limit = "130", remaining = "100")
    }

    @Test
    fun `spending last week with no rate carries nothing and says so`() {
        val repo = repository(budget(), listOf(spend("60", at = lastWeek), spend("20", "XYZ", lastWeek), spend("30")))
        val unified = summary(repo, unified = true)
        assertFigures(unified, carry = "0", limit = "100", remaining = "70")
        assertTrue(unified.carryIncomplete)
        assertTrue(home(repo).carryIncomplete)
    }

    @Test
    fun `outside unified mode another currency last week makes the carry incomplete`() {
        val repo = repository(budget(), listOf(spend("60", at = lastWeek), spend("20", "USD", lastWeek), spend("30")))
        val native = summary(repo, unified = false)
        assertFigures(native, carry = "0", limit = "100", remaining = "70")
        assertTrue(native.carryIncomplete)
    }

    @Test
    fun `budget history shows the week's carry and adds it to the period's budget`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("60", at = lastWeek), spend("30")))
        val history = repo.getBudgetHistoryForMonth(group.budget, today.year, today.monthValue, "JOD")
        val thisWeek = history.single { it.window.start == today }
        assertEquals(0, BigDecimal("40").compareTo(thisWeek.carry!!.amount.amount))
        assertFalse(thisWeek.carry!!.incomplete)
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, resolveBudgetWindow(group.budget, today, 1), "JOD")
        assertEquals(0, BigDecimal("140").compareTo(breakdown.totalBudget))
    }

    @Test
    fun `in unified mode budget history converts last week's other currencies like the card`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("60", at = lastWeek), spend("20", "USD", lastWeek), spend("30")))
        val convert: suspend (String, BigDecimal) -> BigDecimal? = { from, amount -> conversion.convertAmountOrNull(amount, from, "JOD") }
        val history = repo.getBudgetHistoryForMonth(group.budget, today.year, today.monthValue, "JOD", convert)
        val thisWeek = history.single { it.window.start == today }
        assertEquals(0, BigDecimal("30").compareTo(thisWeek.carry!!.amount.amount))
        assertFalse(thisWeek.carry!!.incomplete)
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, resolveBudgetWindow(group.budget, today, 1), "JOD", convert)
        assertEquals(0, BigDecimal("130").compareTo(breakdown.totalBudget))
        assertFigures(summary(repo, unified = true), carry = "30", limit = "130", remaining = "100")
    }
}
