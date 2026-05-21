package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType as ParsedTransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        val store = FakeTransactionStore(
            amountDateMatches = listOf(existingSmsWithoutReference)
        )
        val processor = StatementImportProcessor(store)

        val result = processor.process(
            listOf(parsedTransaction(reference = "190200588907"))
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(0, result.skippedByReference)
        assertEquals(1, result.skippedByAmountDate)
        assertTrue(store.inserted.isEmpty())
        assertEquals(1, store.amountDateQueries.size)
    }

    @Test
    fun `amount date fallback enriches one generic transaction instead of inserting duplicate`() = runBlocking {
        val existingSmsWithoutReference = transaction(
            id = 8,
            merchantName = "UPI Transaction",
            description = null,
            reference = null
        )
        val store = FakeTransactionStore(
            amountDateMatches = listOf(existingSmsWithoutReference)
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
        assertEquals(1, result.enriched)
        assertEquals(0, result.skippedByAmountDate)
        assertEquals("Swiggy", store.updated.single().merchantName)
        assertTrue(store.inserted.isEmpty())
        assertEquals(1, store.amountDateQueries.size)
    }

    @Test
    fun `amount date fallback does not enrich ambiguous generic transactions`() = runBlocking {
        val store = FakeTransactionStore(
            amountDateMatches = listOf(
                transaction(id = 8, merchantName = "UPI Transaction", reference = null),
                transaction(id = 9, merchantName = "UPI Transaction", reference = null)
            )
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
        assertEquals(1, store.amountDateQueries.size)
    }

    @Test
    fun `reference match enriches existing transaction instead of inserting duplicate`() = runBlocking {
        val existingSms = transaction(
            id = 7,
            merchantName = "UPI Transaction",
            description = null,
            reference = "190200588907"
        )
        val store = FakeTransactionStore(mergeCandidate = existingSms)
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
        assertTrue(store.amountDateQueries.isEmpty())
    }

    private class FakeTransactionStore(
        private val hashMatch: TransactionEntity? = null,
        private val mergeCandidate: TransactionEntity? = null,
        private val amountDateMatches: List<TransactionEntity> = emptyList()
    ) : StatementImportProcessor.TransactionStore {
        val inserted = mutableListOf<TransactionEntity>()
        val updated = mutableListOf<TransactionEntity>()
        val amountDateQueries = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

        override suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
            hashMatch

        override suspend fun findStatementMergeCandidate(
            transaction: TransactionEntity
        ): TransactionEntity? =
            mergeCandidate

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            updated += transaction
        }

        override suspend fun getTransactionByAmountAndDate(
            amount: BigDecimal,
            dateStart: LocalDateTime,
            dateEnd: LocalDateTime
        ): List<TransactionEntity> {
            amountDateQueries += dateStart to dateEnd
            return amountDateMatches
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
