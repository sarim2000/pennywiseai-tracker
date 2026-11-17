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
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
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
import com.pennywiseai.tracker.presentation.budgetgroups.historyBudgetTotal
import com.pennywiseai.tracker.presentation.home.homeBudgetSummary
import com.pennywiseai.tracker.utils.Money
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
import org.junit.Assert.assertNull
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
    private fun spend(
        amount: String,
        currency: String = "JOD",
        at: LocalDateTime = today.atTime(9, 0),
        category: String = "Food",
        type: TransactionType = TransactionType.EXPENSE
    ) =
        TransactionWithSplits(
            TransactionEntity(
                id = nextId, amount = BigDecimal(amount), merchantName = "Shop", category = category,
                transactionType = type, dateTime = at, currency = currency,
                transactionHash = "h${nextId++}"
            ),
            emptyList()
        )

    // Groceries is a sub-category of Food.
    private val subCategories = listOf(
        CategoryEntity(id = 1, name = "Food", color = "#000000"),
        CategoryEntity(id = 2, name = "Groceries", color = "#000000", parentId = 1)
    )

    private fun repository(
        group: BudgetWithCategories,
        transactions: List<TransactionWithSplits>,
        categories: List<CategoryEntity> = emptyList()
    ) = repository(group, MutableStateFlow(transactions), categories)

    // Each query re-emits only when its own range changes, so a figure that reads a
    // range it doesn't observe goes stale.
    private fun repository(
        group: BudgetWithCategories,
        transactions: StateFlow<List<TransactionWithSplits>>,
        categoryList: List<CategoryEntity> = emptyList()
    ): BudgetGroupRepository {
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
        val categories = mockk<CategoryDao> { coEvery { getAllCategoriesList() } returns categoryList }
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
            // The widget shows it used up, not its "create a budget" empty state.
            val widget = budgetWidgetData(overall, overall)
            assertTrue(widget.hasLimit)
            assertEquals(100f, widget.percentageUsed)
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
    fun `a parent budget counts its sub-categories this week as it did for last week's carry`() {
        val repo = repository(
            budget(),
            listOf(spend("60", at = lastWeek, category = "Groceries"), spend("30", category = "Groceries")),
            subCategories
        )
        for (unified in listOf(false, true)) {
            assertFigures(summary(repo, unified), carry = "40", limit = "140", remaining = "110")
        }
    }

    @Test
    fun `a budget tracking every expense counts a sub-category's spending once`() {
        val trackAll = budget().copy(categories = emptyList())
        val repo = repository(
            trackAll,
            listOf(spend("60", at = lastWeek, category = "Groceries"), spend("30", category = "Groceries")),
            subCategories
        )
        for (unified in listOf(false, true)) {
            assertFigures(summary(repo, unified), carry = "40", limit = "140", remaining = "110")
        }
    }

    @Test
    fun `a budget tracking every expense counts card purchases this week as it did for last week's carry`() {
        val trackAll = budget().copy(categories = emptyList())
        val repo = repository(
            trackAll,
            listOf(spend("60", at = lastWeek, type = TransactionType.CREDIT), spend("30", type = TransactionType.CREDIT))
        )
        for (unified in listOf(false, true)) {
            assertFigures(summary(repo, unified), carry = "40", limit = "140", remaining = "110")
        }
    }

    @Test
    fun `rollover off carries nothing`() {
        val repo = repository(budget(rollover = false), listOf(spend("60", at = lastWeek), spend("30")))
        assertFigures(summary(repo, unified = false), carry = "0", limit = "100", remaining = "70")
        assertFigures(home(repo), carry = "0", limit = "100", remaining = "70")
    }

    @Test
    fun `a stored rollover on a budget that isn't a spending limit carries nothing`() {
        for (type in listOf(BudgetGroupType.TARGET, BudgetGroupType.EXPECTED)) {
            val group = budget().let { it.copy(budget = it.budget.copy(groupType = type)) }
            val repo = repository(group, listOf(spend("60", at = lastWeek), spend("30")))
            assertFigures(summary(repo, unified = false), carry = "0", limit = "100", remaining = "70")
            assertFigures(summary(repo, unified = true), carry = "0", limit = "100", remaining = "70")
            assertFigures(home(repo), carry = "0", limit = "100", remaining = "70")
        }
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
        assertEquals(CarryGap.NO_RATE, unified.carryGap)
        assertEquals(CarryGap.NO_RATE, home(repo).carryGap)
    }

    @Test
    fun `outside unified mode last week counts only the budget's currency, as its spend does`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("60", at = lastWeek), spend("20", "USD", lastWeek), spend("30")))
        val native = summary(repo, unified = false)
        assertFigures(native, carry = "40", limit = "140", remaining = "110")
        assertNull(native.carryGap)
        val thisWeek = repo.getBudgetHistoryForMonth(group.budget, today.year, today.monthValue, "JOD")
            .single { it.window.start == today }
        assertEquals(0, BigDecimal("40").compareTo(thisWeek.carry!!.amount.amount))
        assertFalse(thisWeek.carry!!.incomplete)
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, resolveBudgetWindow(group.budget, today, 1), "JOD")
        assertEquals(0, BigDecimal("140").compareTo(breakdown.totalBudget))
    }

    @Test
    fun `budget history shows the week's carry and adds it to the window's budget`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("60", at = lastWeek), spend("30")))
        val history = repo.getBudgetHistoryForMonth(group.budget, today.year, today.monthValue, "JOD")
        val thisWeek = history.single { it.window.start == today }
        assertEquals(0, BigDecimal("40").compareTo(thisWeek.carry!!.amount.amount))
        assertFalse(thisWeek.carry!!.incomplete)
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, resolveBudgetWindow(group.budget, today, 1), "JOD")
        assertEquals(0, BigDecimal("140").compareTo(breakdown.totalBudget))
        // The sheet shows the base and the carry behind that figure.
        assertEquals(0, BigDecimal("40").compareTo(breakdown.carry!!.amount.amount))
        assertTrue(breakdown.hasLimit)
    }

    @Test
    fun `budget history's breakdown keeps a limit carried down to 0 as a limit`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("250", at = lastWeek), spend("30")))
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, resolveBudgetWindow(group.budget, today, 1), "JOD")
        assertEquals(0, BigDecimal.ZERO.compareTo(breakdown.totalBudget))
        assertEquals(0, BigDecimal("-100").compareTo(breakdown.carry!!.amount.amount))
        assertTrue(breakdown.hasLimit)
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

    @Test
    fun `in unified mode budget history counts this week's other currencies like the card`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("60", at = lastWeek), spend("30"), spend("20", "USD")))
        val convert: suspend (String, BigDecimal) -> BigDecimal? = { from, amount -> conversion.convertAmountOrNull(amount, from, "JOD") }
        val card = summary(repo, unified = true)
        assertFigures(card, carry = "40", limit = "140", remaining = "100")
        val thisWeek = repo.getBudgetHistoryForMonth(group.budget, today.year, today.monthValue, "JOD", convert)
            .single { it.window.start == today }
        assertEquals(0, card.totalActual.compareTo(thisWeek.spent))
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, resolveBudgetWindow(group.budget, today, 1), "JOD", convert)
        assertEquals(0, card.totalActual.compareTo(breakdown.totalActual))
        assertEquals(0, card.totalActual.compareTo(breakdown.categorySpending.single().actualAmount))
        assertEquals(0, card.remaining.compareTo(breakdown.totalBudget - breakdown.totalActual))
        // Outside unified mode the USD purchase is in neither the card nor the history.
        val native = summary(repo, unified = false)
        val nativeWeek = repo.getBudgetHistoryForMonth(group.budget, today.year, today.monthValue, "JOD")
            .single { it.window.start == today }
        assertEquals(0, BigDecimal("30").compareTo(native.totalActual))
        assertEquals(0, native.totalActual.compareTo(nativeWeek.spent))
    }

    @Test
    fun `budget history carries nothing into weeks that haven't started`() = runBlocking {
        val group = budget()
        val repo = repository(group, listOf(spend("60", at = lastWeek), spend("30")))
        val next = YearMonth.from(today).plusMonths(1)
        val history = repo.getBudgetHistoryForMonth(group.budget, next.year, next.monthValue, "JOD")
        assertTrue(history.isNotEmpty())
        history.forEach { assertNull(it.carry) }
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, history.first().window, "JOD")
        assertEquals(0, BigDecimal("100").compareTo(breakdown.totalBudget))
        assertNull(breakdown.carry)
    }

    @Test
    fun `budget history's month total adds only what carried in from before the month`() {
        val group = budget().budget
        fun week(start: LocalDate, carry: String) = PastWindowSpending(
            window = BudgetWindow(start, start.plusDays(6), 7),
            spent = BigDecimal.ZERO,
            carry = BudgetCarry(Money(BigDecimal(carry), "JOD"))
        )
        val start = LocalDate.of(2026, 11, 2)
        // Week 2's +100 and week 3's +70 are weeks 1 and 2's own leftovers: already in their limits.
        val history = listOf(week(start, "40"), week(start.plusDays(7), "100"), week(start.plusDays(14), "70"))
        assertEquals(0, BigDecimal("340").compareTo(historyBudgetTotal(group, history)))
    }
}
