package com.pennywiseai.tracker.ui.screens.onboarding

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class QuickSetupDetectionTest {

    private var nextId = 0L

    private fun tx(
        date: String,
        amount: String,
        merchant: String = "Shop",
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Others",
        currency: String = "JOD"
    ) = TransactionEntity(
        id = ++nextId, amount = BigDecimal(amount), merchantName = merchant, category = category,
        transactionType = type, dateTime = LocalDate.parse(date).atTime(10, 0),
        transactionHash = "h$nextId", currency = currency
    )

    private val today = LocalDate.parse("2026-10-29")

    @Test
    fun `payday is the earliest day of the monthly salary`() {
        val txs = listOf(
            tx("2026-08-26", "900", type = TransactionType.INCOME, category = "Salary"),
            tx("2026-09-25", "900", type = TransactionType.INCOME, category = "Salary"),
            tx("2026-10-27", "900", type = TransactionType.INCOME, category = "Salary"),
            // A bigger one-off credit that isn't salary doesn't move it.
            tx("2026-10-03", "5000", type = TransactionType.INCOME, category = "Income")
        )
        assertEquals(25, detectPayday(txs, salaryCategory = "Salary"))
    }

    @Test
    fun `payday falls back to the biggest monthly credit when nothing is categorised as salary`() {
        val txs = listOf(
            tx("2026-09-01", "20", type = TransactionType.INCOME),
            tx("2026-09-28", "900", type = TransactionType.INCOME),
            tx("2026-10-28", "900", type = TransactionType.INCOME)
        )
        assertEquals(28, detectPayday(txs, salaryCategory = "Salary"))
    }

    @Test
    fun `scattered or single-month income gives no payday`() {
        assertNull(detectPayday(listOf(tx("2026-10-25", "900", type = TransactionType.INCOME)), "Salary"))
        val scattered = listOf(
            tx("2026-09-03", "900", type = TransactionType.INCOME),
            tx("2026-10-20", "900", type = TransactionType.INCOME)
        )
        assertNull(detectPayday(scattered, "Salary"))
    }

    @Test
    fun `monthly steady charges are suggested with the next due date`() {
        val txs = listOf(
            tx("2026-08-15", "8.99", merchant = "Netflix", category = "Entertainment"),
            tx("2026-09-15", "8.99", merchant = "Netflix", category = "Entertainment"),
            tx("2026-10-15", "8.99", merchant = "NETFLIX ", category = "Entertainment")
        )
        val found = detectRecurring(txs, knownNames = emptySet(), today = today)
        assertEquals(1, found.size)
        assertEquals("NETFLIX", found[0].name)
        assertEquals(LocalDate.parse("2026-11-15"), found[0].nextPaymentDate)
        assertEquals("Entertainment", found[0].category)
    }

    @Test
    fun `irregular, varying, stale or known charges are not suggested`() {
        val txs = listOf(
            // Weekly, not monthly.
            tx("2026-10-01", "5", merchant = "Cafe"), tx("2026-10-08", "5", merchant = "Cafe"),
            // Monthly but the amount swings.
            tx("2026-09-10", "40", merchant = "Grocer"), tx("2026-10-10", "95", merchant = "Grocer"),
            // Stopped months ago.
            tx("2026-05-01", "10", merchant = "Old"), tx("2026-06-01", "10", merchant = "Old"),
            // Already tracked.
            tx("2026-09-05", "12", merchant = "Spotify"), tx("2026-10-05", "12", merchant = "Spotify")
        )
        assertEquals(emptyList<RecurringCandidate>(), detectRecurring(txs, knownNames = setOf("spotify"), today = today))
    }
}

class QuickSetupMergeTest {
    private fun choice(name: String, selected: Boolean = false, fromScan: Boolean = true) = SubscriptionChoice(
        name, BigDecimal("5"), "JOD", "Others", LocalDate.parse("2026-11-01"), selected, fromScan = fromScan
    )

    @Test
    fun `scan findings arriving late keep what the user already did`() {
        val typed = choice("Netflix", selected = true, fromScan = false)
        val state = OnBoardingUiState(paydayDay = 27, paydayPicked = true, subscriptions = listOf(typed))
        val merged = state.withScanFindings(payday = 25, cycleDay = 1, found = listOf(choice("netflix"), choice("Gym")))
        assertEquals(27, merged.paydayDay)
        assertEquals(listOf(choice("Gym"), typed), merged.subscriptions)
    }

    @Test
    fun `untouched payday takes the detected day, else the current cycle day`() {
        assertEquals(25, OnBoardingUiState().withScanFindings(25, cycleDay = 1, found = emptyList()).paydayDay)
        val none = OnBoardingUiState().withScanFindings(null, cycleDay = 10, found = emptyList())
        assertEquals(10, none.paydayDay)
        assertEquals(false, none.paydayDetected)
    }
}
