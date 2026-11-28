package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.domain.spending.Bill
import com.pennywiseai.tracker.domain.spending.BillSource
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NextBillsWidgetDataTest {

    private val today = LocalDate.of(2026, 11, 23)

    private var nextId = 1L

    /** A new schedule each call, unless [id] names one; a card bill has none, as loaded. */
    private fun bill(
        name: String,
        amount: String,
        currency: String,
        inDays: Long,
        source: BillSource? = BillSource.Subscription,
        id: Long = nextId++,
    ) = Bill(name, Money(BigDecimal(amount), currency), today.plusDays(inDays), source, seriesId = id.takeIf { source !is BillSource.Card })

    @Test
    fun `nothing due gives no rows`() {
        assertTrue(nextBills(emptyList()).isEmpty())
    }

    @Test
    fun `soonest first and only the first three`() {
        val bills = listOf(
            bill("Gym", "30", "USD", 9),
            bill("Streaming", "12.99", "USD", 1),
            bill("Rent", "900", "EUR", 4),
            bill("Phone", "15", "USD", 0),
            bill("Cloud", "2.99", "USD", 20),
        )

        val items = nextBills(bills)

        assertEquals(listOf("Phone", "Streaming", "Rent"), items.map { it.name })
        assertEquals(
            listOf(today, today.plusDays(1), today.plusDays(4)).map { it.toEpochDay() },
            items.map { it.dueEpochDay },
        )
    }

    @Test
    fun `each row keeps its own currency and exact amount`() {
        val items = nextBills(
            listOf(
                bill("Streaming", "12.99", "USD", 1),
                bill("Rent", "900.50", "EUR", 2),
                bill("Card", "8450", "INR", 3, BillSource.Card("Bank", "0000")),
            )
        )

        assertEquals(listOf("USD", "EUR", "INR"), items.map { it.currency })
        assertEquals(listOf("12.99", "900.50", "8450"), items.map { it.amount })
        assertEquals(BigDecimal("900.50"), items[1].amountValue)
    }

    @Test
    fun `a daily payment shows once, at its next date`() {
        val daily = (0L..5L).map { bill("Parking", "5", "USD", it, BillSource.Recurring, id = 100) }
        val items = nextBills(daily + bill("Streaming", "12.99", "USD", 2) + bill("Gym", "30", "USD", 3))

        assertEquals(listOf("Parking", "Streaming", "Gym"), items.map { it.name })
        assertEquals(today.toEpochDay(), items.first().dueEpochDay)
    }

    @Test
    fun `two cards' bills are two rows, and an overdue one comes first`() {
        val items = nextBills(
            listOf(
                bill("Bank ••1111", "100", "USD", 5, BillSource.Card("Bank", "1111")),
                bill("Bank ••2222", "200", "USD", -2, BillSource.Card("Bank", "2222")),
            )
        )

        assertEquals(listOf("Bank ••2222", "Bank ••1111"), items.map { it.name })
    }

    @Test
    fun `two separate payments alike in name, amount and currency are both listed`() {
        val items = nextBills(listOf(bill("Gym", "30", "USD", 2), bill("Gym", "30", "USD", 9)))

        assertEquals(listOf(2L, 9L).map { today.plusDays(it).toEpochDay() }, items.map { it.dueEpochDay })
    }

    @Test
    fun `an overdue subscription shows once, on its overdue date`() {
        val items = nextBills(listOf(bill("Streaming", "12.99", "USD", -3, id = 100), bill("Streaming", "12.99", "USD", 27, id = 100)))

        assertEquals(listOf(today.minusDays(3).toEpochDay()), items.map { it.dueEpochDay })
    }

    @Test
    fun `same name in another currency is another payment`() {
        val items = nextBills(listOf(bill("Streaming", "10", "USD", 1), bill("Streaming", "10", "EUR", 2)))

        assertEquals(2, items.size)
    }

    @Test
    fun `snapshot survives a round trip and a bad one reads as not built`() {
        val snapshot = NextBillsSnapshot(today.toEpochDay(), nextBills(listOf(bill("Streaming", "12.99", "USD", 1))))

        assertEquals(snapshot, NextBillsWidgetDataStore.decode(NextBillsWidgetDataStore.encode(snapshot)))
        assertTrue(snapshot.isBuilt)
        assertFalse(NextBillsWidgetDataStore.decode("not json").isBuilt)
        assertFalse(NextBillsWidgetDataStore.decode(null).isBuilt)
    }
}
