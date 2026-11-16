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
        // A year by name, as models write it.
        assertEquals(window(day(1, 1), day(11, 16)), dates("this_year"))
        assertEquals(window(day(1, 1, 2025), day(12, 31, 2025)), dates("last year"))
    }

    @Test
    fun `a day the user named decides when the model left the period empty`() {
        val today = now.toLocalDate()
        assertEquals(window(day(11, 16), day(11, 16)), SpendingQuery.daysNamed("how much did I spend today?", today))
        assertEquals(window(day(11, 15), day(11, 15)), SpendingQuery.daysNamed("كم صرفت أمس؟", today))
        assertEquals(window(day(11, 15), day(11, 15)), SpendingQuery.daysNamed("कल कितना खर्च हुआ?", today))
        assertEquals(window(day(11, 13), day(11, 13)), SpendingQuery.daysNamed("spent 3 days ago", today))
        // Days it can't count are said, not answered with this month.
        assertEquals(Dates.Invalid(Problem.PERIOD), SpendingQuery.daysNamed("how much in the last 10 days?", today))
        assertEquals(Dates.Invalid(Problem.PERIOD), SpendingQuery.daysNamed("كم صرفت في آخر ٣ أيام", today))
        assertEquals(Dates.Invalid(Problem.PERIOD), SpendingQuery.daysNamed("how much on Monday?", today))
        // No day named, or a week or month that decides instead.
        assertNull(SpendingQuery.daysNamed("how much have I spent?", today))
        assertNull(SpendingQuery.daysNamed("how much this month up to today?", today))
        assertNull(SpendingQuery.daysNamed("how much this week?", today))
        // The days so far, not today alone: Home's period decides.
        assertNull(SpendingQuery.daysNamed("how much have I spent right now?", today))
        assertNull(SpendingQuery.daysNamed("aaj tak kitna kharch hua?", today))
        assertNull(SpendingQuery.daysNamed("كم صرفت حتى اليوم؟", today))
        assertNull(SpendingQuery.daysNamed("how much till today?", today))
        assertEquals(window(day(11, 16), day(11, 16)), SpendingQuery.daysNamed("aaj kitna kharch hua?", today))
        // A week or a month ago is before this week or month: the day, or days it can't count.
        assertEquals(window(day(11, 9), day(11, 9)), SpendingQuery.daysNamed("how much did I spend a week ago?", today))
        assertEquals(window(day(11, 2), day(11, 2)), SpendingQuery.daysNamed("spent 2 weeks ago", today))
        assertEquals(Dates.Invalid(Problem.PERIOD), SpendingQuery.daysNamed("how much did I spend a month ago?", today))
        assertEquals(Dates.Invalid(Problem.PERIOD), SpendingQuery.daysNamed("كم صرفت قبل شهر؟", today))
    }

    @Test
    fun `a year named by its words, alone, with the rest of the message`() {
        assertEquals("this_year", SpendingQuery.yearNamed("how much this year?")?.first)
        assertEquals("last_year", SpendingQuery.yearNamed("كم صرفت العام الماضي")?.first)
        assertEquals("last_year", SpendingQuery.yearNamed("pichle saal kitna kharcha?")?.first)
        assertEquals("how much in March  ?", SpendingQuery.yearNamed("how much in March this year?")?.second)
        // Both, or neither, is no one year.
        assertNull(SpendingQuery.yearNamed("this year vs last year"))
        assertNull(SpendingQuery.yearNamed("how much this month?"))
    }

    @Test
    fun `the merchant and account named leave the words read for dates`() {
        assertEquals("spent at   this month", SpendingQuery.withoutNames("spent at 24/7 this month", "24/7", null))
        assertEquals("card ending in  ", SpendingQuery.withoutNames("card ending in 2019", null, "HDFC 2019"))
        assertEquals("how much?", SpendingQuery.withoutNames("how much?", "", null))
    }

    @Test
    fun `dates the user gave win over the period, up to today when open`() {
        assertEquals(window(day(10, 5), day(10, 20)), dates("this_month", "2026-10-05", "2026-10-20"))
        assertEquals(window(day(11, 10), day(11, 16)), dates(from = "2026-11-10"))
        assertEquals(window(day(2, 29, 2024), day(2, 29, 2024)), dates(from = "2024-02-29", to = "2024-02-29"))
        assertEquals(window(day(11, 1), day(11, 16)), dates(from = "2026-11-01", to = "2026-12-31"))
    }

    @Test
    fun `a month named is the latest one begun, or the year written with it`() {
        val today = now.toLocalDate()
        fun month(message: String, year: Int? = null) = SpendingQuery.monthNamed(message, today, year)
        assertEquals(window(day(10, 1), day(10, 31)), month("How much did I spend in October?"))
        assertEquals(window(day(10, 1), day(10, 31)), month("كم صرفت في أكتوبر؟"))
        assertEquals(window(day(10, 1), day(10, 31)), month("كم صرفت بتشرين الأول"))
        // Not begun this year: last year's.
        assertEquals(window(day(12, 1, 2025), day(12, 31, 2025)), month("spending in December"))
        assertEquals(window(day(3, 1, 2025), day(3, 31, 2025)), month("March 2025 spending"))
        assertEquals(window(day(3, 1, 2025), day(3, 31, 2025)), month("in March", year = 2025))
        // This month so far.
        assertEquals(window(day(11, 1), day(11, 16)), month("November"))
        // None, two, or a day in it: not a month's figure.
        assertNull(month("how much in may?"))
        assertNull(month("September vs October"))
        assertNull(month("on 15 October"))
        assertNull(month("October 3rd"))
        // The model's period, spelled out.
        assertEquals(window(day(10, 1), day(10, 31)), dates("October"))
        assertEquals(window(day(10, 1, 2025), day(10, 31, 2025)), dates("october_2025"))
        // A year it says in words is that year's month, not the latest one begun.
        assertEquals(window(day(10, 1, 2025), day(10, 31, 2025)), dates("october_last_year"))
        assertEquals(window(day(10, 1, 2025), day(10, 31, 2025)), dates("October last year"))
    }

    @Test
    fun `last November, in November, is last year's`() {
        val today = now.toLocalDate()
        fun month(message: String) = SpendingQuery.monthNamed(message, today)
        assertEquals(window(day(11, 1, 2025), day(11, 30, 2025)), month("how much did I spend last November?"))
        assertEquals(window(day(11, 1, 2025), day(11, 30, 2025)), month("كم صرفت في نوفمبر الماضي؟"))
        assertEquals(window(day(11, 1, 2025), day(11, 30, 2025)), month("كم صرفت نوفمبر اللي فات"))
        // Last October is the one just gone.
        assertEquals(window(day(10, 1), day(10, 31)), month("how much did I spend last October?"))
    }

    @Test
    fun `a month with other days around it is days it can't look up`() {
        val today = now.toLocalDate()
        fun month(message: String) = SpendingQuery.monthNamed(message, today)
        listOf(
            "How much have I spent since October?",
            "how much did I spend in the first week of October?",
            "spending till October",
            "spending up to October",
            "October till today",
            "October to date",
            "October onwards",
            "how much at the end of October?",
            "mid-October spending",
            "كم صرفت منذ أكتوبر؟",
            "كم صرفت في الأسبوع الأول من أكتوبر؟",
            "كم صرفت في نهاية أكتوبر؟",
            "من أكتوبر وحتى اليوم",
        ).forEach { assertEquals(it, Dates.Invalid(Problem.PERIOD, it), month(it)) }
        // A word that only sits beside it is no other days.
        assertEquals(window(day(10, 1), day(10, 31)), month("how much did I pay in October to the landlord?"))
        assertEquals(window(day(10, 1), day(10, 31)), month("how much did I top up in October?"))
        assertEquals(window(day(10, 1), day(10, 31)), month("كم صرفت من راتبي في أكتوبر؟"))
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

    @Test
    fun `a ranking of the one merchant, account or category asked about is its total`() {
        fun grouping(by: GroupBy, category: String?, merchant: String?, account: String?, message: String = "") =
            SpendingQuery.grouping(by, category, merchant, account, message)
        // What Qwen2.5 sends for "how much at Swiggy?" and "how much from my HDFC account?".
        assertEquals(GroupBy.NONE, grouping(GroupBy.CATEGORY, null, "Swiggy", null, "how much at Swiggy?"))
        assertEquals(GroupBy.NONE, grouping(GroupBy.MERCHANT, null, "Swiggy", null, "Swiggy spending by merchant"))
        assertEquals(GroupBy.NONE, grouping(GroupBy.ACCOUNT, null, null, "HDFC_1234"))
        assertEquals(GroupBy.NONE, grouping(GroupBy.CATEGORY, "Food & Dining", null, null))
        // Rankings within what was asked stay: "where do I spend most on food?".
        assertEquals(GroupBy.MERCHANT, grouping(GroupBy.MERCHANT, "Food & Dining", null, null))
        assertEquals(GroupBy.CATEGORY, grouping(GroupBy.CATEGORY, null, null, "HDFC_1234"))
        assertEquals(GroupBy.CATEGORY, grouping(GroupBy.CATEGORY, null, null, null))
        // A breakdown asked for at one merchant stays.
        assertEquals(GroupBy.ACCOUNT, grouping(GroupBy.ACCOUNT, null, "Amazon", null, "How much did I spend at Amazon on each card?"))
        assertEquals(GroupBy.CATEGORY, grouping(GroupBy.CATEGORY, null, "Carrefour", null, "my Carrefour spending by category"))
        assertEquals(GroupBy.ACCOUNT, grouping(GroupBy.ACCOUNT, null, "أمازون", null, "كم صرفت في أمازون حسب البطاقة؟"))
    }

    @Test
    fun `a category counts as named by a word of it`() {
        assertTrue(SpendingQuery.namesCategory("Food & Dining", "how much on food at Swiggy?"))
        assertTrue(SpendingQuery.namesCategory("Groceries", "grocery spend at Carrefour"))
        assertTrue(!SpendingQuery.namesCategory("Shopping", "Swiggy spending this month?"))
        assertTrue(!SpendingQuery.namesCategory("Food & Dining", "كم صرفت في ستاربكس هالشهر؟"))
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
