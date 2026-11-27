package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
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
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A statement row's smsBody is the file's line, not an SMS. The import reads
 * it as no SMS text, so a later run of the rules must too.
 */
class ApplyRulesStatementRowTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val tags = mockk<TagRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val useCase = ApplyRulesToPastTransactionsUseCase(
        transactions,
        rules,
        RuleEngine(),
        tags,
        mockk(relaxed = true),
        database
    )

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
        coEvery { tags.getTagNamesFor(any()) } returns emptyMap()
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    /** Serves the ledger through the paged reads the use case walks. */
    private fun serve(rows: List<TransactionEntity>) {
        coEvery { transactions.countLiveTransactions() } returns rows.size
        coEvery { transactions.maxLiveTransactionId() } returns rows.maxOf { it.id }
        coEvery { transactions.getTransactionsPageAfter(any(), any(), any()) } answers {
            rows.filter { it.id > firstArg<Long>() && it.id <= secondArg<Long>() }.take(thirdArg<Int>())
        }
    }

    private val blockWithoutKeyword = TransactionRule(
        name = "Block without keyword",
        conditions = listOf(RuleCondition(TransactionField.SMS_TEXT, ConditionOperator.NOT_CONTAINS, "SampleBank")),
        actions = listOf(RuleAction(TransactionField.CATEGORY, ActionType.BLOCK, ""))
    )

    private fun row(id: Long, sender: String, body: String) = TransactionEntity(
        id = id,
        amount = BigDecimal("250.00"),
        merchantName = "Coffee Hut",
        category = "Others",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 1, 15, 12, 0),
        transactionHash = "hash-$id",
        smsBody = body,
        smsSender = sender
    )

    private val statementRow = row(1, "STATEMENT", "15/01/2026 Coffee Hut 250.00")
    private val pdfRow = row(2, "Sample PDF", "Coffee Hut 250.00 debit")
    private val smsRow = row(3, "AD-SAMPLE", "Spent 250.00 at Coffee Hut")

    @Test
    fun `an SMS text rule leaves statement rows alone when run on past transactions`() = runBlocking {
        coEvery { rules.getActiveRules() } returns listOf(blockWithoutKeyword)
        serve(listOf(statementRow, pdfRow, smsRow))

        val result = useCase.applyAllActiveRulesToTransactions()

        assertEquals(1, result.totalDeleted)
        coVerify(exactly = 1) { transactions.updateTransaction(any()) }
        coVerify { transactions.updateTransaction(smsRow.copy(isDeleted = true)) }
    }

    @Test
    fun `the preview of an SMS text rule counts no statement rows`() = runBlocking {
        serve(listOf(statementRow, pdfRow, smsRow))

        val preview = useCase.previewRuleApplication(blockWithoutKeyword)

        assertEquals(1, preview.totalWouldBlock)
        assertEquals(smsRow, preview.samples.single().original)
    }

    @Test
    fun `applying one SMS text rule leaves statement rows alone`() = runBlocking {
        serve(listOf(statementRow, pdfRow, smsRow))

        val result = useCase.applyRuleToAllTransactions(blockWithoutKeyword)

        assertEquals(1, result.totalDeleted)
        coVerify { transactions.updateTransaction(smsRow.copy(isDeleted = true)) }
    }
}
