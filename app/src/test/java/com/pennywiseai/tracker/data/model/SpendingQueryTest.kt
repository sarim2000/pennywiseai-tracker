package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.model.SpendingQuery.AccountMatch
import com.pennywiseai.tracker.data.model.SpendingQuery.Dates
import com.pennywiseai.tracker.data.model.SpendingQuery.GroupBy
import com.pennywiseai.tracker.data.model.SpendingQuery.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SpendingQueryTest {
    // A Monday.
    private val now = LocalDateTime.of(2026, 11, 16, 14, 0)

    private fun dates(period: String = "", from: String = "", to: String = "", cycleStartDay: Int = 1) =
        SpendingQuery.dates(period, from, to, now, DayOfWeek.MONDAY, cycleStartDay)

    private fun window(start: LocalDate, end: LocalDate) =
        Dates.Of(SpendingComparison.Window(start.atStartOfDay(), end.atTime(LocalTime.MAX)))

    private fun day(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `this month, however the model spells it, is Home's period`() {
        listOf("", " ", "this_month", "This Month", "this-month", "month").forEach { assertEquals(it, Dates.Home, dates(it)) }
        assertTrue(SpendingQuery.defaultPeriod(null, null, null))
        assertTrue(SpendingQuery.defaultPeriod("this month", "", ""))
        assertTrue(!SpendingQuery.defaultPeriod("last_month", "", ""))
        assertTrue(!SpendingQuery.defaultPeriod("", "2026-11-01", ""))
    }

    @Test
    fun `last month is the whole cycle before this one`() {
        assertEquals(window(day(10, 1), day(10, 31)), dates("last_month"))
        // A cycle starting on the 25th: 25 Sep to 24 Oct, as on Home.
        assertEquals(window(day(9, 25), day(10, 24)), dates("last month", cycleStartDay = 25))
    }

    @Test
    fun `weeks start on the locale's day, the last one whole`() {
        assertEquals(window(day(11, 16), day(11, 16)), dates("this_week"))
        assertEquals(window(day(11, 9), day(11, 15)), dates("last_week"))
        assertEquals(window(day(11, 15), day(11, 15)), dates("yesterday"))
        assertEquals(window(day(11, 16), day(11, 16)), dates("today"))
    }

    @Test
    fun `a month or year written out is the calendar one, cut at today`() {
        assertEquals(window(day(9, 1), day(9, 30)), dates("2026-09"))
        // A leap February runs to the 29th.
        assertEquals(window(day(2, 1, 2024), day(2, 29, 2024)), dates("2024-02"))
        assertEquals(window(day(2, 1, 2025), day(2, 28, 2025)), dates("2025-02"))
        // This month as a calendar month: never claims the days still to come.
        assertEquals(window(day(11, 1), day(11, 16)), dates("2026-11"))
        assertEquals(window(day(1, 1, 2025), day(12, 31, 2025)), dates("2025"))
        assertEquals(window(day(1, 1), day(11, 16)), dates("2026"))
    }

    @Test
    fun `dates the user gave win over the period, up to today when open`() {
        assertEquals(window(day(10, 5), day(10, 20)), dates("this_month", "2026-10-05", "2026-10-20"))
        assertEquals(window(day(11, 10), day(11, 16)), dates(from = "2026-11-10"))
        assertEquals(window(day(2, 29, 2024), day(2, 29, 2024)), dates(from = "2024-02-29", to = "2024-02-29"))
        assertEquals(window(day(11, 1), day(11, 16)), dates(from = "2026-11-01", to = "2026-12-31"))
    }

    @Test
    fun `what can't be read is said, never swapped for this month`() {
        assertEquals(Dates.Invalid(Problem.PERIOD, "fortnight"), dates("fortnight"))
        assertEquals(Dates.Invalid(Problem.PERIOD, "2026-13"), dates("2026-13"))
        // 2025 isn't a leap year.
        assertEquals(Dates.Invalid(Problem.DATE, "2025-02-29"), dates(from = "2025-02-29"))
        assertEquals(Dates.Invalid(Problem.DATE, "31/10/2026"), dates(from = "2026-10-01", to = "31/10/2026"))
        assertEquals(Dates.Invalid(Problem.NO_START, "2026-10-31"), dates(to = "2026-10-31"))
        assertEquals(Dates.Invalid(Problem.ORDER, "2026-11-10", "2026-11-01"), dates(from = "2026-11-10", to = "2026-11-01"))
        assertEquals(Dates.Invalid(Problem.FUTURE, "2026-12"), dates("2026-12"))
        assertEquals(Dates.Invalid(Problem.FUTURE, "2026-11-17"), dates(from = "2026-11-17"))
    }

    @Test
    fun `groupings the app offers, and nothing else`() {
        assertEquals(GroupBy.NONE, SpendingQuery.groupBy(null))
        assertEquals(GroupBy.NONE, SpendingQuery.groupBy(""))
        assertEquals(GroupBy.NONE, SpendingQuery.groupBy("none"))
        assertEquals(GroupBy.CATEGORY, SpendingQuery.groupBy("Categories"))
        assertEquals(GroupBy.MERCHANT, SpendingQuery.groupBy("merchant"))
        assertEquals(GroupBy.ACCOUNT, SpendingQuery.groupBy("accounts"))
        assertNull(SpendingQuery.groupBy("day"))
    }

    private fun account(bank: String, last4: String, alias: String? = null) = AccountBalanceEntity(
        bankName = bank, accountLast4 = last4, balance = BigDecimal.ZERO, timestamp = now, alias = alias
    )

    @Test
    fun `an account is found by its name, its digits or its bank, and two that fit are a question`() {
        val hdfc = account("HDFC", "1234")
        val sbi = account("SBI", "1234")
        val salary = account("HDFC", "5678", alias = "Salary")
        val cash = account("Cash", AccountBalanceEntity.WALLET_ACCOUNT_MARKER)
        val all = listOf(hdfc, sbi, salary, cash)

        assertEquals(AccountMatch.One(hdfc), SpendingQuery.account("HDFC 1234", all))
        assertEquals(AccountMatch.One(hdfc), SpendingQuery.account("HDFC ••1234", all))
        assertEquals(AccountMatch.One(sbi), SpendingQuery.account("sbi card 1234", all))
        assertEquals(AccountMatch.One(salary), SpendingQuery.account("salary", all))
        assertEquals(AccountMatch.One(salary), SpendingQuery.account("5678", all))
        assertEquals(AccountMatch.One(cash), SpendingQuery.account("cash", all))
        // The prompt lists an account without an alias as "bank last4", so a wallet as "Cash WALLET".
        assertEquals(AccountMatch.One(cash), SpendingQuery.account("Cash WALLET", all))
        // Two cards end in 1234, and HDFC has two accounts: the user picks.
        assertEquals(AccountMatch.Several(listOf(hdfc, sbi)), SpendingQuery.account("1234", all))
        assertEquals(AccountMatch.Several(listOf(hdfc, salary)), SpendingQuery.account("hdfc", all))
        assertEquals(AccountMatch.None, SpendingQuery.account("Barclays", all))
        assertEquals(AccountMatch.None, SpendingQuery.account("9999", all))
    }
}
