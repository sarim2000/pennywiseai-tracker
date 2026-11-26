package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
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
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

private const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** #346: a rule that would make a split bill income is neither previewed, applied nor recorded. */
class ApplyRulesSplitBillTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val engine = mockk<RuleEngine>()
    private val tags = mockk<TagRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val useCase = ApplyRulesToPastTransactionsUseCase(transactions, rules, engine, tags, mockk(relaxed = true), database)
    private val rule = mockk<TransactionRule>(relaxed = true)

    private val bill = TransactionEntity(
        id = 1, amount = BigDecimal("1000"), merchantName = "Dinner", category = "Food",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 1, 20, 0),
        transactionHash = "h", currency = "INR", lentSharesTotal = BigDecimal("600")
    )
    private val typeOnly = RuleApplication(
        ruleId = "r1", ruleName = "Make income", transactionId = "1",
        fieldsModified = listOf(FieldModification(TransactionField.TYPE, "EXPENSE", "INCOME", ActionType.SET))
    )

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
        coEvery { transactions.countLiveTransactions() } returns 1
        coEvery { transactions.getTransactionsPageAfter(any(), any()) } answers {
            if (firstArg<Long>() < 1) listOf(bill) else emptyList()
        }
        every { engine.shouldBlockTransaction(any(), any(), any()) } returns null
        every { engine.evaluateRules(any(), any(), any()) } returns (bill.copy(transactionType = TransactionType.INCOME) to listOf(typeOnly))
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    @Test
    fun previewShowsNoChange() = runBlocking {
        val preview = useCase.previewRuleApplication(rule)
        assertEquals(0, preview.totalMatched)
        assertEquals(0, preview.samples.size)
    }

    @Test
    fun applyWritesAndRecordsNothing() = runBlocking {
        val result = useCase.applyAllActiveRulesToTransactions()
        assertEquals(0, result.totalUpdated)
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        coVerify(exactly = 0) { rules.saveRuleApplications(any()) }
    }
}
