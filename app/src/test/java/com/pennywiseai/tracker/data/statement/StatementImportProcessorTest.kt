package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType as ParsedTransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

class StatementImportProcessorTest {

    private val baseTime = LocalDateTime.of(2025, 9, 1, 15, 2)

    @Test
    fun `referenced statement falls through to amount date dedup when reference has no match`() = runBlocking {
        val existingSmsWithoutReference = transaction(
            merchantName = "Specific Merchant",
            reference = null
        )
        val store = FakeTransactionStore(existingSmsWithoutReference)
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(parsedTransaction(reference = "190200588907"))
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(0, result.skippedByReference)
        assertEquals(1, result.skippedByAmountDate)
        assertTrue(store.inserted.isEmpty())
        assertEquals(1, store.rangeQueries)
    }

    @Test
    fun `amount date fallback enriches one generic transaction instead of inserting duplicate`() = runBlocking {
        val existingSmsWithoutReference = transaction(
            id = 8,
            merchantName = "UPI Transaction",
            description = null,
            reference = null
        )
        val store = FakeTransactionStore(existingSmsWithoutReference)
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(
                parsedTransaction(
                    merchant = "Swiggy",
                    reference = "190200588907"
                )
            )
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.enriched)
        assertEquals(0, result.skippedByAmountDate)
        assertEquals("Swiggy", store.updated.single().merchantName)
        assertTrue(store.inserted.isEmpty())
        assertEquals(1, store.rangeQueries)
    }

    @Test
    fun `amount date fallback enriches generic transaction when statement time does not match`() = runBlocking {
        val existingSmsWithoutReference = transaction(
            id = 8,
            merchantName = "UPI Transaction",
            description = null,
            reference = null,
            dateTime = baseTime
        )
        val store = FakeTransactionStore(existingSmsWithoutReference)
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(
                parsedTransaction(
                    merchant = "Swiggy",
                    reference = "190200588907",
                    timestamp = baseTime.toLocalDate()
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
            )
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.enriched)
        assertEquals(0, result.skippedByAmountDate)
        assertEquals("Swiggy", store.updated.single().merchantName)
        assertTrue(store.inserted.isEmpty())
        assertEquals(1, store.rangeQueries)
    }

    @Test
    fun `amount date fallback does not enrich opposite direction transaction`() = runBlocking {
        val store = FakeTransactionStore(
            transaction(
                id = 8,
                merchantName = "UPI Transaction",
                transactionType = TransactionType.INCOME,
                reference = null
            )
        )
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(
                parsedTransaction(
                    merchant = "Swiggy",
                    reference = "190200588907",
                    type = ParsedTransactionType.EXPENSE
                )
            )
        )

        // A same-amount credit is a different transaction (a refund, a reversal):
        // the debit is neither enriched onto it nor swallowed by it (#13).
        assertEquals(1, result.imported)
        assertEquals(0, result.enriched)
        assertEquals(0, result.skippedByAmountDate)
        assertTrue(store.updated.isEmpty())
    }

    @Test
    fun `hash duplicate enriches generic transaction instead of skipping`() = runBlocking {
        val existingGeneric = transaction(
            id = 8,
            merchantName = "UPI Transaction",
            description = null,
            reference = "190200588907"
        )
        val store = FakeTransactionStore(existingGeneric)
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(
                parsedTransaction(
                    merchant = "Swiggy",
                    reference = "190200588907",
                    transactionHash = existingGeneric.transactionHash
                )
            )
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.enriched)
        assertEquals(0, result.skippedByHash)
        assertEquals("Swiggy", store.updated.single().merchantName)
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun `amount date fallback does not enrich ambiguous generic transactions`() = runBlocking {
        val store = FakeTransactionStore(
            transaction(id = 8, merchantName = "UPI Transaction", reference = null),
            transaction(id = 9, merchantName = "UPI Transaction", reference = null)
        )
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(
                parsedTransaction(
                    merchant = "Swiggy",
                    reference = "190200588907"
                )
            )
        )

        assertEquals(0, result.imported)
        assertEquals(0, result.enriched)
        assertEquals(1, result.skippedByAmountDate)
        assertTrue(store.updated.isEmpty())
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun `referenced statement is inserted when neither reference nor amount date dedup matches`() = runBlocking {
        val store = FakeTransactionStore()
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(parsedTransaction(reference = "190200588907"))
        )

        assertEquals(1, result.imported)
        assertEquals(0, result.skippedDuplicates)
        assertEquals("190200588907", store.inserted.single().reference)
        // One read of the statement's date range, whatever matched.
        assertEquals(1, store.rangeQueries)
    }

    @Test
    fun `reference match enriches existing transaction instead of inserting duplicate`() = runBlocking {
        val existingSms = transaction(
            id = 7,
            merchantName = "UPI Transaction",
            description = null,
            reference = "190200588907"
        )
        val store = FakeTransactionStore(existingSms)
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(
                parsedTransaction(
                    merchant = "Swiggy",
                    reference = "190200588907"
                )
            )
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.enriched)
        assertEquals("Swiggy", store.updated.single().merchantName)
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun `wallet statements printing a shorter or zero-padded account still match the SMS`() = runBlocking {
        // Paytm prints "17" for the SMS's "4517"; Google Pay "0123" for an SMS's "123".
        for ((smsAccount, statementAccount) in listOf("4517" to "17", "123" to "0123")) {
            val store = FakeTransactionStore(
                transaction(merchantName = "Specific Merchant", reference = null, accountNumber = smsAccount)
            )
            val result = StatementImportProcessor(store).process(
                listOf(parsedTransaction(accountLast4 = statementAccount))
            )
            assertEquals(0, result.imported)
            assertEquals(1, result.skippedByAmountDate)
        }
    }

    @Test
    fun `a different account is still no match`() {
        assertFalse(StatementTransactionEnricher.accountsMatch("0517", "4517"))
        assertFalse(StatementTransactionEnricher.accountsMatch("7", "4517"))
        assertTrue(StatementTransactionEnricher.accountsMatch(null, "4517"))
    }

    @Test
    fun `a reference match doesn't need the accounts to agree`() = runBlocking {
        val store = FakeTransactionStore(transaction(merchantName = "Specific Merchant", accountNumber = "9999"))
        val result = StatementImportProcessor(store).process(listOf(parsedTransaction()))
        assertEquals(1, result.skippedByReference)
    }

    @Test
    fun `a timed row never matches an SMS on the next day`() = runBlocking {
        // GPay ₹500 on the 1st; an unrelated ₹500 SMS on the 2nd.
        val unrelated = transaction(amount = BigDecimal("500"), reference = null, dateTime = baseTime.plusDays(1))
        val store = FakeTransactionStore(unrelated)
        val result = StatementImportProcessor(store).process(
            listOf(parsedTransaction(amount = BigDecimal("500.00"), merchant = "Swiggy", reference = null))
        )
        assertEquals(1, result.imported)
        assertTrue(store.updated.isEmpty())
    }

    private class FakeTransactionStore(
        vararg stored: TransactionEntity
    ) : StatementImportProcessor.TransactionStore {
        private val stored = stored.toList()
        val inserted = mutableListOf<TransactionEntity>()
        val updated = mutableListOf<TransactionEntity>()
        var rangeQueries = 0

        override suspend fun getTransactionsByHashes(hashes: List<String>): List<TransactionEntity> =
            stored.filter { it.transactionHash in hashes }

        override suspend fun getTransactionsBetween(start: LocalDateTime, end: LocalDateTime): List<TransactionEntity> {
            rangeQueries++
            return stored.filter { !it.isDeleted && it.dateTime in start..end }
        }

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            updated += transaction
        }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            inserted += transactions
        }
    }

    private fun parsedTransaction(
        amount: BigDecimal = BigDecimal("450.00"),
        type: ParsedTransactionType = ParsedTransactionType.EXPENSE,
        merchant: String = "Sample Store",
        reference: String? = "190200588907",
        accountLast4: String = "2468",
        timestamp: Long = baseTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        transactionHash: String = "statement-hash"
    ): ParsedTransaction = ParsedTransaction(
        amount = amount,
        type = type,
        merchant = merchant,
        reference = reference,
        accountLast4 = accountLast4,
        balance = null,
        smsBody = "Google Pay statement row",
        sender = "GPAY_PDF",
        timestamp = timestamp,
        bankName = "Google Pay",
        transactionHash = transactionHash,
        currency = "INR"
    )

    private fun transaction(
        id: Long = 1,
        amount: BigDecimal = BigDecimal("450.00"),
        transactionType: TransactionType = TransactionType.EXPENSE,
        reference: String? = "190200588907",
        accountNumber: String? = "2468",
        merchantName: String = "UPI Transaction",
        description: String? = null,
        dateTime: LocalDateTime = baseTime
    ): TransactionEntity = TransactionEntity(
        id = id,
        amount = amount,
        merchantName = merchantName,
        category = "Others",
        transactionType = transactionType,
        dateTime = dateTime,
        description = description,
        smsBody = "Bank SMS",
        bankName = "South Indian Bank",
        smsSender = "SIBSMS",
        accountNumber = accountNumber,
        balanceAfter = null,
        transactionHash = "sms-hash-$id",
        currency = "INR",
        reference = reference
    )
}
