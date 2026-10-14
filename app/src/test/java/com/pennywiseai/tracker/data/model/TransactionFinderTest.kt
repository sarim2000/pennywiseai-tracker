package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** #170: "delete the starbucks one from yesterday" has to land on the right row. */
class TransactionFinderTest {
    private val today = LocalDate.of(2026, 9, 15)
    private fun tx(id: Long, merchant: String, amount: String, daysAgo: Int, category: String = "Others") = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = merchant, category = category, transactionType = TransactionType.EXPENSE,
        dateTime = today.minusDays(daysAgo.toLong()).atTime(12, 0), transactionHash = "h$id"
    )
    private val txs = listOf(
        tx(1, "Starbucks", "120", 1, "Food & Dining"),
        tx(2, "Starbucks", "340", 5, "Food & Dining"),
        tx(3, "Uber", "850", 1, "Transportation"),
        tx(4, "Big Basket", "1250", 0, "Groceries")
    )

    @Test
    fun `words plus day pick the right one`() {
        assertEquals(1L, TransactionFinder.findBest(txs, "starbucks coffee", null, 1, today)!!.id)
        assertEquals(3L, TransactionFinder.findBest(txs, "the uber", null, 1, today)!!.id)
    }

    @Test
    fun `amount disambiguates same merchant`() {
        assertEquals(2L, TransactionFinder.findBest(txs, "starbucks", BigDecimal("340"), null, today)!!.id)
    }

    @Test
    fun `words alone prefer the most recent match`() {
        assertEquals(1L, TransactionFinder.findBest(txs, "starbucks", null, null, today)!!.id)
    }

    @Test
    fun `a stated amount or day that matches nothing returns nothing`() {
        assertNull(TransactionFinder.findBest(txs, "starbucks", BigDecimal("999"), null, today))   // no Starbucks at 999
        assertNull(TransactionFinder.findBest(txs, "starbucks", null, 3, today))                   // none 3 days ago
    }

    @Test
    fun `no overlap means nothing`() {
        assertNull(TransactionFinder.findBest(txs, "netflix", null, null, today))
    }

    // #876 follow-up: the on-device model sent days_ago=0 for "change the Swiggy
    // transaction category to Groceries", which hid the 26 Sep row.
    @Test
    fun `a day the user never said is ignored`() {
        assertNull(TransactionFinder.statedDaysAgo(0, "change the Swiggy transaction category to Groceries"))
        assertNull(TransactionFinder.statedDaysAgo(-18, "Swiggy 450 should be Groceries"))
    }

    @Test
    fun `today, yesterday and N days ago come from the user's words`() {
        assertEquals(0, TransactionFinder.statedDaysAgo(5, "delete the tea from today"))
        assertEquals(1, TransactionFinder.statedDaysAgo(0, "the starbucks one from yesterday"))
        assertEquals(18, TransactionFinder.statedDaysAgo(0, "swiggy from 18 days ago"))
    }

    @Test
    fun `other day phrases keep the model's value`() {
        assertEquals(3, TransactionFinder.statedDaysAgo(3, "the uber on monday"))
        assertEquals(19, TransactionFinder.statedDaysAgo(19, "swiggy on 26 sep"))
    }

    @Test
    fun `an amount counts only if the user said it`() {
        assertEquals(BigDecimal("450.0"), TransactionFinder.statedAmount(BigDecimal("450.0"), "Swiggy 450 should be Groceries"))
        assertEquals(BigDecimal("1250.0"), TransactionFinder.statedAmount(BigDecimal("1250.0"), "the 1,250 one"))
        assertNull(TransactionFinder.statedAmount(BigDecimal("450.0"), "change the Swiggy transaction category to Groceries"))
        assertNull(TransactionFinder.statedAmount(BigDecimal.ZERO, "swiggy 0"))
    }

    @Test
    fun `with the guessed day dropped the older row is found`() {
        val day = TransactionFinder.statedDaysAgo(0, "change the starbucks transaction to groceries")
        assertEquals(1L, TransactionFinder.findBest(txs, "Starbucks", null, day, today)?.id)
    }
}
