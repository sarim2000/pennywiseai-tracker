package com.pennywiseai.tracker.data.manager

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionDeduplicationTest {

    private val baseTime = LocalDateTime.of(2025, 12, 26, 19, 2, 1)

    @Test
    fun `matches same UPI reference from partner and account bank`() {
        val partnerBank = transaction(
            id = 1,
            bankName = "State Bank of India",
            dateTime = baseTime,
            balanceAfter = null
        )
        val accountBank = transaction(
            id = 2,
            bankName = "South Indian Bank",
            dateTime = baseTime.plusMinutes(2),
            balanceAfter = BigDecimal("34567.67")
        )

        assertTrue(TransactionDeduplication.isSameUpiTransaction(partnerBank, accountBank))
        assertTrue(TransactionDeduplication.shouldReplaceWithIncoming(partnerBank, accountBank))
    }

    @Test
    fun `does not replace account bank transaction with partner bank transaction`() {
        val accountBank = transaction(
            id = 1,
            bankName = "South Indian Bank",
            dateTime = baseTime,
            balanceAfter = null
        )
        val partnerBank = transaction(
            id = 2,
            bankName = "State Bank of India",
            dateTime = baseTime.plusMinutes(2),
            balanceAfter = BigDecimal("34567.67")
        )

        assertTrue(TransactionDeduplication.isSameUpiTransaction(accountBank, partnerBank))
        assertFalse(TransactionDeduplication.shouldReplaceWithIncoming(accountBank, partnerBank))
    }

    @Test
    fun `matches within 24 hour duplicate window for unique UPI references`() {
        val first = transaction(id = 1, dateTime = baseTime)
        val later = transaction(id = 2, dateTime = baseTime.plusHours(12))

        assertTrue(TransactionDeduplication.isSameUpiTransaction(first, later))
    }

    @Test
    fun `does not match outside 24 hour duplicate window`() {
        val first = transaction(id = 1, dateTime = baseTime)
        val later = transaction(id = 2, dateTime = baseTime.plusHours(25))

        assertFalse(TransactionDeduplication.isSameUpiTransaction(first, later))
    }

    @Test
    fun `does not match different account with same reference`() {
        val first = transaction(id = 1, accountNumber = "2468")
        val otherAccount = transaction(id = 2, accountNumber = "1357")

        assertFalse(TransactionDeduplication.isSameUpiTransaction(first, otherAccount))
    }

    @Test
    fun `does not match non UPI references`() {
        val first = transaction(id = 1, reference = "ABC123")
        val second = transaction(id = 2, reference = "ABC123")

        assertFalse(TransactionDeduplication.isSameUpiTransaction(first, second))
    }

    @Test
    fun `cleanup does not transitively cluster unrelated transactions`() {
        // A (0m), B (12h), C (25h) -> B is within 24h of A, but C is NOT within 24h of A (earliest of cluster).
        // If clustering was transitive, A, B, C would form a single cluster and C would get deleted.
        // With our fix, C is compared to A (the earliest) and since it is > 24h gap, it forms a new cluster.
        val transactions = listOf(
            transaction(id = 1, dateTime = baseTime),
            transaction(id = 2, dateTime = baseTime.plusHours(12)),
            transaction(id = 3, dateTime = baseTime.plusHours(25))
        )

        // Only 2 (duplicate of 1) should be deleted. 3 should be kept!
        assertEquals(listOf(2L), TransactionDeduplication.duplicateIdsToDelete(transactions))
    }

    @Test
    fun `cleanup keeps earliest transaction per matching time window`() {
        val transactions = listOf(
            transaction(id = 3, dateTime = baseTime.plusMinutes(2)),
            transaction(id = 1, dateTime = baseTime),
            transaction(id = 2, dateTime = baseTime.plusMinutes(1)),
            // Use different UPI reference so 4 is not deduped against 1
            transaction(id = 4, reference = "999888777666", dateTime = baseTime.plusMinutes(10)),
            transaction(id = 5, amount = BigDecimal("15001.00")),
            transaction(id = 6, accountNumber = "1357")
        )

        assertEquals(listOf(2L, 3L), TransactionDeduplication.duplicateIdsToDelete(transactions))
    }

    @Test
    fun `cleanup catches duplicate across midnight within matching window`() {
        val beforeMidnight = LocalDateTime.of(2025, 12, 26, 23, 59, 0)
        val transactions = listOf(
            transaction(id = 1, dateTime = beforeMidnight),
            transaction(id = 2, dateTime = beforeMidnight.plusMinutes(2))
        )

        assertEquals(listOf(2L), TransactionDeduplication.duplicateIdsToDelete(transactions))
    }

    @Test
    fun `cleanup keeps account bank over earlier partner bank duplicate`() {
        val transactions = listOf(
            transaction(
                id = 1,
                bankName = "State Bank of India",
                dateTime = baseTime,
                balanceAfter = null
            ),
            transaction(
                id = 2,
                bankName = "South Indian Bank",
                dateTime = baseTime.plusMinutes(2),
                balanceAfter = BigDecimal("34567.67")
            )
        )

        assertEquals(listOf(1L), TransactionDeduplication.duplicateIdsToDelete(transactions))
    }

    @Test
    fun `cleanup keeps balance bearing transaction when bank priority is equal`() {
        val transactions = listOf(
            transaction(
                id = 1,
                bankName = "South Indian Bank",
                dateTime = baseTime,
                balanceAfter = null
            ),
            transaction(
                id = 2,
                bankName = "South Indian Bank",
                dateTime = baseTime.plusMinutes(1),
                balanceAfter = BigDecimal("34567.67")
            )
        )

        assertEquals(listOf(1L), TransactionDeduplication.duplicateIdsToDelete(transactions))
    }

    private fun transaction(
        id: Long,
        amount: BigDecimal = BigDecimal("15000.00"),
        accountNumber: String = "2468",
        bankName: String = "South Indian Bank",
        reference: String = "111222333444",
        dateTime: LocalDateTime = baseTime,
        balanceAfter: BigDecimal? = BigDecimal("34567.67")
    ): TransactionEntity = TransactionEntity(
        id = id,
        amount = amount,
        merchantName = "Sample Merchant",
        category = "Others",
        transactionType = TransactionType.INCOME,
        dateTime = dateTime,
        smsBody = "sample sms",
        bankName = bankName,
        smsSender = "SIBSMS",
        accountNumber = accountNumber,
        balanceAfter = balanceAfter,
        transactionHash = "hash-$id",
        currency = "INR",
        reference = reference
    )
}
