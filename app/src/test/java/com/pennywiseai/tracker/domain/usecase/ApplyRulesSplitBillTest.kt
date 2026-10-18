package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #346: a rule that would make a split bill income is neither previewed, applied nor recorded. */
class ApplyRulesSplitBillTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val engine = mockk<RuleEngine>()
    private val tags = mockk<TagRepository>(relaxed = true)
    private val useCase = ApplyRulesToPastTransactionsUseCase(transactions, rules, engine, tags)
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
        coEvery { transactions.getAllTransactionsList() } returns listOf(bill)
        every { tags.observeTransactionTagNames() } returns flowOf(emptyMap())
        every { engine.shouldBlockTransaction(any(), any(), any()) } returns null
        every { engine.evaluateRules(any(), any(), any()) } returns (bill.copy(transactionType = TransactionType.INCOME) to listOf(typeOnly))
    }

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
