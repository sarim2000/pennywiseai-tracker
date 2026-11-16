package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.model.BillsDue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningRepositoryTest {
    private val today = LocalDate.of(2026, 11, 16)

    private fun group(
        name: String,
        limit: String,
        spent: String,
        start: LocalDate = LocalDate.of(2026, 11, 1),
        end: LocalDate = LocalDate.of(2026, 11, 30),
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        shown: String = limit,
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(name = name, limitAmount = BigDecimal(limit), periodType = BudgetPeriodType.MONTHLY, startDate = start, endDate = end, groupType = type),
            emptyList()
        ),
        categorySpending = emptyList(),
        totalBudget = BigDecimal(shown),
        totalActual = BigDecimal(spent),
        remaining = BigDecimal(limit) - BigDecimal(spent),
        percentageUsed = 0f,
        dailyAllowance = BigDecimal.ZERO,
        daysRemaining = 0,
        daysElapsed = 0,
        windowStart = start,
        windowEnd = end,
    )

    private fun summary(vararg groups: BudgetGroupSpending, currency: String = "SAR") = BudgetOverallSummary(
        groups = groups.toList(),
        totalIncome = BigDecimal.ZERO,
        totalLimitBudget = BigDecimal("999999"),
        totalLimitSpent = BigDecimal.ZERO,
        totalTargetGoal = BigDecimal.ZERO,
        totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO,
        totalExpectedActual = BigDecimal.ZERO,
        netSavings = BigDecimal.ZERO,
        savingsRate = 0f,
        dailyAllowance = BigDecimal.ZERO,
        daysRemaining = 0,
        currency = currency,
    )

    @Test
    fun `each live limit stands alone, in its own window and the screen's currency`() {
        val weekly = group("Coffee", "100", "130", start = LocalDate.of(2026, 11, 16), end = LocalDate.of(2026, 11, 22))
        val statuses = PlanningRepository.budgetStatuses(
            summary(
                group("Food", "2000", "1500"),
                weekly,
                group("Savings", "5000", "1000", type = BudgetGroupType.TARGET),
                group("Rent", "3000", "3000", type = BudgetGroupType.EXPECTED),
                group("Ended trip", "800", "200", start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 11, 15)),
                group("Nothing set", "0", "50"),
            ),
            today
        )
        assertEquals(listOf("Food", "Coffee"), statuses.map { it.name })
        assertEquals(listOf("SAR", "SAR"), statuses.map { it.currency })
        assertEquals(BigDecimal("500"), statuses[0].remaining)
        // Past the limit reads as negative remaining, not zero.
        assertEquals(BigDecimal("-30"), statuses[1].remaining)
        assertEquals(LocalDate.of(2026, 11, 22), statuses[1].end)
    }

    @Test
    fun `a limit the screen can't convert is kept, without a figure`() {
        // Unified currency with no cached rate: the screen's limit reads zero.
        val statuses = PlanningRepository.budgetStatuses(summary(group("Food", "2000", "150", shown = "0")), today)
        assertEquals(listOf("Food"), statuses.map { it.name })
        assertEquals(null, statuses.single().limit)
        assertEquals(null, statuses.single().remaining)
    }

    @Test
    fun `a window that starts or ends today is live`() {
        val statuses = PlanningRepository.budgetStatuses(
            summary(group("Starts", "10", "0", start = today, end = today.plusDays(6)), group("Ends", "10", "0", start = today.minusDays(6), end = today)),
            today
        )
        assertEquals(listOf("Starts", "Ends"), statuses.map { it.name })
    }

    private fun bill(
        name: String,
        due: LocalDate?,
        currency: String = "SAR",
        direction: SubscriptionDirection = SubscriptionDirection.EXPENSE,
        state: SubscriptionState = SubscriptionState.ACTIVE,
        category: String? = null,
        cycle: String = "Monthly",
    ) = SubscriptionEntity(
        merchantName = name, amount = BigDecimal("10"), nextPaymentDate = due, currency = currency, direction = direction, state = state,
        category = category, billingCycle = cycle,
    )

    private val subscriptions = SubscriptionRepository(mockk(), mockk())

    private fun billsDue(vararg bills: SubscriptionEntity) =
        PlanningRepository.billsDue(bills.toList(), today, 7, incomeCategories = setOf("Salary"), advance = subscriptions::advance)

    @Test
    fun `bills due within the week, overdue ones apart, money in and undated ones left out`() {
        val due = billsDue(
            bill("Gym", today.plusDays(7)),
            bill("Netflix", today, currency = "USD"),
            bill("Rent", today.minusDays(3)),
            bill("Later", today.plusDays(8)),
            bill("Allowance", today.plusDays(1), direction = SubscriptionDirection.INCOME),
            bill("Undated", null),
            bill("Hidden", today.plusDays(1), state = SubscriptionState.HIDDEN),
        )
        assertEquals(today.plusDays(7), due.through)
        assertEquals(listOf("Rent"), due.overdue.map { it.merchantName })
        assertEquals(listOf("Netflix", "Gym"), due.upcoming.map { it.merchantName })
    }

    @Test
    fun `recurring income stored as an expense is no bill`() {
        // Added by hand before income had its own direction: EXPENSE, but filed under an income category.
        val due = billsDue(bill("Salary", today.plusDays(3), category = "Salary"), bill("Phone", today.plusDays(3), category = "Bills"))
        assertEquals(listOf("Phone"), due.upcoming.map { it.merchantName })
    }

    @Test
    fun `a date a whole cycle behind is stale, not overdue`() {
        val due = billsDue(
            // Last charged in June and never matched since: the screen rolls it on, so no "overdue".
            bill("Gym", LocalDate.of(2026, 6, 16)),
            bill("Phone", today.minusMonths(1)),
            // Still within its cycle: really waiting.
            bill("Rent", today.minusDays(20)),
            bill("Coffee club", today.minusDays(6), cycle = "Weekly"),
            bill("Insurance", today.minusMonths(2), cycle = "Yearly"),
            bill("Paper", today.minusDays(7), cycle = "Weekly"),
        )
        assertEquals(listOf("Insurance", "Rent", "Coffee club"), due.overdue.map { it.merchantName })
    }

    @Test
    fun `a stale date is rolled on, and due when its next date falls in the week`() {
        val due = billsDue(
            // Stored 20 Jun, unmatched since: the screen says it's due 20 Nov.
            bill("Gym", LocalDate.of(2026, 6, 20)),
            // Rolls on to 30 Nov: past the week.
            bill("Phone", LocalDate.of(2026, 9, 30)),
            bill("Coffee club", today.minusDays(7), cycle = "Weekly"),
        )
        assertEquals(emptyList<String>(), due.overdue.map { it.merchantName })
        assertEquals(listOf("Coffee club" to today, "Gym" to LocalDate.of(2026, 11, 20)), due.upcoming.map { it.merchantName to it.nextPaymentDate })
    }

    @Test
    fun `a bill paid drops out of the bills due as its date moves on`() = runTest {
        val rows = MutableStateFlow(listOf(bill("Rent", today.minusDays(2)), bill("Gym", today.plusDays(3))))
        val categoryDao = mockk<CategoryDao> { every { getAllCategories() } returns flowOf(emptyList()) }
        val subscriptionDao = mockk<SubscriptionDao> { every { getActiveSubscriptions() } returns rows }
        val planning = PlanningRepository(mockk(), SubscriptionRepository(subscriptionDao, categoryDao), categoryDao)
        val seen = mutableListOf<BillsDue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { planning.observeBillsDue(today).toList(seen) }
        assertEquals(listOf("Rent"), seen.last().overdue.map { it.merchantName })

        // Marked paid: its date moves a cycle on, out of the week.
        rows.value = listOf(bill("Rent", today.minusDays(2).plusMonths(1)), bill("Gym", today.plusDays(3)))
        assertEquals(emptyList<String>(), seen.last().overdue.map { it.merchantName })
        assertEquals(listOf("Gym"), seen.last().upcoming.map { it.merchantName })
    }
}
