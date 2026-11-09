package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeSpendTrendTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun expense(amount: String, date: LocalDate, currency: String = "INR") =
        tx(amount, TransactionType.EXPENSE, currency = currency, date = date)

    /** A calendar-month cycle: this one starts Mar 1, the previous Feb 1. */
    private suspend fun trend(
        rows: List<TransactionEntity>,
        today: LocalDate,
        currency: String = "INR",
        isUnified: Boolean = false,
        convert: ConvertAmount = fakeConvert(),
    ) = spendTrend(
        rows,
        cycleStart = d(3, 1),
        previousCycleStart = d(2, 1),
        today = today,
        currency = currency,
        isUnified = isUnified,
        convert = convert,
    )

    @Test fun `this cycle has one point per day from cycle start through today`() = runTest {
        val t = trend(listOf(expense("100", d(3, 2)), expense("50", d(3, 4))), today = d(3, 5))
        assertAmounts(listOf("0", "100", "100", "150", "150"), t.thisCycle)
    }

    @Test fun `refund before the first expense still counts but the line never dips below zero`() = runTest {
        val t = trend(
            listOf(
                tx("30", TransactionType.INCOME, date = d(3, 1), impact = BudgetImpactType.DEDUCT_SPENT),
                expense("100", d(3, 3)),
            ),
            today = d(3, 3),
        )
        assertAmounts(listOf("0", "0", "70"), t.thisCycle)
    }

    @Test fun `income that isn't a refund doesn't move the line`() = runTest {
        val t = trend(listOf(tx("500", TransactionType.INCOME, date = d(3, 1)), expense("20", d(3, 2))), today = d(3, 2))
        assertAmounts(listOf("0", "20"), t.thisCycle)
    }

    @Test fun `last cycle is cut at today's day-of-cycle`() = runTest {
        val t = trend(listOf(expense("40", d(2, 2)), expense("99", d(2, 10))), today = d(3, 3))
        assertAmounts(listOf("0", "40", "40"), t.lastCycleSamePoint)
    }

    @Test fun `last cycle shorter than the elapsed days stops at its end`() = runTest {
        val t = trend(listOf(expense("40", d(2, 28))), today = d(3, 31))
        assertEquals(31, t.thisCycle.size)
        assertEquals(28, t.lastCycleSamePoint.size)
        assertAmount("40", t.lastCycleSamePoint.last())
    }

    @Test fun `native mode draws only the selected currency`() = runTest {
        val t = trend(listOf(expense("100", d(3, 1)), expense("10", d(3, 1), currency = "USD")), today = d(3, 1))
        assertAmounts(listOf("100"), t.thisCycle)
    }

    @Test fun `unified mode converts and skips a never-rated currency`() = runTest {
        val t = trend(
            listOf(
                expense("100", d(3, 1)),
                expense("10", d(3, 1), currency = "USD"),
                expense("5", d(3, 1), currency = "XYZ"),
                expense("2", d(2, 1), currency = "USD"),
            ),
            today = d(3, 1),
            isUnified = true,
            convert = fakeConvert(mapOf("USD>INR" to "83")),
        )
        assertAmounts(listOf("930"), t.thisCycle)
        assertAmounts(listOf("166"), t.lastCycleSamePoint)
    }

    @Test fun `excluded and loan rows are ignored`() = runTest {
        val t = trend(
            listOf(
                tx("999", TransactionType.EXPENSE, date = d(3, 1), excluded = true),
                tx("1000", TransactionType.EXPENSE, date = d(3, 1), loanId = 1),
                tx("1000", TransactionType.EXPENSE, date = d(3, 1), loanId = 2, loanContribution = "400"),
            ),
            today = d(3, 1),
        )
        assertAmounts(listOf("600"), t.thisCycle)
    }

    @Test fun `empty history gives a flat zero line`() = runTest {
        val t = trend(emptyList(), today = d(3, 3))
        assertAmounts(listOf("0", "0", "0"), t.thisCycle)
        assertAmounts(listOf("0", "0", "0"), t.lastCycleSamePoint)
    }

    @Test fun `rows outside both cycles are ignored`() = runTest {
        val t = trend(listOf(expense("70", d(1, 31)), expense("80", d(3, 10))), today = d(3, 2))
        assertAmounts(listOf("0", "0"), t.thisCycle)
        assertAmounts(listOf("0", "0"), t.lastCycleSamePoint)
    }

    @Test fun `today before the cycle start draws nothing`() = runTest {
        // A stale today after the start day moved later: both lines are empty, not one point.
        val t = trend(listOf(expense("40", d(2, 2))), today = d(2, 27))
        assertTrue(t.thisCycle.isEmpty())
        assertTrue(t.lastCycleSamePoint.isEmpty())
    }
}
