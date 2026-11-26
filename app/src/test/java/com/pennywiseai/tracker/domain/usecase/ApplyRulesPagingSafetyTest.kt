package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.FieldModification
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

private const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** Apply to Past walks the ledger in id pages, writes a row with its history atomically, and keeps pair legs and loan/split rows out of harm. */
class ApplyRulesPagingSafetyTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val engine = mockk<RuleEngine>()
    private val tags = mockk<TagRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val useCase = ApplyRulesToPastTransactionsUseCase(transactions, rules, engine, tags, mockk(relaxed = true), database)
    private val rule = TransactionRule(id = "r1", name = "Food", conditions = emptyList(), actions = emptyList())

    private fun txn(id: Long, category: String = "Others", type: TransactionType = TransactionType.EXPENSE) = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = "Shop $id", category = category,
        transactionType = type, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
        transactionHash = "h$id", currency = "INR"
    )

    private fun application(txn: TransactionEntity, field: TransactionField, from: String, to: String) = RuleApplication(
        ruleId = "r1", ruleName = "Food", transactionId = txn.id.toString(),
        fieldsModified = listOf(FieldModification(field, from, to, ActionType.SET))
    )

    private val store = sortedMapOf<Long, TransactionEntity>()

    private fun serve(rows: List<TransactionEntity>) {
        rows.forEach { store[it.id] = it }
        coEvery { transactions.countLiveTransactions() } answers { store.size }
        coEvery { transactions.getTransactionsPageAfter(any(), any()) } answers {
            store.values.filter { it.id > firstArg<Long>() }.take(secondArg<Int>())
        }
        every { engine.shouldBlockTransaction(any(), any(), any()) } returns null
    }

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    @Test
    fun everyRowIsVisitedOnceAcrossPagesEvenWhileRowsChange() = runBlocking {
        serve((1L..1203L).map { txn(it) })
        every { engine.evaluateRules(any(), any(), any()) } answers {
            val t = firstArg<TransactionEntity>()
            t.copy(category = "Food") to listOf(application(t, TransactionField.CATEGORY, "Others", "Food"))
        }
        val written = mutableListOf<Long>()
        coEvery { transactions.updateTransaction(any()) } answers {
            val t = firstArg<TransactionEntity>()
            written.add(t.id)
            store[t.id] = t // the row changes under the walk
        }
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = useCase.applyRuleToAllTransactions(rule) { p, t -> progress.add(p to t) }

        assertEquals(1203, result.totalProcessed)
        assertEquals(1203, result.totalUpdated)
        assertEquals((1L..1203L).toList(), written)
        assertEquals(1203 to 1203, progress.last())
    }

    @Test
    fun ruleDeletedMidRunLeavesNoRowChangedWithoutHistory() = runBlocking {
        serve((1L..10L).map { txn(it) })
        every { engine.evaluateRules(any(), any(), any()) } answers {
            val t = firstArg<TransactionEntity>()
            t.copy(category = "Food") to listOf(application(t, TransactionField.CATEGORY, "Others", "Food"))
        }
        val written = mutableListOf<Long>()
        val withHistory = mutableListOf<Long>()
        coEvery { transactions.updateTransaction(any()) } answers { written.add(firstArg<TransactionEntity>().id) }
        // Rolls the block's writes back when it throws, as the database does.
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            val before = written.toList()
            try {
                secondArg<suspend () -> Any?>().invoke()
            } catch (e: Exception) {
                written.clear()
                written.addAll(before)
                throw e
            }
        }
        coEvery { rules.saveRuleApplications(any()) } answers {
            val id = firstArg<List<RuleApplication>>().first().transactionId.toLong()
            if (id > 3) {
                coEvery { rules.getRuleById("r1") } returns null
                error("FOREIGN KEY constraint failed")
            }
            withHistory.add(id)
        }
        coEvery { rules.getRuleById("r1") } returns rule

        val result = useCase.applyRuleToAllTransactions(rule)

        assertEquals(listOf(1L, 2L, 3L), written)
        assertEquals(written, withHistory)
        assertEquals(3, result.totalUpdated)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("rule was deleted"))
        coVerify(exactly = 4) { engine.evaluateRules(any(), any(), any()) }
    }

    private fun pairedLeg() = txn(1, "Transfer", TransactionType.TRANSFER).copy(transferPairId = "pair-1")

    @Test
    fun transferLegKeepsItsTypeInApplyAndPreview() = runBlocking {
        val leg = pairedLeg()
        serve(listOf(leg))
        every { engine.evaluateRules(any(), any(), any()) } returns
            (leg.copy(transactionType = TransactionType.INCOME) to listOf(application(leg, TransactionField.TYPE, "TRANSFER", "INCOME")))

        val preview = useCase.previewRuleApplication(rule)
        val result = useCase.applyRuleToAllTransactions(rule)

        assertEquals(0, preview.totalMatched)
        assertEquals(0, result.totalUpdated)
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        coVerify(exactly = 0) { rules.saveRuleApplications(any()) }
    }

    @Test
    fun uncategorizedModeSkipsSplitParentsAndLoanLinkedRows() = runBlocking {
        val plain = txn(1)
        val splitParent = txn(2).copy(lentSharesTotal = BigDecimal("10"))
        val loanLinked = txn(3).copy(loanId = 7L)
        val counted = txn(4, category = "Food")
        serve(listOf(plain, splitParent, loanLinked, counted))
        every { engine.evaluateRules(any(), any(), any()) } answers { firstArg<TransactionEntity>() to emptyList() }

        val result = useCase.applyRuleToUncategorizedTransactions(rule)

        assertEquals(1, result.totalProcessed)
        coVerify(exactly = 1) { engine.evaluateRules(any(), any(), any()) }
        coVerify { engine.evaluateRules(plain, any(), any()) }
    }

    @Test
    fun uncategorizedModeFollowsARenamedOthers() = runBlocking {
        BuiltinCategoryNames.update(
            listOf(CategoryEntity(name = "Misc", color = "#000000", isSystem = true, systemName = "Others"))
        )
        try {
            val renamed = txn(1, category = "Misc")
            serve(listOf(renamed, txn(2, category = "Food")))
            every { engine.evaluateRules(any(), any(), any()) } answers { firstArg<TransactionEntity>() to emptyList() }

            val result = useCase.applyRuleToUncategorizedTransactions(rule)

            assertEquals(1, result.totalProcessed)
            coVerify { engine.evaluateRules(renamed, any(), any()) }
        } finally {
            BuiltinCategoryNames.update(emptyList())
        }
    }
}
