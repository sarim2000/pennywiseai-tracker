package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class NetWorthTrendTest {
    private fun d(v: String) = BigDecimal(v)
    private val start = LocalDate.of(2026, 11, 1)
    private val end = LocalDate.of(2026, 11, 5)

    private fun row(
        last4: String,
        balance: String,
        at: LocalDateTime,
        currency: String = "INR",
        card: Boolean = false,
    ) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = d(balance), timestamp = at,
        isCreditCard = card, accountType = if (card) "CREDIT" else "SAVINGS", currency = currency,
    )

    private fun day(n: Int, hour: Int = 10) = LocalDateTime.of(2026, 11, n, hour, 0)

    private fun values(points: List<NetWorthPoint>?) = points.orEmpty().map { it.net.toPlainString() }

    @Test
    fun `carries the balance from before the range forward each day`() {
        val trend = NetWorth.trend(
            listOf(
                row("1111", "1000", LocalDateTime.of(2026, 10, 20, 9, 0)),
                row("1111", "1500", day(3)),
            ),
            emptyMap(), start, end,
        )
        assertEquals(listOf("1000", "1000", "1500", "1500", "1500"), values(trend["INR"]))
        assertEquals(start, trend.getValue("INR").first().date)
    }

    @Test
    fun `a card's outstanding is taken off`() {
        val trend = NetWorth.trend(
            listOf(
                row("1111", "1000", day(1)),
                row("2222", "300", day(1), card = true),
                row("2222", "450", day(4), card = true),
            ),
            emptyMap(), start, end,
        )
        assertEquals(listOf("700", "700", "700", "550", "550"), values(trend["INR"]))
    }

    @Test
    fun `an account seen only later adds nothing before it`() {
        val trend = NetWorth.trend(
            listOf(row("1111", "1000", day(1)), row("2222", "200", day(3))),
            emptyMap(), start, end,
        )
        assertEquals(listOf("1000", "1000", "1200", "1200", "1200"), values(trend["INR"]))
    }

    @Test
    fun `each currency is its own line, and a currency change crosses none`() {
        val trend = NetWorth.trend(
            listOf(
                row("1111", "1000", day(1)),
                row("2222", "50", day(1), currency = "USD"),
                // The account was moved to INR: from here it counts there only.
                row("2222", "4000", day(3), currency = "INR"),
            ),
            emptyMap(), start, end,
        )
        assertEquals(listOf("50", "50"), values(trend["USD"]))
        assertEquals(listOf(day(1).toLocalDate(), day(2).toLocalDate()), trend.getValue("USD").map { it.date })
        assertEquals(listOf("1000", "1000", "5000", "5000", "5000"), values(trend["INR"]))
    }

    @Test
    fun `a manual account comes from its transactions, and updating its balance keeps the earlier days`() {
        val cash = DerivedHistory(
            currency = "INR",
            current = d("700"),
            // The balance was last given on Nov 4; that must not cut the line there.
            anchored = true,
            effects = listOf(day(3) to d("-200"), day(4) to d("-100"), LocalDateTime.of(2026, 10, 1, 9, 0) to d("50")),
        )
        val trend = NetWorth.trend(
            listOf(row("1111", "1000", day(1))),
            mapOf(("Cash" to "0000") to cash), start, end,
        )
        assertEquals(start, trend.getValue("INR").first().date)
        assertEquals(listOf("2000", "2000", "1800", "1700", "1700"), values(trend["INR"]))
    }

    @Test
    fun `a manual account adds nothing before it existed`() {
        // Added on Nov 4 with 3000: the days before must not show 3000.
        val cash = DerivedHistory("INR", d("3000"), anchored = true, effects = emptyList(), existsFrom = day(4))
        val trend = NetWorth.trend(
            listOf(row("1111", "1000", day(1))),
            mapOf(("Cash" to "0000") to cash), start, end,
        )
        assertEquals(listOf("1000", "1000", "1000", "4000", "4000"), values(trend["INR"]))

        // Created today alongside today's only bank row: a single day, no history to draw.
        val fresh = NetWorth.trend(
            listOf(row("1111", "1000", day(5))),
            mapOf(("Cash" to "0000") to cash.copy(existsFrom = day(5))), start, end,
        )
        assertEquals(listOf(end), fresh.getValue("INR").map { it.date })
    }

    private fun cash(
        currency: String,
        balance: String,
        anchorAt: LocalDateTime,
        anchorSource: String = AccountBalanceRepository.ANCHOR_ENTERED,
    ) = AccountBalanceEntity(
        bankName = "Cash", accountLast4 = "0000", balance = d(balance), timestamp = anchorAt,
        accountType = "SAVINGS", currency = currency, anchorAmount = d(balance), anchorAt = anchorAt,
        anchorSource = anchorSource,
    )

    @Test
    fun `a currency change with no transactions in the old one still starts the line`() {
        // USD 100 cash since October with nothing spent, moved to EUR 100 on Nov 3: the
        // days before must not show EUR 100.
        val history = NetWorth.derivedHistory(
            cash("EUR", "100", anchorAt = day(3, hour = 12), anchorSource = AccountBalanceRepository.ANCHOR_CURRENCY_CHANGE),
            emptyList(),
            firstRow = LocalDateTime.of(2026, 10, 1, 9, 0),
        )
        val trend = NetWorth.trend(emptyList(), mapOf(("Cash" to "0000") to history), start, end)

        assertEquals(listOf(day(3), day(4), day(5)).map { it.toLocalDate() }, trend.getValue("EUR").map { it.date })
        assertEquals(listOf("100", "100", "100"), values(trend["EUR"]))
    }

    @Test
    fun `a manual account's past doesn't cross a change of its currency`() {
        // USD cash since October, spent USD 20 on Nov 2, then moved to EUR on Nov 3 at
        // EUR 100 and spent EUR 10 on Nov 4. The USD spending must never count as EUR.
        val history = NetWorth.derivedHistory(
            cash("EUR", "90", anchorAt = day(3, hour = 12)),
            listOf(
                BalanceEffect(day(2), d("-20"), "USD"),
                BalanceEffect(day(4), d("-10"), "EUR"),
            ),
            firstRow = LocalDateTime.of(2026, 10, 1, 9, 0),
        )
        val trend = NetWorth.trend(emptyList(), mapOf(("Cash" to "0000") to history), start, end)

        assertEquals(listOf(day(3), day(4), day(5)).map { it.toLocalDate() }, trend.getValue("EUR").map { it.date })
        assertEquals(listOf("100", "90", "90"), values(trend["EUR"]))
        assertTrue("USD" !in trend)
    }

    @Test
    fun `a manual account in one currency throughout keeps its whole past`() {
        val history = NetWorth.derivedHistory(
            cash("INR", "700", anchorAt = day(4)),
            listOf(BalanceEffect(day(3), d("-200"), "INR")),
            firstRow = LocalDateTime.of(2026, 10, 1, 9, 0),
        )
        val trend = NetWorth.trend(emptyList(), mapOf(("Cash" to "0000") to history), start, end)

        assertEquals(listOf("900", "900", "700", "700", "700"), values(trend["INR"]))
    }

    @Test
    fun `a manual account never given a balance leaves its currency with no line`() {
        val cash = DerivedHistory("INR", d("700"), anchored = false, effects = emptyList())
        val trend = NetWorth.trend(
            listOf(row("1111", "1000", day(1)), row("2222", "10", day(1), currency = "USD")),
            mapOf(("Cash" to "0000") to cash), start, end,
        )
        assertTrue("INR" !in trend)
        assertEquals(5, trend.getValue("USD").size)
    }

    @Test
    fun `no rows, or rows rebuilt mid-way, don't break it`() {
        assertTrue(NetWorth.trend(emptyList(), emptyMap(), start, end).isEmpty())
        assertTrue(NetWorth.trend(listOf(row("1111", "1", day(1))), emptyMap(), end, start).isEmpty())
        // The same row twice (read while a rescan rewrote it) is still one balance.
        val trend = NetWorth.trend(listOf(row("1111", "5", day(2)), row("1111", "5", day(2))), emptyMap(), start, end)
        assertEquals(listOf("5", "5", "5", "5"), values(trend["INR"]))
    }
}
