package com.pennywiseai.tracker.data.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A fresh row for an account keeps what the user set on the account. */
class AccountFieldsTest {

    private val set = row(BigDecimal("900")).copy(
        statementDay = 12,
        cardDueDay = 2,
        cardBillAmount = BigDecimal("650.25"),
        cardBillAsOf = LocalDateTime.of(2026, 3, 14, 9, 0),
    )

    @Test
    fun `a new row carries the bill and statement day`() {
        val next = row(BigDecimal("950")).withAccountFieldsOf(set)

        assertEquals(BigDecimal("950"), next.balance)
        assertEquals(12, next.statementDay)
        assertEquals(2, next.cardDueDay)
        assertEquals(BigDecimal("650.25"), next.cardBillAmount)
        assertEquals(set.cardBillAsOf, next.cardBillAsOf)
    }

    @Test
    fun `a new currency drops the bill amount but keeps the due day`() {
        val next = row(BigDecimal("950")).copy(currency = "USD").withAccountFieldsOf(set)

        assertEquals(2, next.cardDueDay)
        assertEquals(null, next.cardBillAmount)
        assertEquals(null, next.cardBillAsOf)
        assertEquals(12, next.statementDay)
    }

    @Test
    fun `a first row has nothing to carry`() {
        val first = row(BigDecimal("10"))

        assertSame(first, first.withAccountFieldsOf(null))
    }

    @Test
    fun `an account with nothing set stays unknown`() {
        val next = row(BigDecimal("10")).copy(cardDueDay = 5).withAccountFieldsOf(row(BigDecimal("5")))

        assertEquals(null, next.cardDueDay)
        assertEquals(null, next.cardBillAmount)
    }

    private fun row(balance: BigDecimal) = AccountBalanceEntity(
        bankName = "Test Bank",
        accountLast4 = "0000",
        balance = balance,
        timestamp = LocalDateTime.of(2026, 3, 20, 10, 0),
        isCreditCard = true,
    )
}
