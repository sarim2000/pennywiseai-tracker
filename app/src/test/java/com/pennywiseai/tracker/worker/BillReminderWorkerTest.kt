package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The reminder picks only active expense bills due exactly tomorrow. */
class BillReminderWorkerTest {

    private val today = LocalDate.of(2026, 3, 10)

    private fun sub(
        id: Long,
        due: LocalDate?,
        state: SubscriptionState = SubscriptionState.ACTIVE,
        direction: SubscriptionDirection = SubscriptionDirection.EXPENSE
    ) = SubscriptionEntity(
        id = id, merchantName = "Sub $id", amount = BigDecimal("10"), nextPaymentDate = due,
        state = state, currency = "SAR", direction = direction
    )

    @Test
    fun `only active expense subscriptions due tomorrow are reminded`() {
        val tomorrow = today.plusDays(1)
        val subs = listOf(
            sub(1, tomorrow),
            sub(2, today),
            sub(3, today.plusDays(2)),
            sub(4, tomorrow, direction = SubscriptionDirection.INCOME),
            sub(5, tomorrow, state = SubscriptionState.HIDDEN),
            sub(6, tomorrow, state = SubscriptionState.ENDED),
            sub(7, null)
        )
        assertEquals(listOf(1L), BillReminderWorker.dueTomorrow(subs, today).map { it.id })
    }
}
