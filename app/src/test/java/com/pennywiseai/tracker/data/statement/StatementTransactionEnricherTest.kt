package com.pennywiseai.tracker.data.statement

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class StatementTransactionEnricherTest {

    private val baseTime = LocalDateTime.of(2025, 9, 1, 15, 2)

    @Test
    fun `matches same reference amount direction account and close timestamp`() {
        val sms = transaction(id = 1, merchantName = "UPI Transaction")
        val statement = transaction(
            id = 2,
            merchantName = "Sample Store",
            sender = "GPay PDF",
            dateTime = baseTime.plusMinutes(4)
        )

        assertTrue(StatementTransactionEnricher.isStatementMatch(sms, statement))
    }

    @Test
    fun `fallback matches generic transaction with same details and close timestamp`() {
        val sms = transaction(id = 1, merchantName = "UPI Transaction", reference = "")
        val statement = transaction(
            id = 2,
            merchantName = "Sample Store",
            sender = "GPay PDF",
            dateTime = baseTime.plusMinutes(4)
        )

        assertTrue(StatementTransactionEnricher.isFallbackStatementMatch(sms, statement))
    }

    @Test
    fun `fallback does not match specific merchant`() {
        val sms = transaction(id = 1, merchantName = "Specific Merchant", reference = "")
        val statement = transaction(
            id = 2,
            merchantName = "Sample Store",
            sender = "GPay PDF",
            dateTime = baseTime.plusMinutes(4)
        )

        assertFalse(StatementTransactionEnricher.isFallbackStatementMatch(sms, statement))
    }

    @Test
    fun `does not match amount mismatch`() {
        val sms = transaction(id = 1, amount = BigDecimal("450.00"))
        val statement = transaction(id = 2, amount = BigDecimal("451.00"))

        assertFalse(StatementTransactionEnricher.isStatementMatch(sms, statement))
    }

    @Test
    fun `does not match timestamp outside tolerance`() {
        val sms = transaction(id = 1, dateTime = baseTime)
        val statement = transaction(id = 2, dateTime = baseTime.plusMinutes(6))

        assertFalse(StatementTransactionEnricher.isStatementMatch(sms, statement))
    }

    @Test
    fun `does not match opposite direction`() {
        val sms = transaction(id = 1, transactionType = TransactionType.INCOME)
        val statement = transaction(id = 2, transactionType = TransactionType.EXPENSE)

        assertFalse(StatementTransactionEnricher.isStatementMatch(sms, statement))
    }

    @Test
    fun `enriches generic merchant and blank details`() {
        val sms = transaction(
            id = 1,
            merchantName = "UPI Transaction",
            description = null,
            fromAccount = null,
            toAccount = null
        )
        val statement = transaction(
            id = 2,
            merchantName = "Sample Store",
            description = "Google Pay statement",
            fromAccount = "wallet",
            toAccount = "2468"
        )

        val enriched = StatementTransactionEnricher.enrich(sms, statement)

        assertTrue(StatementTransactionEnricher.hasEnrichment(sms, enriched))
        assertEquals("Sample Store", enriched.merchantName)
        assertEquals("Google Pay statement", enriched.description)
        assertEquals("wallet", enriched.fromAccount)
        assertEquals("2468", enriched.toAccount)
    }

    @Test
    fun `does not overwrite specific existing merchant or notes`() {
        val sms = transaction(
            id = 1,
            merchantName = "Specific Merchant",
            description = "User note"
        )
        val statement = transaction(
            id = 2,
            merchantName = "Sample Store",
            description = "Google Pay statement"
        )

        val enriched = StatementTransactionEnricher.enrich(sms, statement)

        assertFalse(StatementTransactionEnricher.hasEnrichment(sms, enriched))
        assertEquals("Specific Merchant", enriched.merchantName)
        assertEquals("User note", enriched.description)
    }

    @Test
    fun `does not use generic statement values`() {
        val sms = transaction(id = 1, merchantName = "Unknown Merchant")
        val statement = transaction(id = 2, merchantName = "UPI Transaction")

        val enriched = StatementTransactionEnricher.enrich(sms, statement)

        assertFalse(StatementTransactionEnricher.hasEnrichment(sms, enriched))
        assertEquals("Unknown Merchant", enriched.merchantName)
    }

    private fun transaction(
        id: Long,
        amount: BigDecimal = BigDecimal("450.00"),
        transactionType: TransactionType = TransactionType.EXPENSE,
        reference: String = "111222333444",
        accountNumber: String = "2468",
        merchantName: String = "UPI Transaction",
        description: String? = null,
        fromAccount: String? = null,
        toAccount: String? = null,
        sender: String = "SIBSMS",
        dateTime: LocalDateTime = baseTime
    ): TransactionEntity = TransactionEntity(
        id = id,
        amount = amount,
        merchantName = merchantName,
        category = "Others",
        transactionType = transactionType,
        dateTime = dateTime,
        description = description,
        smsBody = "sample source",
        bankName = "South Indian Bank",
        smsSender = sender,
        accountNumber = accountNumber,
        balanceAfter = null,
        transactionHash = "hash-$id",
        currency = "INR",
        fromAccount = fromAccount,
        toAccount = toAccount,
        reference = reference
    )
}
