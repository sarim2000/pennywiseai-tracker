package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.model.SpendingComparison.Period
import com.pennywiseai.tracker.data.model.SpendingComparison.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** M4: the chat's "this month vs last month" is computed here, never by the model. */
class SpendingComparisonTest {
    private val now = LocalDateTime.of(2026, 11, 9, 14, 30)
    private val windows = SpendingComparison.windows(Period.MONTH, now, DayOfWeek.MONDAY)
    private var nextId = 1L

    private fun tx(
        amount: String,
        currency: String = "INR",
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Food & Dining",
        splits: List<Pair<String, String>> = emptyList(),
        excluded: Boolean = false,
        lent: String? = null
    ): TransactionWithSplits {
        val id = nextId++
        return TransactionWithSplits(
            TransactionEntity(
                id = id, amount = BigDecimal(amount), merchantName = "Shop", category = category, transactionType = type,
                dateTime = now, transactionHash = "h$id", currency = currency, excludedFromAnalytics = excluded,
                lentSharesTotal = lent?.let(::BigDecimal)
            ),
            splits.map { (cat, amt) -> TransactionSplitEntity(transactionId = id, category = cat, amount = BigDecimal(amt)) }
        )
    }

    private fun rows(r: Result) = (r as Result.Rows).rows.associate { it.currency to Triple(it.current.toPlainString(), it.previous.toPlainString(), it.changePercent?.toInt()) }

