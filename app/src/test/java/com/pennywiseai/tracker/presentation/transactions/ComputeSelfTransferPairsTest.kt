package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Unit tests for [computeSelfTransferPairs] — the self-transfer suggestion
 * heuristic. Focus is #611: account-less manual rows must be pairable.
 */
class ComputeSelfTransferPairsTest {

    private val base: LocalDateTime = LocalDateTime.of(2026, 8, 1, 12, 0)

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: String = "500",
        minutesFromBase: Long = 0,
        bankName: String? = null,
        accountNumber: String? = null,
        currency: String = "INR",
    ): TransactionEntity = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "M",
        category = "Others",
        transactionType = type,
        dateTime = base.plusMinutes(minutesFromBase),
        bankName = bankName,
        accountNumber = accountNumber,
        transactionHash = "hash-$id",
        currency = currency,
    )

    /** #611: two manual rows with NO account, same amount, within window → suggested. */
    @Test
    fun `account-less manual expense and income are paired`() {
        val expense = tx(1, TransactionType.EXPENSE)
        val income = tx(2, TransactionType.INCOME, minutesFromBase = 5)

        val pairs = computeSelfTransferPairs(listOf(expense, income))

        assertEquals(2L, pairs[1L])
        assertEquals(1L, pairs[2L])
    }

    /** Same KNOWN account is not a transfer — must NOT be suggested. */
    @Test
    fun `same known account is not paired`() {
        val expense = tx(1, TransactionType.EXPENSE, bankName = "HDFC", accountNumber = "1234")
        val income = tx(2, TransactionType.INCOME, minutesFromBase = 5, bankName = "HDFC", accountNumber = "1234")

        val pairs = computeSelfTransferPairs(listOf(expense, income))

        assertTrue(pairs.isEmpty())
    }

    /** Different known accounts → suggested (unchanged existing behaviour). */
    @Test
    fun `different known accounts are paired`() {
        val expense = tx(1, TransactionType.EXPENSE, bankName = "HDFC", accountNumber = "1234")
        val income = tx(2, TransactionType.INCOME, minutesFromBase = 5, bankName = "ICICI", accountNumber = "9999")

        val pairs = computeSelfTransferPairs(listOf(expense, income))

        assertEquals(2L, pairs[1L])
        assertEquals(1L, pairs[2L])
    }

    /** One account-less leg + one known-account leg → still a valid suggestion. */
    @Test
    fun `mixed known and account-less legs are paired`() {
        val expense = tx(1, TransactionType.EXPENSE) // manual, no account
        val income = tx(2, TransactionType.INCOME, minutesFromBase = 5, bankName = "HDFC", accountNumber = "1234")

        val pairs = computeSelfTransferPairs(listOf(expense, income))

        assertEquals(2L, pairs[1L])
    }

    /** Outside the match window → not suggested. */
    @Test
    fun `pair outside the time window is not paired`() {
        val expense = tx(1, TransactionType.EXPENSE)
        val income = tx(2, TransactionType.INCOME, minutesFromBase = 120) // 2h > 60m window

        val pairs = computeSelfTransferPairs(listOf(expense, income))

        assertTrue(pairs.isEmpty())
    }

    /** Amount mismatch → not suggested. */
    @Test
    fun `different amounts are not paired`() {
        val expense = tx(1, TransactionType.EXPENSE, amount = "500")
        val income = tx(2, TransactionType.INCOME, amount = "600", minutesFromBase = 5)

        val pairs = computeSelfTransferPairs(listOf(expense, income))

        assertTrue(pairs.isEmpty())
    }
}
