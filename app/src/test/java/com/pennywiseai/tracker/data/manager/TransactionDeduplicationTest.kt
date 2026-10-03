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

    @Test
    fun `isSameCharge matches same bank and merchant across channels`() {
        val smsStored = transaction(id = 1, merchantName = "Sample Merchant")
        val notificationStored = transaction(id = 2, merchantName = "sample merchant")

        assertTrue(TransactionDeduplication.isSameCharge(smsStored, notificationStored))
    }

    @Test
    fun `isSameCharge ignores distinct merchants sharing bank and amount`() {
        val first = transaction(id = 1, merchantName = "Coffee Co")
        val second = transaction(id = 2, merchantName = "Grocery Co")

        assertFalse(TransactionDeduplication.isSameCharge(first, second))
    }

    @Test
    fun `isSameCharge ignores other banks`() {
        val first = transaction(id = 1)
        val second = transaction(id = 2, bankName = "State Bank of India")

        assertFalse(TransactionDeduplication.isSameCharge(first, second))
    }

    @Test
    fun `sms arriving after the app notification is the same charge`() {
        val aliases = setOf("Huntington")
        val fromNotification = transaction(id = 1, smsSender = "Huntington")
        val sms = transaction(id = 2, smsSender = "446622")

        assertTrue(TransactionDeduplication.isBookedByNotification(sms, listOf(fromNotification), aliases))
    }

    @Test
    fun `two identical sms charges are both kept`() {
        // Two real purchases a minute apart — only a notification-booked row counts.
        val aliases = setOf("Huntington")
        val firstSms = transaction(id = 1, smsSender = "446622")
        val secondSms = transaction(id = 2, smsSender = "446622")

        assertFalse(TransactionDeduplication.isBookedByNotification(secondSms, listOf(firstSms), aliases))
    }

    @Test
    fun `a notification is not checked against itself on the sms side`() {
        val aliases = setOf("Huntington")
        val existing = transaction(id = 1, smsSender = "Huntington")
        val incomingNotification = transaction(id = 2, smsSender = "Huntington")

        assertFalse(TransactionDeduplication.isBookedByNotification(incomingNotification, listOf(existing), aliases))
    }

    @Test
    fun `equal amounts in different currencies are different charges`() {
        val gbp = transaction(id = 1, currency = "GBP")
        val eur = transaction(id = 2, currency = "EUR")

        assertFalse(TransactionDeduplication.isSameCharge(gbp, eur))
    }

    private fun transaction(
        id: Long,
        amount: BigDecimal = BigDecimal("15000.00"),
        accountNumber: String = "2468",
        bankName: String = "South Indian Bank",
        reference: String = "111222333444",
        dateTime: LocalDateTime = baseTime,
        balanceAfter: BigDecimal? = BigDecimal("34567.67"),
        merchantName: String = "Sample Merchant",
        smsSender: String = "SIBSMS",
        currency: String = "INR"
    ): TransactionEntity = TransactionEntity(
        id = id,
        amount = amount,
        merchantName = merchantName,
        category = "Others",
        transactionType = TransactionType.INCOME,
        dateTime = dateTime,
        smsBody = "sample sms",
        bankName = bankName,
        smsSender = smsSender,
        accountNumber = accountNumber,
        balanceAfter = balanceAfter,
        transactionHash = "hash-$id",
        currency = currency,
        reference = reference
    )
}
