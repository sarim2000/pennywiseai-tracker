package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Both ways a payment is written (a bank charge, mark-paid) count it and end a plan on its last (#1061). */
class SubscriptionPlanEndTest {
    private val dao = mockk<SubscriptionDao>(relaxed = true)
    private val repository = SubscriptionRepository(dao, mockk())
    private val due = LocalDate.of(2026, 12, 5)

    private fun plan(made: Int) = SubscriptionEntity(
        id = 7, merchantName = "Synthetic EMI", amount = BigDecimal("1500"), nextPaymentDate = due,
        totalPayments = 3, paymentsMade = made,
    ).also { coEvery { dao.getSubscriptionById(7) } returns it }

    @Test
    fun `a bank charge for a plan's last payment ends it where it stands`() = runBlocking {
        repository.recordCharge(plan(made = 2), due)

        coVerify { dao.markPaid(7, due, due, 3, SubscriptionState.ENDED) }
    }

    @Test
    fun `mark paid counts a payment that isn't the last and moves the schedule`() = runBlocking {
        plan(made = 1)
        val next = LocalDate.of(2027, 1, 5)

        repository.markPaid(7, due, next)

        coVerify { dao.markPaid(7, due, next, 2, SubscriptionState.ACTIVE) }
    }
}
