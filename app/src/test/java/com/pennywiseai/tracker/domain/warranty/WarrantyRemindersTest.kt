package com.pennywiseai.tracker.domain.warranty

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class WarrantyRemindersTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val yesterday = today.minusDays(1)

    private fun purchase(
        id: Long,
        until: LocalDate?,
        deleted: Boolean = false,
        // Long before any run, unless a test says otherwise.
        updatedAt: LocalDateTime = LocalDateTime.of(2025, 3, 1, 10, 0)
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal("899"),
        merchantName = "Synthetic store $id",
        category = "Shopping",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2025, 3, 1, 10, 0),
        transactionHash = "synthetic-warranty-$id",
        isDeleted = deleted,
        warrantyUntil = until,
        updatedAt = updatedAt
    )

    private fun dueIds(vararg txns: TransactionEntity, lastRun: LocalDate? = yesterday) =
        WarrantyReminders.due(txns.toList(), today, lastRun).map { it.id }

    @Test
    fun remindsThirtyDaysAndOneDayBefore() {
        assertEquals(
            listOf(1L, 2L),
            dueIds(purchase(1, today.plusDays(30)), purchase(2, today.plusDays(1)))
        )
    }

    @Test
    fun otherDaysStayQuiet() {
        assertEquals(
            emptyList<Long>(),
            dueIds(
                purchase(1, today.plusDays(29)),
                purchase(2, today.plusDays(31)),
                purchase(3, today.plusDays(2)),
                purchase(4, today),
                purchase(5, null)
            )
        )
    }

    @Test
    fun anEndedOrDeletedPurchaseIsNeverReminded() {
        // Both reminder days fell in a long gap, but the warranty ended yesterday.
        assertEquals(
            emptyList<Long>(),
            dueIds(purchase(1, yesterday), purchase(2, today.plusDays(1), deleted = true), lastRun = today.minusDays(60))
        )
    }

    @Test
    fun aMissedDayIsCaughtUp() {
        // The worker last ran three days ago; the 1-day reminder was due yesterday,
        // and the warranty still covers today.
        assertEquals(listOf(1L), dueIds(purchase(1, today), lastRun = today.minusDays(3)))
    }

    @Test
    fun bothRemindersInTheGapListThePurchaseOnce() {
        assertEquals(listOf(1L), dueIds(purchase(1, today.plusDays(1)), lastRun = today.minusDays(40)))
    }

    @Test
    fun aSecondRunTheSameDaySendsNothing() {
        assertTrue(dueIds(purchase(1, today.plusDays(30)), lastRun = today).isEmpty())
    }

    @Test
    fun firstRunOnlyLooksAtToday() {
        assertEquals(
            listOf(1L),
            dueIds(purchase(1, today.plusDays(30)), purchase(2, today.plusDays(29)), lastRun = null)
        )
    }

    @Test
    fun aClockSetBackStillRemindsToday() {
        assertEquals(listOf(1L), dueIds(purchase(1, today.plusDays(1)), lastRun = today.plusDays(5)))
    }

    /** The 30-day reminder counts calendar days, so a short February is no special case. */
    @Test
    fun countsCalendarDaysAcrossFebruary() {
        val feb = LocalDate.of(2026, 2, 27)
        val due = WarrantyReminders.due(listOf(purchase(1, LocalDate.of(2026, 3, 29))), feb, feb.minusDays(1))
        assertEquals(listOf(1L), due.map { it.id })
    }

    /** Set yesterday afternoon to end today: yesterday's 1-day run had passed, so today's catches it. */
    @Test
    fun aWarrantySetAfterItsReminderDayRunIsCaughtUp() {
        val setYesterdayAfternoon = yesterday.atTime(15, 0)
        assertEquals(
            listOf(1L, 2L),
            dueIds(
                purchase(1, today, updatedAt = setYesterdayAfternoon),
                purchase(2, today.plusDays(1), updatedAt = today.atTime(8, 0))
            )
        )
    }

    @Test
    fun aLateEditOnlyCatchesUpTheLastDayReminder() {
        val setYesterdayAfternoon = yesterday.atTime(15, 0)
        assertEquals(
            emptyList<Long>(),
            dueIds(
                // Two days or more out: the 1-day reminder will still come on its day.
                purchase(1, today.plusDays(2), updatedAt = setYesterdayAfternoon),
                purchase(2, today.plusDays(10), updatedAt = setYesterdayAfternoon),
                // Ended, or changed before the last run's day.
                purchase(3, yesterday, updatedAt = setYesterdayAfternoon),
                purchase(4, today, updatedAt = yesterday.minusDays(1).atTime(15, 0))
            )
        )
    }

    @Test
    fun namesThePurchaseByItsNoteThenItsMerchant() {
        val txn = purchase(1, today.plusDays(30))
        assertEquals("Synthetic store 1", WarrantyReminders.itemName(txn))
        assertEquals("Synthetic store 1", WarrantyReminders.itemName(txn.copy(description = "  ")))
        assertEquals("Laptop", WarrantyReminders.itemName(txn.copy(description = " Laptop \nextended cover")))
    }

    @Test
    fun leavesOutAMerchantThatSaysNothingAboutThePurchase() {
        val txn = purchase(1, today.plusDays(30))
        listOf("Unknown Merchant", "Unknown", "UPI Payment", "A/c XX1234", "Card **9876", "Acct 0000").forEach {
            assertNull(it, WarrantyReminders.itemName(txn.copy(merchantName = it)))
        }
        // A note still names it, and real shops that merely look close keep their name.
        assertEquals("Headphones", WarrantyReminders.itemName(txn.copy(merchantName = "Unknown Merchant", description = "Headphones")))
        assertEquals("AC Services", WarrantyReminders.itemName(txn.copy(merchantName = "AC Services")))
        assertEquals("Xbox Store", WarrantyReminders.itemName(txn.copy(merchantName = "Xbox Store")))
    }
}
