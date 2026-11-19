package com.pennywiseai.tracker.presentation.analytics

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class AnalyticsTrendTest {
    @Test
    fun `an All time trend starts at a refund older than the first spend`() {
        val tx = TransactionEntity(
            id = 1, amount = BigDecimal("100"), merchantName = "Test", category = "Others",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2024, 3, 10, 12, 0),
            transactionHash = "h1", currency = "INR"
        )
        val trend = calculateTrend(
            rows = listOf(CountedRow(tx, Money(tx.amount, "INR"))),
            dateRange = LocalDate.of(2020, 1, 1) to LocalDate.of(2026, 12, 31),
            period = TimePeriod.ALL,
            currency = "INR",
            refunds = mapOf(LocalDate.of(2024, 1, 15) to BigDecimal("30")),
            today = LocalDate.of(2026, 11, 20)
        )
        // Yearly buckets: 2024 is the spend net of the January refund.
        val first = trend.line.first()
        assertEquals(LocalDate.of(2024, 1, 1), first.timestamp.toLocalDate())
        assertEquals(0, BigDecimal("70").compareTo(first.balance))
    }
}
