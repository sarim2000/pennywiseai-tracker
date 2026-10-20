package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.Locale

/** #422: the Analytics "Group by" math and its Pro fallback. */
class AnalyticsGroupingTest {
    private fun row(
        id: Long,
        amount: String,
        currency: String = "INR",
        bank: String = "Test Bank",
        account: String? = "1234",
        // 2026-10-05 is a Monday
        day: Int = 5
    ): CountedRow {
        val tx = TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Test", category = "Others",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, day, 12, 0),
            transactionHash = "h$id", currency = currency, bankName = bank, accountNumber = account
        )
        return CountedRow(tx, Money(tx.amount, currency))
    }

    @Test
    fun `totals stay per currency inside a group`() {
        val groups = groupTotals(listOf(row(1, "500"), row(2, "10", "USD"), row(3, "250"))) { listOf("all") }
        val all = groups.getValue("all")
        assertEquals(BigDecimal("750"), all.amountIn("INR"))
        assertEquals(BigDecimal("10"), all.amountIn("USD"))
        assertEquals(BigDecimal.ZERO, all.amountIn("EUR"))
        assertEquals(3, all.rows.size)
    }

    @Test
    fun `a multi-tag row counts in full under each tag, untagged under No tag`() {
        val tags = mapOf(1L to listOf("Trip", "Work"), 2L to listOf("Trip"), 3L to emptyList())
        val groups = groupTotals(listOf(row(1, "100"), row(2, "40"), row(3, "7"), row(4, "3"))) {
            tagKeys(tags[it.id])
        }
        assertEquals(BigDecimal("140"), groups.getValue("Trip").amountIn("INR"))
        assertEquals(BigDecimal("100"), groups.getValue("Work").amountIn("INR"))
        // Row 3 has an empty tag list and row 4 none at all: both are "No tag".
        assertEquals(BigDecimal("10"), groups.getValue(null).amountIn("INR"))
        assertEquals(2, groups.getValue(null).rows.size)
        assertEquals(setOf("Trip", "Work", null), groups.keys)
    }

    @Test
    fun `a tag repeated on one row counts once`() {
        val groups = groupTotals(listOf(row(1, "100"))) { tagKeys(listOf("Trip", "Trip")) }
        assertEquals(BigDecimal("100"), groups.getValue("Trip").amountIn("INR"))
    }

    @Test
    fun `weekdays follow the locale's week`() {
        assertEquals(DayOfWeek.entries, weekdayOrder(Locale.UK))
        assertEquals(
            listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1),
            weekdayOrder(Locale.US)
        )
    }

    @Test
    fun `rows group onto their weekday`() {
        // Oct 5 and 12 are Mondays, Oct 11 a Sunday.
        val groups = groupTotals(listOf(row(1, "10", day = 5), row(2, "20", day = 12), row(3, "5", day = 11))) {
            listOf(it.dateTime.dayOfWeek)
        }
        assertEquals(BigDecimal("30"), groups.getValue(DayOfWeek.MONDAY).amountIn("INR"))
        assertEquals(BigDecimal("5"), groups.getValue(DayOfWeek.SUNDAY).amountIn("INR"))
    }

    @Test
    fun `account labels prefer the alias, else bank and last four`() {
        val a = row(1, "10", bank = "HDFC", account = "1111").tx
        val b = row(2, "10", bank = "SBI", account = "2222").tx
        val labels = mapOf(accountKeyOf(a) to "Salary")
        assertEquals("HDFC_1111", accountKeyOf(a))
        assertEquals("Salary", accountLabelFor(accountKeyOf(a), a, labels))
        assertEquals("SBI ••2222", accountLabelFor(accountKeyOf(b), b, labels))
    }

    @Test
    fun `pro groupings fall back to category without pro`() {
        AnalyticsGroupBy.entries.forEach { option ->
            assertEquals(option, option.allowedFor(isProEntitled = true))
        }
        assertEquals(AnalyticsGroupBy.CATEGORY, AnalyticsGroupBy.TAG.allowedFor(isProEntitled = false))
        assertEquals(AnalyticsGroupBy.CATEGORY, AnalyticsGroupBy.DAY_OF_WEEK.allowedFor(isProEntitled = false))
        assertEquals(AnalyticsGroupBy.MERCHANT, AnalyticsGroupBy.MERCHANT.allowedFor(isProEntitled = false))
        assertEquals(AnalyticsGroupBy.ACCOUNT, AnalyticsGroupBy.ACCOUNT.allowedFor(isProEntitled = false))
    }

    @Test
    fun `a stored grouping restores, unknown or missing means category`() {
        assertEquals(AnalyticsGroupBy.TAG, AnalyticsGroupBy.fromSaved("TAG"))
        assertEquals(AnalyticsGroupBy.CATEGORY, AnalyticsGroupBy.fromSaved(null))
        assertEquals(AnalyticsGroupBy.CATEGORY, AnalyticsGroupBy.fromSaved("PIVOT"))
        // A lapsed Pro user with a stored Pro grouping sees Category.
        assertEquals(AnalyticsGroupBy.CATEGORY, AnalyticsGroupBy.fromSaved("DAY_OF_WEEK").allowedFor(false))
    }
}