    @Test
    fun `month so far against the same days of last month`() {
        val (cur, prev) = windows
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), cur.start)
        assertEquals(now, cur.end)
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), prev.start)
        assertEquals(LocalDateTime.of(2026, 10, 9, 14, 30), prev.end)
    }

    @Test
    fun `past the end of a shorter month the whole of it is compared`() {
        val (cur, prev) = SpendingComparison.windows(Period.MONTH, LocalDateTime.of(2026, 3, 31, 10, 0), DayOfWeek.MONDAY)
        assertEquals(LocalDateTime.of(2026, 3, 1, 0, 0), cur.start)
        assertEquals(LocalDateTime.of(2026, 2, 28, 0, 0).with(LocalTime.MAX), prev.end)
        // Leap year: the 29th exists, so it's like for like.
        val (_, leap) = SpendingComparison.windows(Period.MONTH, LocalDateTime.of(2028, 3, 29, 10, 0), DayOfWeek.MONDAY)
        assertEquals(LocalDateTime.of(2028, 2, 29, 10, 0), leap.end)
    }

    @Test
    fun `january compares with december of the year before`() {
        val (_, prev) = SpendingComparison.windows(Period.MONTH, LocalDateTime.of(2027, 1, 1, 8, 0), DayOfWeek.MONDAY)
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), prev.start)
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 0), prev.end)
    }

    @Test
    fun `week starts on the locale's first day`() {
        // 9 Nov 2026 is a Monday.
        val (mon, monPrev) = SpendingComparison.windows(Period.WEEK, now, DayOfWeek.MONDAY)
        assertEquals(LocalDateTime.of(2026, 11, 9, 0, 0), mon.start)
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0), monPrev.start)
        assertEquals(LocalDateTime.of(2026, 11, 2, 14, 30), monPrev.end)
        val (sat, _) = SpendingComparison.windows(Period.WEEK, now, DayOfWeek.SATURDAY)
        assertEquals(LocalDateTime.of(2026, 11, 7, 0, 0), sat.start)
    }

    @Test
    fun `per currency, never added together`() {
        val r = SpendingComparison.compare(
            windows,
            current = listOf(tx("800"), tx("50", "USD"), tx("200", type = TransactionType.CREDIT)),
            previous = listOf(tx("1250"), tx("40", "AED"))
        )
        assertEquals(
            mapOf(
                "AED" to Triple("0", "40", -100),
                "INR" to Triple("1000", "1250", -20),
                "USD" to Triple("50", "0", null)
            ),
            rows(r)
        )
    }

    @Test
    fun `income, transfers and what doesn't count toward totals are left out`() {
        val r = SpendingComparison.compare(
            windows,
            current = listOf(
                tx("300"), tx("5000", type = TransactionType.INCOME), tx("900", type = TransactionType.TRANSFER),
                tx("700", excluded = true), tx("1000", lent = "400")
            ),
            previous = listOf(tx("300"))
        )
        assertEquals(mapOf("INR" to Triple("900", "300", 200)), rows(r))
    }

    @Test
    fun `a category counts its share of split transactions`() {
        val current = listOf(tx("100", category = "Food & Dining", splits = listOf("Food & Dining" to "60", "Transportation" to "40")))
        val previous = listOf(tx("50", category = "Transportation"))
        assertEquals(mapOf("INR" to Triple("40", "50", -20)), rows(SpendingComparison.compare(windows, current, previous, "transportation")))
        assertEquals(mapOf("INR" to Triple("60", "0", null)), rows(SpendingComparison.compare(windows, current, previous, "Food & Dining")))
        assertEquals(mapOf("INR" to Triple("100", "50", 100)), rows(SpendingComparison.compare(windows, current, previous)))
    }

    @Test
    fun `an empty window can't be compared, but zero spending in a category can`() {
        assertEquals(windows.second, (SpendingComparison.compare(windows, listOf(tx("10")), emptyList()) as Result.NoData).missing)
        assertEquals(windows.first, (SpendingComparison.compare(windows, emptyList(), listOf(tx("10"))) as Result.NoData).missing)
        // Only income last month: recorded, so a real zero.
        val r = SpendingComparison.compare(windows, listOf(tx("10")), listOf(tx("500", type = TransactionType.INCOME)))
        assertEquals(mapOf("INR" to Triple("10", "0", null)), rows(r))
        val none = SpendingComparison.compare(windows, listOf(tx("10")), listOf(tx("20")), category = "Travel")
        assertTrue((none as Result.Rows).rows.isEmpty())
    }

    @Test
    fun `change rounds half up and needs something before`() {
        assertEquals(BigDecimal("34"), SpendingComparison.change(BigDecimal("133.5"), BigDecimal("100")))
        assertEquals(BigDecimal("-33"), SpendingComparison.change(BigDecimal("66.6"), BigDecimal("100")))
        assertEquals(0, SpendingComparison.change(BigDecimal("100"), BigDecimal("100.00"))!!.signum())
        // A tiny previous total: a huge rise, never an overflow into "less".
        assertEquals(BigDecimal("2999999900"), SpendingComparison.change(BigDecimal("300000"), BigDecimal("0.01")))
        assertNull(SpendingComparison.change(BigDecimal("100"), BigDecimal.ZERO))
    }

    @Test
    fun `the user's words pick the period over the model`() {
        assertEquals(Period.WEEK, SpendingComparison.period("this week vs last week?", "month"))
        assertEquals(Period.MONTH, SpendingComparison.period("compare with last month", "week"))
        assertEquals(Period.WEEK, SpendingComparison.period("قارن صرفي هذا الأسبوع بالأسبوع الماضي", "month"))
        assertEquals(Period.MONTH, SpendingComparison.period("كم صرفت هالشهر مقارنة بالشهر الماضي", null))
        assertEquals(Period.WEEK, SpendingComparison.period("صرفت أكثر هالأسبوع؟", "month"))
        assertEquals(Period.WEEK, SpendingComparison.period("قارن صرفي للأسبوع الماضي", "month"))
        assertEquals(Period.WEEK, SpendingComparison.period("is hafte pichle hafte se zyada kharcha hua?", "month"))
        assertEquals(Period.MONTH, SpendingComparison.period("इस महीने बनाम पिछले महीने", "week"))
        assertEquals(Period.MONTH, SpendingComparison.period("am I spending more?", null))
        assertEquals(Period.WEEK, SpendingComparison.period("am I spending more?", "Week"))
    }

    @Test
    fun `periods we can't compare are refused, not swapped for this month`() {
        assertNull(SpendingComparison.period("last week vs last month", "month"))
        assertNull(SpendingComparison.period("September vs August", "month"))
        assertNull(SpendingComparison.period("this year vs 2025", "month"))
        assertNull(SpendingComparison.period("قارن سبتمبر مع أغسطس", "month"))
        assertNull(SpendingComparison.period("am I spending more?", "quarter"))
        assertNull(SpendingComparison.period("मई के मुकाबले इस महीने", "month"))
        assertNull(SpendingComparison.period("قارن صرفي هذا العام بالعام الماضي", "month"))
        assertNull(SpendingComparison.period("2025 vs 2024", "month"))
        assertNull(SpendingComparison.period("compare this month with the same month in 2025", "month"))
        assertNull(SpendingComparison.period("قارن صرفي هالشهر وهالأسبوع", null))
    }

    @Test
    fun `words inside other words and amounts don't count`() {
        // "साल" (year) sits inside "मसालों" (spices).
        assertEquals(Period.MONTH, SpendingComparison.period("इस महीने मसालों पर खर्च पिछले महीने से बढ़ा?", null))
        assertEquals(Period.MONTH, SpendingComparison.period("compare my 2000 rupee rent this month with last month", null))
        // "بشكل عام" is "in general", not "year"; "مشهور" holds "شهر".
        assertEquals(Period.WEEK, SpendingComparison.period("بشكل عام هل صرفت أكثر هذا الأسبوع؟", null))
        assertEquals(Period.WEEK, SpendingComparison.period("مطعم مشهور، صرفي هالأسبوع أكثر؟", null))
    }

    @Test
    fun `naming last month or week turns a this-month lookup into a comparison`() {
        assertTrue(SpendingComparison.asksAboutIncome("how much income did I receive last month?"))
        assertTrue(SpendingComparison.asksAboutIncome("كم راتبي الشهر الماضي"))
        assertTrue(!SpendingComparison.asksAboutIncome("how much did I spend last month?"))
        listOf(
            "how much did I spend on food last month?", "pichle mahine ke mukable shopping pe kitna kharch hua?",
            "कितना खर्च हुआ पिछले हफ्ते", "كم صرفت الشهر الماضي؟", "صرفت كم الأسبوع اللي فات", "pichle month shopping pe kitna kharch hua?"
        ).forEach { assertTrue(it, SpendingComparison.namesThePreviousPeriod(it)) }
        listOf("how much did I spend on food this month?", "my last coffee", "كم صرفت هذا الشهر؟")
            .forEach { assertTrue(it, !SpendingComparison.namesThePreviousPeriod(it)) }
    }

    @Test
    fun `top categories rank within each currency and take split shares`() {
        val top = SpendingComparison.topCategories(
            listOf(
                tx("250", category = "Shopping"),
                tx("500", splits = listOf("Food & Dining" to "100", "Travel" to "400")),
                tx("200", category = "Food & Dining"),
                tx("9", currency = "USD", category = "Travel"),
                tx("1000", type = TransactionType.INCOME, category = "Salary"),
                tx("5000", category = "Rent", excluded = true)
            ),
            limit = 2
        )
        // INR: Travel 400 (a split share), Food 300 (split 100 + 200); Shopping is past the limit. USD apart.
        assertEquals(listOf("INR", "USD"), top.keys.toList())
        assertEquals(listOf("Travel" to BigDecimal("400"), "Food & Dining" to BigDecimal("300")), top.getValue("INR"))
        assertEquals(listOf("Travel" to BigDecimal("9")), top.getValue("USD"))
    }

    @Test
    fun `month spend is per currency and leaves income out`() {
        val spent = SpendingComparison.spend(listOf(tx("120"), tx("30", type = TransactionType.CREDIT), tx("7", currency = "USD"), tx("900", type = TransactionType.INCOME)))
        assertEquals(mapOf("INR" to BigDecimal("150"), "USD" to BigDecimal("7")), spent)
    }
}
