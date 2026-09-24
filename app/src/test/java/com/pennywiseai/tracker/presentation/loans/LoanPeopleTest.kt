package com.pennywiseai.tracker.presentation.loans

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class LoanPeopleTest {

    private val t0 = LocalDateTime.of(2026, 9, 1, 10, 0)

    private fun loan(
        id: Long,
        name: String,
        direction: LoanDirection,
        remaining: String,
        currency: String = "INR",
        status: LoanStatus = LoanStatus.ACTIVE,
        updatedDay: Long = 0
    ) = LoanEntity(
        id = id,
        personName = name,
        direction = direction,
        originalAmount = BigDecimal(remaining).max(BigDecimal("1")),
        remainingAmount = BigDecimal(remaining),
        currency = currency,
        status = status,
        updatedAt = t0.plusDays(updatedDay)
    )

    @Test
    fun `nets lent minus borrowed per currency and never mixes currencies`() {
        val people = groupLoansByPerson(
            listOf(
                loan(1, "Asha", LoanDirection.LENT, "2000"),
                loan(2, "Asha", LoanDirection.BORROWED, "500"),
                loan(3, "Asha", LoanDirection.LENT, "50", currency = "USD")
            )
        )
        assertEquals(1, people.size)
        val net = people.single().net
        assertEquals(0, BigDecimal("1500").compareTo(net.getValue("INR").amount))
        assertEquals(0, BigDecimal("50").compareTo(net.getValue("USD").amount))
    }

    @Test
    fun `names that differ only in case stay separate people`() {
        val people = groupLoansByPerson(
            listOf(loan(1, "Rahul", LoanDirection.LENT, "100"), loan(2, "rahul", LoanDirection.LENT, "200"))
        )
        assertEquals(2, people.size)
    }

    @Test
    fun `equal open balances net to nothing but stay active`() {
        val p = groupLoansByPerson(
            listOf(loan(1, "Kim", LoanDirection.LENT, "500"), loan(2, "Kim", LoanDirection.BORROWED, "500"))
        ).single()
        assertTrue(p.hasActive)
        assertTrue(p.net.isEmpty())
    }

    @Test
    fun `settled loans do not count and people with open loans come first`() {
        val people = groupLoansByPerson(
            listOf(
                loan(1, "Old", LoanDirection.LENT, "0", status = LoanStatus.SETTLED, updatedDay = 9),
                loan(2, "Ravi", LoanDirection.BORROWED, "300", updatedDay = 1),
                loan(3, "Ravi", LoanDirection.LENT, "0", status = LoanStatus.SETTLED, updatedDay = 5)
            )
        )
        assertEquals(listOf("Ravi", "Old"), people.map { it.name })
        val ravi = people.first()
        assertTrue(ravi.hasActive)
        assertEquals(LoanStatus.ACTIVE, ravi.loans.first().status)
        assertEquals(0, BigDecimal("-300").compareTo(ravi.net.getValue("INR").amount))
        assertFalse(people.last().hasActive)
        assertTrue(people.last().net.isEmpty())
    }
}
