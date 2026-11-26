package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.service.RuleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import com.pennywiseai.parser.core.TransactionType as ParsedTransactionType

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

    @Test
    fun `a statement credit without a reference matches a marked pair's incoming leg`() = runBlocking {
        val incomingLeg = transaction(transactionType = TransactionType.TRANSFER, reference = null)
            .copy(fromAccount = "1111", toAccount = "2468", transferPairId = "pair")
        val credit = parsedTransaction(type = ParsedTransactionType.INCOME, reference = null)
        val debit = parsedTransaction(type = ParsedTransactionType.EXPENSE, reference = null)

        val processor = StatementImportProcessor(FakeTransactionStore(incomingLeg))

        assertEquals(1, processor.plan(listOf(credit)).matchedCount)
        // The same amount going out is a different payment.
        assertEquals(0, processor.plan(listOf(debit)).matchedCount)
    }

    // --- Learned mappings and rules on new rows ---

    private fun merchantRule(merchant: String, vararg actions: RuleAction) = TransactionRule(
        name = "$merchant rule",
        conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, merchant)),
        actions = actions.toList()
    )

    private fun importRules(
        mappings: Map<String, String> = emptyMap(),
        incomeCategories: Map<String, Boolean> = emptyMap(),
        vararg rules: TransactionRule
    ) = ImportRules(mappings, incomeCategories, rules.toList(), RuleEngine())

    @Test
    fun `a learned mapping categorizes new rows but never an enriched one`() = runBlocking {
        val generic = transaction(id = 8, merchantName = "UPI Transaction", reference = null)
        val store = FakeTransactionStore(generic)
        val rules = importRules(mappings = mapOf("Coffee Hut" to "Snacks", "Swiggy" to "Groceries"))

        val result = StatementImportProcessor(store, rules).process(
            listOf(
                // Matches the stored SMS by amount and date: enriched, its category left alone.
                parsedTransaction(merchant = "Swiggy", reference = null, transactionHash = "a"),
                parsedTransaction(merchant = "Coffee Hut", amount = BigDecimal("120.00"), reference = null, transactionHash = "b")
            )
        )

        assertEquals(1, result.enriched)
        assertEquals("Others", store.updated.single().category)
        assertEquals("Snacks", store.inserted.single().category)
        assertTrue(store.recorded.isEmpty())
    }

    @Test
    fun `a mapping to an income category leaves a payment alone`() = runBlocking {
        val store = FakeTransactionStore()
        val rules = importRules(mappings = mapOf("Coffee Hut" to "Salary"), incomeCategories = mapOf("Salary" to true))

        val payment = parsedTransaction(merchant = "Coffee Hut")
        StatementImportProcessor(store, rules).process(listOf(payment))

        assertEquals(payment.toEntity().category, store.inserted.single().category)
    }

    @Test
    fun `a rule has the last word over a mapping and its applications are saved with the row id`() = runBlocking {
        val store = FakeTransactionStore()
        val rule = merchantRule(
            "Coffee",
            RuleAction(TransactionField.CATEGORY, ActionType.SET, "Cafes"),
            RuleAction(TransactionField.TAGS, ActionType.ADD_TAG, "Weekend")
        )
        val rules = importRules(mappings = mapOf("Coffee Hut" to "Food & Dining"), rules = arrayOf(rule))

        StatementImportProcessor(store, rules).process(
            listOf(
                parsedTransaction(merchant = "Book Nook", transactionHash = "a", reference = "111111111111"),
                parsedTransaction(merchant = "Coffee Hut", transactionHash = "b", reference = "222222222222")
            )
        )

        assertEquals("Cafes", store.inserted[1].category)
        val (id, applications) = store.recorded.single()
        assertEquals(101L, id)
        assertEquals(rule.id, applications.single().ruleId)
        assertTrue(applications.single().fieldsModified.any { it.field == TransactionField.TAGS && it.newValue == "Weekend" })
    }

    @Test
    fun `a block rule keeps a new row out and is counted`() = runBlocking {
        val store = FakeTransactionStore()
        val rules = importRules(rules = arrayOf(merchantRule("Spam Shop", RuleAction(TransactionField.CATEGORY, ActionType.BLOCK, ""))))

        val result = StatementImportProcessor(store, rules).process(
            listOf(
                parsedTransaction(merchant = "Spam Shop", transactionHash = "a", reference = "111111111111"),
                parsedTransaction(merchant = "Coffee Hut", transactionHash = "b", reference = "222222222222")
            )
        )

        assertEquals(1, result.imported)
        assertEquals(1, result.blockedByRules)
        assertEquals("Coffee Hut", store.inserted.single().merchantName)
    }

    @Test
    fun `rules don't change which rows match`() = runBlocking {
        val generic = transaction(id = 8, merchantName = "UPI Transaction", reference = null)
        val rules = importRules(rules = arrayOf(merchantRule("Swiggy", RuleAction(TransactionField.CATEGORY, ActionType.BLOCK, ""))))
        val store = FakeTransactionStore(generic)

        val result = StatementImportProcessor(store, rules).process(
            listOf(parsedTransaction(merchant = "Swiggy", reference = null))
        )

        // A blocked merchant still enriches the SMS it duplicates.
        assertEquals(1, result.enriched)
        assertEquals(0, result.blockedByRules)
        assertEquals("Swiggy", store.updated.single().merchantName)
    }

    private class FakeTransactionStore(
        vararg stored: TransactionEntity
    ) : StatementImportProcessor.TransactionStore {
        private val stored = stored.toList()
        val inserted = mutableListOf<TransactionEntity>()
        val updated = mutableListOf<TransactionEntity>()
        val recorded = mutableListOf<Pair<Long, List<RuleApplication>>>()
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

        override suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long> {
            inserted += transactions
            return transactions.indices.map { 100L + inserted.size - transactions.size + it }
        }

        override suspend fun recordRuleApplications(transactionId: Long, applications: List<RuleApplication>) {
            recorded += transactionId to applications
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
