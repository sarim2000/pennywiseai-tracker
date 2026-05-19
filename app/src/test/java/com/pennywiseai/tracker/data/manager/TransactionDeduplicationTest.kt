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
    fun `does not match outside duplicate window`() {
        val first = transaction(id = 1, dateTime = baseTime)
        val later = transaction(id = 2, dateTime = baseTime.plusMinutes(4))

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
    fun `cleanup keeps earliest transaction per matching time window`() {
        val transactions = listOf(
            transaction(id = 3, dateTime = baseTime.plusMinutes(2)),
            transaction(id = 1, dateTime = baseTime),
            transaction(id = 2, dateTime = baseTime.plusMinutes(1)),
            transaction(id = 4, dateTime = baseTime.plusMinutes(10)),
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
